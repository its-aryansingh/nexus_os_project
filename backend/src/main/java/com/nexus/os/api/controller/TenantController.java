package com.nexus.os.api.controller;

import com.nexus.os.domain.Tenant;
import com.nexus.os.domain.TenantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenants;

    public TenantController(TenantRepository tenants) {
        this.tenants = tenants;
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
        return tenants.save(tenant);
    }
}
