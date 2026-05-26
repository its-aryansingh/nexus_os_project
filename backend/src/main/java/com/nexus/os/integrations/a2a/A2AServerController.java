package com.nexus.os.integrations.a2a;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the A2A agent card at the well-known location. See
 * docs/ADRS/0005-mcp-and-a2a-from-day-1.md.
 */
@RestController
public class A2AServerController {

    private final String baseUrl;

    public A2AServerController(@Value("${nexus.a2a.base-url:http://localhost:8083}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @GetMapping("/.well-known/agent.json")
    public A2AAgentCard card() {
        return A2AAgentCard.nexusDefault(baseUrl);
    }
}
