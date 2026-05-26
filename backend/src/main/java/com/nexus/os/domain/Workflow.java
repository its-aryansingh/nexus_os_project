package com.nexus.os.domain;

import jakarta.persistence.*;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflows")
public class Workflow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> definition = Map.of();

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "temporal_workflow_type", nullable = false, length = 255)
    private String temporalWorkflowType = "AgentOrchestrationWorkflow";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getDefinition() { return definition; }
    public void setDefinition(Map<String, Object> definition) { this.definition = definition; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getTemporalWorkflowType() { return temporalWorkflowType; }
    public void setTemporalWorkflowType(String t) { this.temporalWorkflowType = t; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
