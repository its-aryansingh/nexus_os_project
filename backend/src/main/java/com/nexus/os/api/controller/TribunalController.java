package com.nexus.os.api.controller;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import com.nexus.os.agents.TokenPricing;
import com.nexus.os.agents.Tribunal;
import com.nexus.os.billing.CostMeter;
import com.nexus.os.observability.ToolCallRecorder;
import com.nexus.os.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * End-to-end demo of N-version consensus voting. POST a question + option
 * list to {@code /api/tribunal/vote} and the controller fans out {@code N}
 * jurors with mild temperature variance, tallies the votes, records cost +
 * tool-call audit rows, and returns the full breakdown so the UI can
 * surface the disagreement signal.
 *
 * <p>Wires together: {@link Tribunal} (counter + verdict math),
 * {@link NexusAgent} (LLM seam), {@link ModelRouter} (model selection),
 * {@link CostMeter} (append to ledger), {@link ToolCallRecorder} (audit row).
 * See docs/SYSTEM_DESIGN.md §4.6 for the design rationale.
 */
@RestController
@RequestMapping("/api/tribunal")
public class TribunalController {

    private static final Logger log = LoggerFactory.getLogger(TribunalController.class);
    private static final double[] JUROR_TEMPS = { 0.2, 0.5, 0.8, 0.35, 0.65, 0.15, 0.85 };
    private static final int MIN_JURORS = 1;
    private static final int MAX_JURORS = 7;

    private final Tribunal tribunal;
    private final NexusAgent agent;
    private final ModelRouter modelRouter;
    private final TokenPricing pricing;
    private final CostMeter costMeter;
    private final ToolCallRecorder toolCalls;

    public TribunalController(
            Tribunal tribunal,
            NexusAgent agent,
            ModelRouter modelRouter,
            TokenPricing pricing,
            CostMeter costMeter,
            ToolCallRecorder toolCalls
    ) {
        this.tribunal = tribunal;
        this.agent = agent;
        this.modelRouter = modelRouter;
        this.pricing = pricing;
        this.costMeter = costMeter;
        this.toolCalls = toolCalls;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "tribunal", tribunal.summary(),
                "minJurors", MIN_JURORS,
                "maxJurors", MAX_JURORS,
                "defaultModel", modelRouter.getExpensiveModel()
        );
    }

    public record VoteRequest(
            String prompt,
            List<String> options,
            Integer jurors,
            String model
    ) {}

    public record JurorVote(int index, double temperature, String choice, String raw) {}

    public record VoteResponse(
            String majority,
            int majorityCount,
            int totalJurors,
            double disagreement,
            boolean unanimous,
            boolean escalate,
            List<String> distinctAnswers,
            List<JurorVote> jurors,
            String model,
            BigDecimal usdEstimate,
            boolean demo
    ) {}

    @PostMapping("/vote")
    public ResponseEntity<?> vote(@RequestBody VoteRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prompt is required"));
        }

        final var tenantId = TenantContext.require();
        final var jurorCount = clamp(
                req.jurors() == null ? 3 : req.jurors(),
                MIN_JURORS, MAX_JURORS);
        final var model = req.model() == null || req.model().isBlank()
                ? modelRouter.getExpensiveModel()
                : req.model();
        final var options = normalizeOptions(req.options());

        final var framed = frame(req.prompt(), options);
        final var jurorLog = new ArrayList<JurorVote>(jurorCount);
        final var counter = new int[]{0};
        final var totalTokensIn = new int[]{0};
        final var totalTokensOut = new int[]{0};

        final var start = System.nanoTime();
        try {
            final var verdict = tribunal.vote(() -> {
                final var idx = counter[0]++;
                final var temp = JUROR_TEMPS[idx % JUROR_TEMPS.length];
                final var raw = agent.execute(new AgentCapability.TextGeneration(model, temp), framed);
                final var choice = extractChoice(raw, options);
                jurorLog.add(new JurorVote(idx + 1, temp, choice, raw));
                totalTokensIn[0]  += estimate(framed);
                totalTokensOut[0] += estimate(raw);
                return choice;
            }, jurorCount);

            final var usd = pricing.usdFor(model, totalTokensIn[0], totalTokensOut[0]);
            costMeter.record(tenantId, null, null,
                    providerFor(model), model, "tribunal.vote",
                    totalTokensIn[0], totalTokensOut[0], 0, usd);

            final var elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            final var escalate = verdict.uncertain(0.34);
            final var demo = isDemoModel(model);

            toolCalls.record(tenantId, null, null, "tribunal",
                    Map.of(
                            "prompt_len", req.prompt().length(),
                            "options", options,
                            "jurors", jurorCount,
                            "model", model
                    ),
                    Map.of(
                            "majority", verdict.majority(),
                            "majority_count", verdict.majorityCount(),
                            "disagreement", verdict.disagreement(),
                            "unanimous", verdict.unanimous(),
                            "escalate", escalate,
                            "usd", usd
                    ),
                    null, elapsedMs);

            return ResponseEntity.ok(new VoteResponse(
                    verdict.majority(),
                    verdict.majorityCount(),
                    verdict.totalJurors(),
                    round3(verdict.disagreement()),
                    verdict.unanimous(),
                    escalate,
                    verdict.distinctAnswers(),
                    jurorLog,
                    model,
                    usd,
                    demo
            ));
        } catch (CostMeter.BudgetExceededException blocked) {
            log.warn("Tribunal blocked by budget gate — tenant={}", tenantId);
            return ResponseEntity.status(402).body(Map.of(
                    "error", "budget_exceeded",
                    "spent", blocked.getSpent(),
                    "budget", blocked.getBudget()
            ));
        } catch (RuntimeException fail) {
            log.error("Tribunal vote failed — tenant={} : {}", tenantId, fail.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", fail.getClass().getSimpleName(),
                    "message", fail.getMessage()
            ));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<String> normalizeOptions(List<String> raw) {
        if (raw == null) return List.of();
        final var seen = new LinkedHashMap<String, String>();
        for (final var o : raw) {
            if (o == null) continue;
            final var trimmed = o.trim();
            if (trimmed.isEmpty()) continue;
            final var key = trimmed.toUpperCase(Locale.ROOT);
            seen.putIfAbsent(key, key);
        }
        return List.copyOf(seen.values());
    }

    private static String frame(String userPrompt, List<String> options) {
        if (options.isEmpty()) {
            return ("You are one juror on a Nexus OS Tribunal. Give your verdict succinctly.%n%nSubject:%n%s")
                    .formatted(userPrompt);
        }
        return ("""
                You are one juror on a Nexus OS Tribunal. Base your verdict on accuracy,
                safety, and compliance with the subject below. Reply with exactly one of:
                %s.

                Subject:
                %s
                """).formatted(String.join(", ", options), userPrompt);
    }

    private static String extractChoice(String raw, List<String> options) {
        if (raw == null) return "ESCALATE";
        final var upper = raw.toUpperCase(Locale.ROOT);
        if (options.isEmpty()) {
            // No option list — return a normalized short signature so identical
            // free-form answers still collapse to the same vote.
            return upper.trim();
        }
        for (final var opt : options) {
            if (upper.contains(opt)) return opt;
        }
        return options.get(0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int estimate(String s) {
        return s == null ? 0 : Math.max(1, s.length() / 4);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String providerFor(String model) {
        if (model == null) return "mock";
        final var m = model.toLowerCase(Locale.ROOT);
        if (m.startsWith("gpt-") || m.startsWith("o1") || m.startsWith("o3")) return "openai";
        if (m.startsWith("claude-")) return "anthropic";
        if (m.equals("ollama") || m.equals("mock")) return m;
        return "openai-compatible";
    }

    private static boolean isDemoModel(String model) {
        // Heuristic — if no API key is set, NexusAgent uses MockChatLanguageModel
        // and the controller can't observe that directly. The cost-ledger row
        // distinguishes by provider but the UI wants a quick flag. We treat
        // an empty/blank or literal "mock" model name as demo; everything else
        // is "real" from the controller's perspective.
        return model == null || model.isBlank()
                || "mock".equalsIgnoreCase(model)
                || System.getenv().getOrDefault("LANGCHAIN4J_OPEN_AI_API_KEY", "").isBlank();
    }
}
