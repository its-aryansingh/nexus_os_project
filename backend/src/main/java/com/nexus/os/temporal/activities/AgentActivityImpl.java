package com.nexus.os.temporal.activities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.HallucinationGuard;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import com.nexus.os.agents.PromptCache;
import com.nexus.os.agents.TokenPricing;
import com.nexus.os.billing.CostMeter;
import com.nexus.os.domain.OutboxEvent;
import com.nexus.os.domain.OutboxRepository;
import com.nexus.os.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wires the runtime agent invocation through the full v0.1 cross-cutting
 * stack documented in SYSTEM_DESIGN.md: budget check → prompt cache →
 * model router → LangChain4j call → hallucination guard → cost ledger.
 *
 * <p>Each activity execution binds the tenant context for its lifetime so
 * the {@code RlsAspect} attaches the Postgres RLS session variable and
 * the cost-ledger insert is properly tenant-scoped.
 */
@Component
public class AgentActivityImpl implements AgentActivity {

    private static final Logger log = LoggerFactory.getLogger(AgentActivityImpl.class);
    private static final BigDecimal PREFLIGHT_BUDGET_ESTIMATE = new BigDecimal("0.005");

    private final NexusAgent nexusAgent;
    private final ModelRouter modelRouter;
    private final PromptCache promptCache;
    private final HallucinationGuard guard;
    private final CostMeter costMeter;
    private final TokenPricing pricing;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outbox;
    private final Timer activityTimer;

    public AgentActivityImpl(
            NexusAgent nexusAgent,
            ModelRouter modelRouter,
            PromptCache promptCache,
            HallucinationGuard guard,
            CostMeter costMeter,
            TokenPricing pricing,
            ObjectMapper objectMapper,
            OutboxRepository outbox,
            MeterRegistry meters
    ) {
        this.nexusAgent = nexusAgent;
        this.modelRouter = modelRouter;
        this.promptCache = promptCache;
        this.guard = guard;
        this.costMeter = costMeter;
        this.pricing = pricing;
        this.objectMapper = objectMapper;
        this.outbox = outbox;
        this.activityTimer = Timer.builder("nexus.activity.execute")
                .description("Time to execute one agent activity (cache + LLM + guard)")
                .publishPercentileHistogram()
                .register(meters);
    }

    @Override
    public String classifyIntent(String requestPayload) {
        final var payload = parsePayload(requestPayload);
        final var tenantId = bindTenant(payload);
        try {
            final var prompt = "Classify the following user message into exactly one of: "
                    + "customer_support, code_generation, data_analysis, general_chat. "
                    + "Respond with ONLY the category name.\n\n"
                    + asString(payload.get("prompt"));
            return invoke(tenantId, "intent_classification",
                    new AgentCapability.TextGeneration(modelRouter.getCheapModel(), 0.1),
                    prompt).trim().toLowerCase();
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public String routeToAgent(String intent) {
        return switch (intent == null ? "" : intent.trim().toLowerCase()) {
            case "customer_support" -> "agent-support-v1";
            case "code_generation"  -> "agent-coder-v1";
            case "data_analysis"    -> "agent-analyst-v1";
            default                 -> "agent-general-v1";
        };
    }

    @Override
    public String executeAgentTask(String agentId, String requestPayload) {
        final var payload = parsePayload(requestPayload);
        final var tenantId = bindTenant(payload);
        final var workflowRunId = parseUuid(payload.get("workflow_run_id"));
        final var taskStart = System.nanoTime();
        try {
            final var prompt = asString(payload.get("prompt"));
            final var capability = capabilityFor(agentId);
            final var response = invoke(tenantId, agentId, capability, prompt);
            emitCompletion(tenantId, workflowRunId, response, null,
                    Duration.ofNanos(System.nanoTime() - taskStart));
            return response;
        } catch (RuntimeException fail) {
            emitCompletion(tenantId, workflowRunId, null, fail.getMessage(),
                    Duration.ofNanos(System.nanoTime() - taskStart));
            throw fail;
        } finally {
            TenantContext.clear();
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    private String invoke(UUID tenantId, String operation, AgentCapability capability, String prompt) {
        final var start = System.nanoTime();
        try {
            // 1. Budget pre-flight (Resilience4j 'tenant-budget' breaker fires
            //    if assertWithinBudget throws — see application.yml).
            costMeter.assertWithinBudget(tenantId, PREFLIGHT_BUDGET_ESTIMATE);

            // 2. Cache hit? Multi-tier (Caffeine -> Redis), see SYSTEM_DESIGN §4.4.
            final var model = modelFor(capability);
            final var temp  = temperatureFor(capability);
            final Optional<String> hit = promptCache.get(prompt, model, temp, tenantId.toString());
            if (hit.isPresent()) {
                log.debug("Prompt cache HIT — tenant={} model={}", tenantId, model);
                return hit.get();
            }

            // 3. Model invocation (router-selected on entry).
            final var response = nexusAgent.execute(capability, prompt);

            // 4. Hallucination guard — log/meter-only in v0.1; v0.2 swaps to
            //    explicit reject + retry via the router escalation path.
            final var clean = guard.isAcceptable(response, false, false);
            if (!clean) {
                log.warn("HallucinationGuard rejected output — tenant={} model={} len={}",
                        tenantId, model, response.length());
            }

            // 5. Cache write — async-safe; even if the worker dies mid-record,
            //    the next identical prompt will still hit the LLM (no harm).
            promptCache.put(prompt, model, temp, tenantId.toString(), response);

            // 6. Cost ledger row (append-only — V004 trigger blocks UPDATE/DELETE).
            final var tokensIn = estimateTokens(prompt);
            final var tokensOut = estimateTokens(response);
            final var usd = pricing.usdFor(model, tokensIn, tokensOut);
            costMeter.record(tenantId, null, null,
                    providerFor(model), model, operation,
                    tokensIn, tokensOut, 0, usd);

            return response;
        } finally {
            activityTimer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    private AgentCapability capabilityFor(String agentId) {
        return switch (agentId) {
            case "agent-coder-v1"   -> new AgentCapability.CodeExecution("java", 30_000L);
            case "agent-analyst-v1" -> new AgentCapability.DataRetrieval("nexus-memory", 5);
            default                 -> new AgentCapability.TextGeneration(modelRouter.getCheapModel(), 0.7);
        };
    }

    private String modelFor(AgentCapability c) {
        return switch (c) {
            case AgentCapability.TextGeneration tg -> tg.modelId();
            case AgentCapability.CodeExecution ce -> modelRouter.getExpensiveModel();   // code needs the big model
            case AgentCapability.DataRetrieval dr -> modelRouter.getCheapModel();
            case AgentCapability.ImageAnalysis ia -> ia.modelId();
        };
    }

    private double temperatureFor(AgentCapability c) {
        return c instanceof AgentCapability.TextGeneration tg ? tg.temperature() : 0.7;
    }

    private static String providerFor(String model) {
        if (model == null) return "mock";
        final var m = model.toLowerCase();
        if (m.startsWith("gpt-") || m.startsWith("o1") || m.startsWith("o3")) return "openai";
        if (m.startsWith("claude-")) return "anthropic";
        if (m.equals("ollama") || m.equals("mock")) return m;
        return "openai-compatible";
    }

    private static int estimateTokens(String s) {
        return s == null ? 0 : Math.max(1, s.length() / 4);
    }

    private UUID bindTenant(Map<String, Object> payload) {
        final var raw = asString(payload.get("tenant_id"));
        final var id = raw == null || raw.isBlank()
                ? UUID.fromString("00000000-0000-0000-0000-000000000001")
                : UUID.fromString(raw);
        TenantContext.set(id);
        return id;
    }

    private UUID parseUuid(Object o) {
        if (o == null) return null;
        try {
            return UUID.fromString(o.toString());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Emit a workflow.run.completed (or .failed) event into the outbox.
     * {@link com.nexus.os.kafka.WorkflowEventProjector} consumes it via
     * Kafka and updates the corresponding {@code WorkflowRun} row, closing
     * the CQRS read-model loop documented in ARCHITECTURE.md §4.1.
     */
    private void emitCompletion(UUID tenantId, UUID workflowRunId, String output, String error, Duration elapsed) {
        if (workflowRunId == null) {
            // Activity was invoked directly without a workflow row (e.g. tests);
            // no projector to notify.
            return;
        }
        final var payload = new LinkedHashMap<String, Object>();
        payload.put("event_type", error == null ? "workflow.run.completed" : "workflow.run.failed");
        payload.put("workflow_run_id", workflowRunId.toString());
        payload.put("tenant_id", tenantId.toString());
        payload.put("duration_ms", elapsed.toMillis());
        payload.put("finished_at", OffsetDateTime.now().toString());
        if (output != null) payload.put("output", output);
        if (error != null) payload.put("error", error);

        final var ev = new OutboxEvent();
        ev.setTenantId(tenantId);
        ev.setAggregateType("workflow_run");
        ev.setAggregateId(workflowRunId.toString());
        ev.setTopic("nexus.agent.events");
        ev.setPartitionKey(workflowRunId.toString());
        ev.setPayload(payload);
        outbox.save(ev);
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            final var parsed = (Map<String, Object>) objectMapper.readValue(json, Map.class);
            return parsed;
        } catch (Exception bad) {
            // Treat raw strings as a plain prompt for ergonomic callers.
            log.debug("Non-JSON activity payload — treating as plain prompt");
            return Map.of("prompt", json);
        }
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
}
