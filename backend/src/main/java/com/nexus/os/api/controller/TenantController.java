package com.nexus.os.api.controller;

import com.nexus.os.domain.Tenant;
import com.nexus.os.domain.TenantRepository;
import com.nexus.os.observability.AuditLogger;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenants;
    private final AuditLogger audit;

    public TenantController(TenantRepository tenants, AuditLogger audit) {
        this.tenants = tenants;
        this.audit = audit;
    }

    @GetMapping
    public List<Tenant> list() {
        return tenants.findAll();
    }

    @GetMapping("/{id}")
    public Tenant get(@PathVariable UUID id) {
        return tenants.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));
    }

    @PostMapping
    public Tenant create(@RequestBody Tenant tenant) {
        final var saved = tenants.save(tenant);
        audit.log("tenant.created", Map.of(
                "tenantId", saved.getId(),
                "slug", saved.getSlug(),
                "plan", saved.getPlan(),
                "budgetUsd", saved.getMonthlyUsdBudget()
        ));
        return saved;
    }
}
