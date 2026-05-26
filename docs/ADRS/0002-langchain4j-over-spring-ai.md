# ADR 0002 — LangChain4j Over Spring AI

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect)

## Context

Two mature Java AI integration libraries exist in 2026:
1. **Spring AI 1.1** — Spring-team-owned; ties into ApplicationContext, Actuator, Micrometer.
2. **LangChain4j 0.35 (→ 1.3+)** — community-driven; framework-agnostic; rich RAG/document loaders; native MCP + A2A as of 1.3.

Nexus OS needs:
- Native MCP and A2A support (2026 standards)
- Strong RAG primitives (chunking, embedding, retrieval)
- Future portability (could move from Spring Boot to Quarkus if perf demands it)
- Multi-provider support (OpenAI, Anthropic, Ollama, Together, etc.)

## Decision

Use **LangChain4j** as the AI framework.

## Consequences

### Positive
- **MCP + A2A native** in LangChain4j 1.3 `langchain4j-agentic` and `langchain4j-agentic-a2a` modules.
- **Provider diversity** — switch between OpenAI / Anthropic / Ollama by config; OpenAI-compatible base URL covers ~20 providers.
- **AI Services pattern** — interface + annotations generates the implementation; clean, testable.
- **Document loaders** — better than Spring AI for messy formats (PDF, DOCX).
- **Framework-agnostic** — survives a future Quarkus migration.

### Negative / Trade-offs
- **No native Spring observability** — we re-implement what Spring AI's Actuator integration provides automatically. Mitigation: our `observability/` module wraps every LLM call in OpenTelemetry spans + Micrometer counters.
- **Manual bean wiring** — Spring AI auto-configures providers; we configure them ourselves. Mitigation: thin `LangChainConfig` class.

### Alternatives rejected
- **Spring AI:** Tighter Spring integration but coupled to ApplicationContext. Slower releases. Smaller community than LangChain4j.
- **Direct OpenAI SDK:** No abstraction; lock-in to OpenAI; manual RAG implementation.

## References
- `backend/src/main/java/com/nexus/os/agents/**`
- `docs/COMPETITIVE_ANALYSIS.md` §3.1 (Spring AI comparison)
- LangChain4j 1.3 release notes
