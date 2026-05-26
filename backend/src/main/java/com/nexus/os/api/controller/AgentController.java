package com.nexus.os.api.controller;

import com.nexus.os.domain.AgentEntity;
import com.nexus.os.domain.AgentRepository;
import com.nexus.os.tenancy.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRepository agents;

    public AgentController(AgentRepository agents) {
        this.agents = agents;
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
        return agents.save(agent);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        agents.deleteById(id);
    }
}
