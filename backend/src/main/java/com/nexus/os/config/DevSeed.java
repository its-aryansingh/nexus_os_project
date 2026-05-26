package com.nexus.os.config;

import com.nexus.os.domain.AgentEntity;
import com.nexus.os.domain.AgentRepository;
import com.nexus.os.domain.Tenant;
import com.nexus.os.domain.TenantRepository;
import com.nexus.os.domain.Workflow;
import com.nexus.os.domain.WorkflowRepository;
import com.nexus.os.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idempotent local-profile data seed. Inserts a second tenant + sample
 * agents + sample workflows so the dashboard, /agents, /workflows, and
 * /runs pages have content immediately after first boot.
 *
 * <p>Skipped in test / prod profiles. Safe to re-run — every insert
 * checks existence first.
 */
@Configuration
@Profile("local")
public class DevSeed {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    private static final UUID DEV_TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACME_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Bean
    public ApplicationRunner seedRunner(
            TenantRepository tenants,
            AgentRepository agents,
            WorkflowRepository workflows,
            TransactionTemplate tx
    ) {
        return args -> {
            log.info("DevSeed: ensuring local-profile sample data is present");
            ensureTenant(tx, tenants, DEV_TENANT_ID, "default", "Default (dev)", Tenant.Plan.starter, "50.00");
            ensureTenant(tx, tenants, ACME_TENANT_ID, "acme", "Acme Corp", Tenant.Plan.pro, "500.00");

            seedTenantContent(tx, agents, workflows, DEV_TENANT_ID);
            seedTenantContent(tx, agents, workflows, ACME_TENANT_ID);
            log.info("DevSeed: complete");
        };
    }

    private void ensureTenant(
            TransactionTemplate tx,
            TenantRepository tenants,
            UUID id,
            String slug,
            String name,
            Tenant.Plan plan,
            String budgetUsd
    ) {
        tx.executeWithoutResult(status -> {
            if (tenants.existsById(id)) return;
            final var t = new Tenant();
            t.setId(id);
            t.setSlug(slug);
            t.setName(name);
            t.setPlan(plan);
            t.setMonthlyUsdBudget(new BigDecimal(budgetUsd));
            tenants.save(t);
            log.info("DevSeed: tenant inserted slug={} budget={}", slug, budgetUsd);
        });
    }

    private void seedTenantContent(
            TransactionTemplate tx,
            AgentRepository agents,
            WorkflowRepository workflows,
            UUID tenantId
    ) {
        // RLS is gated by TenantContext via RlsAspect — must bind before any
        // tenant-scoped insert otherwise the rows are invisible / rejected.
        try {
            TenantContext.set(tenantId);
            tx.executeWithoutResult(status -> {
                seedAgent(agents, tenantId, "support",  "Support Agent",  "Resolves customer questions with empathy.",                   "gpt-4o-mini", List.of("search_memory", "send_reply"));
                seedAgent(agents, tenantId, "coder",    "Coder Agent",    "Generates and reviews production-grade Java/TypeScript code.", "gpt-4o",      List.of("run_tests", "open_pr", "search_repo"));
                seedAgent(agents, tenantId, "analyst",  "Analyst Agent",  "RAG over your internal docs; cites every claim.",              "gpt-4o-mini", List.of("search_memory", "render_chart"));
                seedWorkflow(workflows, tenantId, "default-orchestration", "Default Orchestration", "Classify intent → route → execute. The v0.1 baseline saga.", 1);
                seedWorkflow(workflows, tenantId, "support-triage",         "Support Triage",         "Inbound message → urgency check → human-or-agent route.",      1);
            });
        } finally {
            TenantContext.clear();
        }
    }

    private void seedAgent(
            AgentRepository agents,
            UUID tenantId,
            String slug,
            String name,
            String description,
            String model,
            List<String> tools
    ) {
        if (agents.findByTenantIdAndSlug(tenantId, slug).isPresent()) return;
        final var a = new AgentEntity();
        a.setTenantId(tenantId);
        a.setSlug(slug);
        a.setName(name);
        a.setDescription(description);
        a.setModelPreference(model);
        a.setTools(tools);
        a.setCapabilities(List.of(Map.of("type", "TextGeneration", "modelId", model, "temperature", 0.7)));
        a.setActive(true);
        agents.save(a);
        log.info("DevSeed: agent inserted tenant={} slug={}", tenantId, slug);
    }

    private void seedWorkflow(
            WorkflowRepository workflows,
            UUID tenantId,
            String slug,
            String name,
            String description,
            int version
    ) {
        if (workflows.findByTenantIdAndSlugAndVersion(tenantId, slug, version).isPresent()) return;
        final var w = new Workflow();
        w.setTenantId(tenantId);
        w.setSlug(slug);
        w.setVersion(version);
        w.setName(name);
        w.setDescription(description);
        w.setTemporalWorkflowType("AgentOrchestrationWorkflow");
        w.setDefinition(Map.of(
                "nodes", List.of(
                        Map.of("id", "classify", "type", "activity", "name", "classifyIntent"),
                        Map.of("id", "route",    "type", "activity", "name", "routeToAgent"),
                        Map.of("id", "execute",  "type", "activity", "name", "executeAgentTask")
                ),
                "edges", List.of(
                        Map.of("from", "classify", "to", "route"),
                        Map.of("from", "route",    "to", "execute")
                )
        ));
        w.setActive(true);
        workflows.save(w);
        log.info("DevSeed: workflow inserted tenant={} slug={}", tenantId, slug);
    }
}
