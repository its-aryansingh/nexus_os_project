package com.nexus.os.api.controller;

import com.nexus.os.domain.AgentEntity;
import com.nexus.os.domain.AgentRepository;
import com.nexus.os.observability.AuditLogger;
import com.nexus.os.tenancy.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRepository agents;
    private final AuditLogger audit;

    public AgentController(AgentRepository agents, AuditLogger audit) {
        this.agents = agents;
        this.audit = audit;
    }

    @GetMapping
    public List<AgentEntity> list() {
        return agents.findByTenantIdAndActive(TenantContext.require(), true);
    }

    @GetMapping("/{id}")
    public AgentEntity get(@PathVariable UUID id) {
        return agents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
    }

    @PostMapping
    public AgentEntity create(@RequestBody AgentEntity agent) {
        agent.setTenantId(TenantContext.require());
        final var saved = agents.save(agent);
        audit.log("agent.created", Map.of(
                "agentId", saved.getId(),
                "slug", saved.getSlug(),
                "model", saved.getModelPreference()
        ));
        return saved;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        agents.deleteById(id);
        audit.log("agent.deleted", Map.of("agentId", id));
    }
}
