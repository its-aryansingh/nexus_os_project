package com.nexus.os.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "temporal_workflow_id", nullable = false, length = 255)
    private String temporalWorkflowId;

    @Column(name = "temporal_run_id", nullable = false, length = 255)
    private String temporalRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.running;

    @Column(name = "input_payload", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> inputPayload;

    @Column(name = "output_payload", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> outputPayload;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    public enum Status { running, completed, failed, compensated, timed_out }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = OffsetDateTime.now();
        }
    }

    // accessors
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getWorkflowId() { return workflowId; }
    public void setWorkflowId(UUID workflowId) { this.workflowId = workflowId; }
    public String getTemporalWorkflowId() { return temporalWorkflowId; }
    public void setTemporalWorkflowId(String v) { this.temporalWorkflowId = v; }
    public String getTemporalRunId() { return temporalRunId; }
    public void setTemporalRunId(String v) { this.temporalRunId = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Map<String, Object> getInputPayload() { return inputPayload; }
    public void setInputPayload(Map<String, Object> v) { this.inputPayload = v; }
    public Map<String, Object> getOutputPayload() { return outputPayload; }
    public void setOutputPayload(Map<String, Object> v) { this.outputPayload = v; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime v) { this.startedAt = v; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer v) { this.durationMs = v; }
}
