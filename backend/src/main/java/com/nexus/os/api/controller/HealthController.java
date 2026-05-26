package com.nexus.os.api.controller;

import com.nexus.os.agents.Tribunal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Lightweight system-readiness endpoint. Sits alongside Spring Boot's
 * {@code /actuator/health} (which covers the standard liveness/readiness
 * checks); this one returns Nexus-specific feature flags + config snapshot
 * so the dashboard can show "what's wired right now".
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final Tribunal tribunal;

    public HealthController(Tribunal tribunal) {
        this.tribunal = tribunal;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "name", "nexus-os",
                "version", "0.1.0-SNAPSHOT",
                "phase", "foundation",
                "now", OffsetDateTime.now().toString(),
                "tribunal", tribunal.summary(),
                "features", Map.of(
                        "mcpServer", true,
                        "a2aServer", true,
                        "tribunal", true,
                        "modelRouter", true,
                        "promptCache", true,
                        "hallucinationGuard", true,
                        "outboxDispatcher", true,
                        "rlsMultiTenancy", true,
                        "byomOllama", true
                )
        );
    }
}
