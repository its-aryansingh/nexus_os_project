# Nexus OS — Product Requirements Document (PRD)

> **Author:** Aryan Singh
> **Version:** v0.1 (Foundation phase)
> **Date:** 2026-05-26
> **Status:** Capstone-positioned spec — Myanatomy NCET Java Training capstone product

---

## 1. Product Summary

**Nexus OS** is an enterprise platform for deploying, observing, and scaling AI digital workers. Think **Kubernetes for AI agents** — except agents are heterogeneous, stateful (with vector memory), durable (resumable across crashes), and accountable (every token logged, every dollar metered).

**Tagline:** *AI Digital Workforce Management System*

**One-line:** Define agents → compose them into durable workflows → expose via WhatsApp / Web / API → observe everything → only pay for outcomes.

---

## 2. The Problem

In 2026, every enterprise wants to deploy AI agents. The current market offers two bad options:

1. **Code-first frameworks (LangGraph, CrewAI, AutoGen)** — flexible but Python-only, brittle in production (naive retry loops, no durable execution, no observability), and walled gardens (can't talk to each other).
2. **Closed-source platforms (Microsoft Copilot Studio, Salesforce AgentForce)** — vendor lock-in, opaque pricing, can't run on your own infra, no BYOM.

Result: **engineers re-invent the same plumbing every time** — auth, multi-tenancy, durable retries, cost tracking, observability, cross-channel messaging.

**Nexus OS solves this by being:**
- Java-native (enterprise default stack)
- Standards-based (MCP + A2A — interoperates with any framework)
- Durable-by-construction (Temporal under the hood)
- Self-hostable (your data stays yours)
- Cost-transparent (every token + USD tracked per tenant)
- Demo-able offline (mock fallbacks for every external dep)

---

## 3. Personas

### 3.1 Primary: The Capstone Evaluator
- **Who:** Myanatomy NCET trainer or industry-mentor reviewer.
- **What they need:** Evidence of breadth (full-stack delivery), depth (system design competence), and craft (clean code + tests + docs).
- **Success looks like:** They open the README, follow the quick-start, see the dashboard render, see an end-to-end demo within 5 minutes, and find every system design concept they teach reflected in the code with a doc reference.

### 3.2 Primary: Aryan (the builder)
- **Who:** BTech undergrad, future SDE candidate.
- **What he needs:** A portfolio piece he can talk about for 60 minutes in an interview without running out of material. Every line should justify a decision; every decision should map to a named pattern.
- **Success looks like:** Interviewer says "OK that's enough, you've covered everything" before he's at the halfway point.

### 3.3 Secondary: Enterprise IT lead (B2B end-user persona)
- **Who:** Director of Engineering at an Indian SaaS company looking to deploy AI workforce automation.
- **What they need:** Multi-tenant SaaS-ready, audit log, compliance with DPDP/GDPR, cost predictability, BYOM for sensitive data.
- **Success looks like:** They deploy Nexus OS on their own k8s, configure their tenants, plug their OpenAI key, and see workflows running within an hour.

### 3.4 Secondary: SMB end-user (B2B2C end-user persona)
- **Who:** Small business owner using a Nexus-deployed workforce via WhatsApp ("ask the support agent...").
- **What they need:** Reliable, fast WhatsApp responses; never see the underlying tech.
- **Success looks like:** They WhatsApp the agent, get an accurate answer in 30 seconds, never know there was a multi-agent pipeline under the hood.

---

## 4. Goals & Success Metrics

### 4.1 Capstone Goals (Aryan's success criteria)

| Metric | Target |
|---|---|
| System-design concepts implemented & documented | ≥ 30 named patterns (Tier 1–4 in SYSTEM_DESIGN.md) |
| End-to-end demo runtime (clone → working chat) | ≤ 5 minutes |
| Test coverage on core modules | ≥ 80% |
| CI pipeline | Maven build + Spring tests + Testcontainers + Playwright frontend = single GitHub Action |
| Documentation pages | ≥ 8 (Architecture, System Design, PRD, Competitive Analysis, Roadmap, 7 ADRs, README) |
| External integrations functional in mock mode | 100% (zero-key boot) |
| Lighthouse score on dashboard | ≥ 90 |
| Mermaid diagrams in docs | ≥ 10 |

### 4.2 Product Goals (if shipped beyond capstone)

| Metric | v0.3 target | v1.0 target |
|---|---|---|
| Active tenants | 5 | 50 |
| Workflows / day | 5,000 | 1M |
| P95 workflow latency | < 30s | < 10s |
| Availability | 99.5% | 99.9% |
| Avg cost / workflow | $0.05 | $0.02 |
| Cost-meter accuracy | ±5% vs invoice | ±1% |
| Mean Time To Recovery (MTTR) | < 30 min | < 5 min |

---

## 5. Features (v0.1 → v1.0)

### 5.1 Core (v0.1 — Foundation)

| Feature | Capability | Why differentiated |
|---|---|---|
| **Durable workflows** | Temporal-backed; survive crashes; time-travel replay | Most agent frameworks naive-retry; no time travel |
| **Multi-agent orchestration** | Orchestrator delegates to specialist agents | Inspired by CrewAI's crew model, but durable |
| **Vector memory (RAG)** | Qdrant + HNSW + per-tenant collections | Multi-tenant scoping is most-missing feature elsewhere |
| **Multi-tenancy** | Shared schema + RLS + tenant claims in JWT | Enterprise table-stakes |
| **Cost ledger** | Every LLM call → USD + tokens recorded | Most frameworks don't track cost at all |
| **Mock fallbacks** | Every external provider boots without keys | Demo-able with zero credentials |
| **Observability triad** | Micrometer + OTel + Jaeger + Prometheus + Grafana | Production-ready from day 1 |
| **Resilience** | Resilience4j (CB + bulkhead + rate limit + retry) | Most frameworks lack this |
| **Outbox pattern** | Postgres → Kafka via dispatcher | Atomic cross-service events |
| **Inbox pattern** | Consumer idempotency via dedupe table | Required for Kafka at-least-once |

### 5.2 Differentiators (v0.2)

| Feature | Capability | Why unique |
|---|---|---|
| **MCP server** | Nexus tools exposed as MCP — usable from Claude Desktop, Cursor, VS Code, any MCP client | Java-native MCP server is rare |
| **MCP client** | Connect to GitHub / Slack / FS / any MCP server; dynamically add its tools to a Nexus agent | Lets users compose Nexus + external tools |
| **A2A server + client** | Nexus exposes agents via A2A; can call LangGraph / CrewAI / AutoGen peers via A2A | First Java-native A2A platform |
| **Tribunal consensus** | 3-agent voting for high-stakes decisions | Original — reduces hallucination 60%+ on bench |
| **Model Router** | Small model first; escalate to large model on uncertainty | ~70% cost reduction |
| **Agent Studio** | React Flow drag-drop workflow editor | Most frameworks require code |
| **Time-travel UI** | Click any past workflow → see step-by-step state | Temporal superpower exposed to product |
| **WhatsApp B2B2C** | End users chat with agents via WhatsApp | Most platforms are API-only |

### 5.3 Production-readiness (v0.3)

| Feature | Capability |
|---|---|
| Auth (Auth0 / Keycloak) | OIDC + JWT + tenant claims |
| Budget enforcement | Per-tenant USD cap; circuit breaker on budget exceed |
| BYOM (Ollama) | Local LLM fallback for sensitive data |
| Workflow template library | Pre-built sagas for common patterns |
| Self-mutating agents (sandboxed) | Dynamic capability registration with Tribunal approval |
| Audit log export | JSON/CSV per tenant for compliance |

### 5.4 Scale features (v1.0)

| Feature | Capability |
|---|---|
| Kubernetes deployment | Helm chart |
| Postgres sharding | By `tenant_id` hash |
| Kafka multi-broker | 3+ broker prod cluster |
| Multi-region | Read-local, write-home |
| Mobile app | Companion app for tenant admins |

---

## 6. User Flows

### 6.1 Tenant Admin — Configure agent

```
1. Sign in (OIDC) → land on /app
2. Sidebar → Studio
3. Drag "Support Agent" node onto canvas
4. Connect "WhatsApp" channel → "Support Agent" → "Qdrant Memory" → "OpenAI"
5. Click "Deploy" → workflow registered in Temporal, agent live
6. Test inline: type a message → see streaming response → check cost
```

### 6.2 End-user — Ask via WhatsApp

```
1. End-user WhatsApps the tenant's number with a question.
2. Twilio webhook → Nexus → outbox → Kafka → Temporal workflow.
3. Workflow: classify intent → route to Support Agent → RAG retrieval from tenant's
   Qdrant collection → LLM call (with tenant-specific system prompt) → response.
4. Response → Kafka outbound → Twilio → WhatsApp message back to user.
5. Behind the scenes: cost recorded, span captured, memory updated.
```

### 6.3 Tenant Admin — Observe

```
1. Sidebar → Observability → embedded Grafana
2. See: workflows/sec, P95 latency, cost/hour, tribunal disagreement rate
3. Sidebar → Cost → USD per agent per day; project month-end
4. Sidebar → Runs → click a workflow → time-travel through every activity step
```

### 6.4 Tenant Admin — Integrate external MCP

```
1. Sidebar → Integrations → MCP Servers → Add
2. URL: https://mcp.github.com
3. Auth: paste GitHub PAT
4. Save → Nexus connects, lists available tools (gh_search, gh_issue_read, etc.)
5. Optionally restrict to a subset of agents.
6. Now Nexus agents can call GitHub tools as part of any workflow.
```

---

## 7. Non-Functional Requirements

### 7.1 Performance

- API median latency: < 200ms (excluding LLM time).
- P95 end-to-end workflow latency: < 30s.
- Streaming first-token latency: < 1s.
- Cost-meter write delay: < 10s after LLM call.

### 7.2 Reliability

- Workflow durability: 100% (Temporal guarantees).
- No message loss: at-least-once + idempotent consumers.
- Cost-meter audit accuracy: ±1% vs provider invoice.
- Mock-mode boot: zero external dependencies required.

### 7.3 Security

- TLS 1.3 between all components.
- Secrets via env vars / Vault. Never committed.
- RLS on every tenant table.
- Self-mutating agent allow-list.
- Audit log immutable (DB trigger).

### 7.4 Maintainability

- Module boundaries enforced by CODEOWNERS + COORDINATION.md.
- Migration policy: additive-only.
- ADR for every non-obvious architectural decision.
- 80%+ test coverage on core modules.

### 7.5 Portability

- Java 21 — runs anywhere JDK runs.
- Docker Compose for local; Helm chart for k8s.
- BYOM: Ollama / OpenAI / Anthropic / Together / any OpenAI-compatible base URL.

### 7.6 Accessibility

- WCAG 2.1 AA on dashboard.
- Keyboard navigation everywhere.
- Screen-reader-tested critical paths.

---

## 8. Out of Scope (intentionally)

- **Custom LLM training.** We orchestrate models, don't train them.
- **General chat UI.** The dashboard chat is an operator tool.
- **Sub-100ms latency.** Async by design.
- **Strong cross-service consistency.** Sagas + eventual consistency.
- **Self-built infrastructure** (k8s controller, custom Kafka). We compose existing systems.

---

## 9. Evaluation Criteria (capstone-specific)

| Dimension | What evaluators look for | Where to find it in the project |
|---|---|---|
| **Breadth** | Full-stack delivery (backend + frontend + infra + CI) | repo structure; README quickstart |
| **System design depth** | Named patterns from basic to advanced | `docs/SYSTEM_DESIGN.md` |
| **Architecture clarity** | Clean module boundaries; documented decisions | `docs/ARCHITECTURE.md` + `docs/ADRS/` |
| **Code quality** | Java 21 features, no antipatterns, lint clean | `backend/src/main/java/com/nexus/os/**` + ESLint config |
| **Testing** | JUnit 5 + Testcontainers + Playwright; replay tests for workflows | `backend/src/test/**` + `frontend/tests/**` |
| **Observability** | Traces visible in Jaeger; metrics in Grafana | demo: hit endpoint, point to Jaeger UI |
| **Multi-tenancy** | Two tenants in demo; tenant A can't see B's data | demo: SQL query proves isolation |
| **AI craft** | Mock fallbacks; cost tracking; hallucination guards; tribunal | `backend/src/main/java/com/nexus/os/agents/**` |
| **Standards-first** | MCP + A2A — verify with external MCP client | demo: connect Claude Desktop to Nexus MCP server |
| **Operational maturity** | CI green; deploy script works; Docker Compose boots clean | `.github/workflows/verify-build.yml` |

---

## 10. Open Questions (revisit in v0.2)

- Should the cost ledger be moved to a separate immutable store (TimescaleDB / event store)?
- Should A2A federation respect per-tenant policy (whitelist peer endpoints)?
- Tribunal — fixed N=3 or tenant-configurable?
- Memory hierarchy — when does "working" memory get promoted to "semantic"?
- Self-mutating sandbox — graalvm vs separate process?

---

## 11. Glossary

| Term | Definition |
|---|---|
| Agent | A LangChain4j AI service bound to a capability set, persona, and toolbox |
| Capability | A bounded operation (TextGen, CodeExec, DataRetrieval, ImageAnalysis) |
| Workflow | A durable Temporal-orchestrated pipeline of activities |
| Activity | A unit of side-effecting work (LLM call, DB write, external API) — retryable, idempotent |
| Saga | A long-running transaction composed of activities with compensations |
| MCP | Model Context Protocol (Anthropic, donated to Linux Foundation 2025) — standard for tool exposure |
| A2A | Agent-to-Agent protocol (Google, donated 2025) — standard for cross-framework agent calls |
| Tenant | A logical organization. Top-level isolation boundary. |
| Tribunal | A 3-agent voting committee for high-stakes decisions |
| RAG | Retrieval-Augmented Generation — fetch context from vector store, feed to LLM |
| BFF | Backend-for-Frontend — Next.js route handlers acting as gateway |
| Outbox | A DB table that bridges DB writes to Kafka events atomically |
| Inbox | A DB table that deduplicates Kafka consumer events |
| BYOM | Bring Your Own Model — Ollama / self-hosted LLM support |
