package com.nexus.os.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface CostLedgerRepository extends JpaRepository<CostLedgerEntry, UUID> {

    /**
     * Sum of USD spent for a tenant since a given timestamp.
     * Used by {@link com.nexus.os.billing.CostMeter} to enforce per-tenant budgets.
     */
    @Query("""
        SELECT COALESCE(SUM(c.usd), 0)
          FROM CostLedgerEntry c
         WHERE c.tenantId = :tenantId
           AND c.occurredAt >= :since
        """)
    BigDecimal sumUsdSince(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);
}
