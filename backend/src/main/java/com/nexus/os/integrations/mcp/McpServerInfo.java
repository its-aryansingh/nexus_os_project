package com.nexus.os.integrations.mcp;

import java.util.List;
import java.util.Map;

/**
 * Stub MCP server descriptor. Real protocol implementation lands in v0.2
 * once the Java MCP SDK is added (see docs/ADRS/0005-mcp-and-a2a-from-day-1.md).
 *
 * <p>This v0.1 type lets the rest of the system reference MCP tools by
 * shape — the persistence layer (V006 mcp_servers table) already supports
 * the full schema.
 */
public record McpServerInfo(
        String name,
        String baseUrl,
        String transport,           // sse | http | stdio
        List<McpTool> tools
) {
    public record McpTool(String name, String description, Map<String, Object> inputSchema) {}
}
