# ADR 0005 — MCP and A2A from Day 1 (Not Bolted On Later)

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect)

## Context

Two open agent protocols reached Linux Foundation stewardship in 2025:
- **MCP (Model Context Protocol)** — Anthropic, donated December 2025. Standardizes tool exposure.
- **A2A (Agent-to-Agent)** — Google, donated June 2025. Standardizes cross-framework agent invocation.

By 2026 Q2:
- MCP has 97M+ monthly SDK downloads.
- Every major framework (LangGraph, CrewAI, AutoGen, ADK, Spring AI, LangChain4j) supports both natively or through adapters.
- A Java A2A-MCP bridge exists.

Question: do we treat these as foundational primitives or as optional v0.x add-ons?

## Decision

**Foundational.** Both MCP and A2A are first-class from v0.1.

- `integrations/mcp/` exposes Nexus tools via MCP server **and** consumes external MCP servers.
- `integrations/a2a/` exposes Nexus agents via A2A **and** invokes peer A2A agents.

## Consequences

### Positive
- **Interop wins** — Nexus agents are reachable from Claude Desktop / Cursor / any MCP client. Nexus can call LangGraph / CrewAI / AutoGen via A2A. We participate in the ecosystem instead of fighting it.
- **No "framework war" lock-in** — customers can adopt Nexus without abandoning their existing agents.
- **Capstone signal** — being early on a standards-track is a strong signal to evaluators.
- **Future-proof** — the protocol becomes more central; we're already there.

### Negative / Trade-offs
- **More surface to implement** — MCP server + client, A2A server + client = 4 modules vs none.
- **Protocol churn** — pre-1.0 protocols may evolve; we'll need to track spec updates.
- **Testing complexity** — interop tests need real peer agents (mocked in v0.1; live in v0.2).

### Alternative rejected
- **Defer until v0.3**: Would be cheaper short-term but defeats the differentiation. Late adopters look indistinguishable from competitors.

## References
- `backend/src/main/java/com/nexus/os/integrations/mcp/**`
- `backend/src/main/java/com/nexus/os/integrations/a2a/**`
- `docs/COMPETITIVE_ANALYSIS.md` §5 (elevator pitch)
- MCP spec: `modelcontextprotocol.io`
- A2A spec: `a2a-protocol.org`
