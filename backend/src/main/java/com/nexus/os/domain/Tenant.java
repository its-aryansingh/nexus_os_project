package com.nexus.os.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The root multi-tenancy partition. Every other tenant-scoped row carries
 * a {@code tenant_id} foreign key, and every tenant-scoped table is under
 * PostgreSQL Row-Level Security gated on this id.
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.active;

    @Column(name = "monthly_usd_budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyUsdBudget = new BigDecimal("50.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Plan plan = Plan.starter;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> settings = Map.of();

    public enum Status { active, suspended, trial }
    public enum Plan   { starter, pro, enterprise }

    // ── accessors ─────────────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public BigDecimal getMonthlyUsdBudget() { return monthlyUsdBudget; }
    public void setMonthlyUsdBudget(BigDecimal v) { this.monthlyUsdBudget = v; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }
}
