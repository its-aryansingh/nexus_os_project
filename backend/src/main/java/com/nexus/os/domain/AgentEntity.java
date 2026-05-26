package com.nexus.os.domain;

import jakarta.persistence.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted agent definition. Distinct from {@link com.nexus.os.agents.NexusAgent}
 * which is the runtime LangChain4j wrapper. This entity holds the configuration
 * an operator authors via the Agent Studio.
 */
@Entity
@Table(name = "agents")
public class AgentEntity extends BaseEntity {

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

    @Column(columnDefinition = "text")
    private String persona;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<Map<String, Object>> capabilities = List.of();

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> tools = List.of();

    @Column(name = "model_preference", nullable = false, length = 64)
    private String modelPreference = "gpt-4o-mini";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ── accessors ─────────────────────────────────────────────────────────────
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
    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }
    public List<Map<String, Object>> getCapabilities() { return capabilities; }
    public void setCapabilities(List<Map<String, Object>> capabilities) { this.capabilities = capabilities; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    public String getModelPreference() { return modelPreference; }
    public void setModelPreference(String modelPreference) { this.modelPreference = modelPreference; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
