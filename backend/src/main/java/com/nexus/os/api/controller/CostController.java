package com.nexus.os.api.controller;

import com.nexus.os.billing.CostMeter;
import com.nexus.os.domain.CostLedgerRepository;
import com.nexus.os.tenancy.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/cost")
public class CostController {

    private final CostLedgerRepository ledger;
    private final CostMeter meter;

    public CostController(CostLedgerRepository ledger, CostMeter meter) {
        this.ledger = ledger;
        this.meter = meter;
    }

    @GetMapping("/month-to-date")
    public Map<String, Object> monthToDate() {
        final var tenantId = TenantContext.require();
        final var monthStart = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        final BigDecimal spent = ledger.sumUsdSince(tenantId, monthStart);
        return Map.of(
                "tenantId", tenantId,
                "monthStart", monthStart.toString(),
                "spentUsd", spent
        );
    }

    @PostMapping("/check-budget")
    public Map<String, Object> checkBudget(@RequestParam BigDecimal estimatedUsd) {
        try {
            meter.assertWithinBudget(TenantContext.require(), estimatedUsd);
            return Map.of("withinBudget", true, "estimatedUsd", estimatedUsd);
        } catch (CostMeter.BudgetExceededException blocked) {
            return Map.of(
                    "withinBudget", false,
                    "estimatedUsd", estimatedUsd,
                    "spent", blocked.getSpent(),
                    "budget", blocked.getBudget()
            );
        }
    }
}
