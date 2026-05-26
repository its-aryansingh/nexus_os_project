package com.nexus.os.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only ledger row. The DB trigger {@code cost_ledger_no_modify}
 * (added in V004) blocks UPDATE/DELETE — write once, sum forever.
 */
@Entity
@Table(name = "cost_ledger")
public class CostLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_run_id")
    private UUID workflowRunId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn = 0;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut = 0;

    @Column(nullable = false)
    private int units = 0;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal usd = BigDecimal.ZERO;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> metadata = Map.of();

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getWorkflowRunId() { return workflowRunId; }
    public void setWorkflowRunId(UUID v) { this.workflowRunId = v; }
    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID v) { this.agentId = v; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public int getTokensIn() { return tokensIn; }
    public void setTokensIn(int v) { this.tokensIn = v; }
    public int getTokensOut() { return tokensOut; }
    public void setTokensOut(int v) { this.tokensOut = v; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
    public BigDecimal getUsd() { return usd; }
    public void setUsd(BigDecimal usd) { this.usd = usd; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
