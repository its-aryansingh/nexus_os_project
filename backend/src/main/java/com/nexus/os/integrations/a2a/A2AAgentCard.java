package com.nexus.os.integrations.a2a;

import java.util.List;
import java.util.Map;

/**
 * Skeleton {@code agent.json} payload served at
 * {@code /.well-known/agent.json} (A2A spec) by {@link A2AServerController}.
 *
 * <p>v0.1 ships a single static card describing the Nexus orchestrator;
 * v0.2 generates per-tenant cards based on configured agents.
 */
public record A2AAgentCard(
        String name,
        String description,
        String version,
        List<String> capabilities,
        Map<String, Object> auth,
        Map<String, Object> rateLimit
) {
    public static A2AAgentCard nexusDefault(String baseUrl) {
        return new A2AAgentCard(
                "nexus-os-orchestrator",
                "Nexus OS multi-agent orchestrator — durable workflows, vector memory, cost-transparent.",
                "0.1.0",
                List.of("text-generation", "code-execution", "data-retrieval", "image-analysis"),
                Map.of(
                        "type", "bearer",
                        "discovery_url", baseUrl + "/.well-known/oauth-authorization-server"
                ),
                Map.of(
                        "requests_per_minute", 60,
                        "concurrent", 5
                )
        );
    }
}
