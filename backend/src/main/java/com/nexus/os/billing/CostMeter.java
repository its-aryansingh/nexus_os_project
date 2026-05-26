package com.nexus.os.billing;

import com.nexus.os.domain.CostLedgerEntry;
import com.nexus.os.domain.CostLedgerRepository;
import com.nexus.os.domain.Tenant;
import com.nexus.os.domain.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Records every meterable external call into {@link CostLedgerEntry} and
 * enforces per-tenant monthly USD budgets. See docs/SYSTEM_DESIGN.md §4.12.
 *
 * <p>The actual provider-side circuit breaker named {@code tenant-budget}
 * is configured in {@code application.yml} and tripped by exceptions from
 * {@link #assertWithinBudget}.
 */
@Component
public class CostMeter {

    private static final Logger log = LoggerFactory.getLogger(CostMeter.class);

    private final CostLedgerRepository ledger;
    private final TenantRepository tenants;
    private final MeterRegistry meters;

    public CostMeter(CostLedgerRepository ledger, TenantRepository tenants, MeterRegistry meters) {
        this.ledger = ledger;
        this.tenants = tenants;
        this.meters = meters;
    }

    /**
     * Append a ledger row + emit Micrometer counters. Idempotent caller is
     * responsible for not double-recording (each row gets a fresh UUID).
     */
    @Transactional
    public void record(
            UUID tenantId,
            UUID workflowRunId,
            UUID agentId,
            String provider,
            String model,
            String operation,
            int tokensIn,
            int tokensOut,
            int units,
            BigDecimal usd
    ) {
        final var row = new CostLedgerEntry();
        row.setTenantId(tenantId);
        row.setWorkflowRunId(workflowRunId);
        row.setAgentId(agentId);
        row.setProvider(provider);
        row.setModel(model);
        row.setOperation(operation);
        row.setTokensIn(tokensIn);
        row.setTokensOut(tokensOut);
        row.setUnits(units);
        row.setUsd(usd);
        ledger.save(row);

        // Metrics: nexus_tokens_consumed_total, nexus_cost_usd_total
        Counter.builder("nexus.tokens.consumed")
                .description("Tokens consumed by tenant/model/direction")
                .tag("tenant", tenantId.toString())
                .tag("model", model)
                .tag("direction", "in")
                .register(meters)
                .increment(tokensIn);

        Counter.builder("nexus.tokens.consumed")
                .tag("tenant", tenantId.toString())
                .tag("model", model)
                .tag("direction", "out")
                .register(meters)
                .increment(tokensOut);

        meters.counter("nexus.cost.usd", "tenant", tenantId.toString(), "model", model)
                .increment(usd.doubleValue());
    }

    /**
     * Throws if the tenant's month-to-date spend plus the estimate would
     * exceed the monthly budget. Wrap your LLM call in a try/catch +
     * Resilience4j circuit breaker named {@code tenant-budget} to open
     * the circuit on breach.
     */
    @Transactional(readOnly = true)
    public void assertWithinBudget(UUID tenantId, BigDecimal estimatedUsd) {
        final var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));
        final var budget = tenant.getMonthlyUsdBudget();
        if (budget == null || budget.signum() <= 0) {
            return; // no budget set
        }
        final var monthStart = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        final var spent = ledger.sumUsdSince(tenantId, monthStart);
        final var projected = spent.add(estimatedUsd);
        if (projected.compareTo(budget) > 0) {
            log.warn("Budget exceeded for tenant {}: spent={} estimate={} budget={}",
                    tenantId, spent, estimatedUsd, budget);
            throw new BudgetExceededException(tenantId, spent, estimatedUsd, budget);
        }
    }

    public static class BudgetExceededException extends RuntimeException {
        private final UUID tenantId;
        private final BigDecimal spent;
        private final BigDecimal estimated;
        private final BigDecimal budget;

        public BudgetExceededException(UUID tenantId, BigDecimal spent, BigDecimal estimated, BigDecimal budget) {
            super("Tenant %s budget exceeded: spent=%s + estimate=%s > budget=%s"
                    .formatted(tenantId, spent, estimated, budget));
            this.tenantId = tenantId;
            this.spent = spent;
            this.estimated = estimated;
            this.budget = budget;
        }

        public UUID getTenantId() { return tenantId; }
        public BigDecimal getSpent() { return spent; }
        public BigDecimal getEstimated() { return estimated; }
        public BigDecimal getBudget() { return budget; }
    }
}
