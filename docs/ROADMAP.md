# Nexus OS — Roadmap

> **Cadence:** Phased delivery. Each phase ends with a working demo + green CI + updated docs.
> **Status legend:** ✅ done · 🚧 in progress · ⏳ next · 💤 future · ❌ explicitly excluded

---

## v0.1 — Foundation (Current — 2026-05-26)

**Goal:** Coordination scaffolding + deep architecture docs + bottom-up backend/frontend/infra scaffolds.

| Track | Item | Status |
|---|---|---|
| Coordination | COORDINATION.md + entry-point files + git hooks | ✅ |
| Public docs | ARCHITECTURE.md | ✅ |
| Public docs | SYSTEM_DESIGN.md (capstone centerpiece) | ✅ |
| Public docs | PRD.md | ✅ |
| Public docs | COMPETITIVE_ANALYSIS.md | ✅ |
| Public docs | ROADMAP.md | ✅ |
| Public docs | ADRs (7 records) | ⏳ |
| Backend | Domain models + Flyway migrations | ⏳ |
| Backend | Observability stack (Micrometer + OTel) | ⏳ |
| Backend | Resilience4j config | ⏳ |
| Backend | Kafka producers/consumers + outbox dispatcher | ⏳ |
| Backend | Tenancy (TenantContext + RlsAspect) | ⏳ |
| Backend | Agent enhancements (ModelRouter, PromptCache, HallucinationGuard, Tribunal) | ⏳ |
| Backend | RAG pipeline (Chunking + Embedding + QdrantStore + Retriever) | ⏳ |
| Backend | Integrations stubs (WhatsApp + MCP + A2A) | ⏳ |
| Frontend | Layout + sidebar + page scaffolds | ⏳ |
| Frontend | BFF route handlers | ⏳ |
| Infra | Redis + Jaeger + Prometheus + Grafana in docker-compose | ⏳ |
| CI | Tighten verify-build with Flyway + Testcontainers | ⏳ |

**Demo at end of v0.1:** `docker compose up && mvnw spring-boot:run && npm run dev` → dashboard renders, hits `/actuator/health` returns green, Jaeger UI shows backend startup spans.

---

## v0.2 — End-to-End Differentiators (Next, ~2-3 weeks out)

**Goal:** Make the differentiator features functionally complete + demo-able.

### v0.2a — Agents go live
- 🚧 Real RAG end-to-end: upload doc → chunk → embed → store → retrieve → answer with citation.
- 🚧 Tribunal consensus end-to-end demo: high-stakes query → 3-agent vote → output + disagreement metric.
- 🚧 ModelRouter live: small-first → escalate on uncertainty; cost reduction measurable in Grafana.

### v0.2b — Standards interop
- 🚧 MCP server: expose `search_memory`, `list_workflows`, `start_workflow` as MCP tools; connect Claude Desktop and prove tools listed.
- 🚧 MCP client: connect a Nexus tenant to the GitHub MCP server; agent can call `gh_search`.
- 🚧 A2A server: expose Nexus agents at `/.well-known/agent.json`; a peer LangGraph agent can invoke them.
- 🚧 A2A client: from Nexus, call a hosted CrewAI agent via A2A; chain it into a Nexus workflow.

### v0.2c — Channels go live
- 🚧 WhatsApp E2E: Twilio webhook → Kafka → Temporal → Agent → response back via Twilio.
- 🚧 Web chat E2E: SSE streaming token-by-token from agent.

### v0.2d — Agent Studio MVP
- 🚧 React Flow editor with drag-drop agents + connections.
- 🚧 Save → POST workflow definition → registered as Temporal workflow.
- 🚧 Deploy → workflow live; new runs use the new definition.

### v0.2e — Operations
- 🚧 Cost dashboard UI (Grafana embed + custom Next.js views).
- 🚧 Time-travel replay UI for any historical workflow run.
- 🚧 Tenant management page (add/edit tenants, budgets).

**Demo at end of v0.2:** Live WhatsApp conversation flows through full Nexus pipeline; the same agent is invoked by an external LangGraph workflow via A2A; cost dashboard shows USD spent in real time.

---

## v0.3 — Production-Readiness (~1 month out)

**Goal:** Make it deployable to production for a real (small) customer.

| Track | Item |
|---|---|
| Auth | Auth0 / Keycloak OIDC integration; tenant claims in JWT |
| Authz | RBAC (`OWNER`, `ADMIN`, `OPERATOR`, `VIEWER`) enforced via `@PreAuthorize` |
| Budget | Per-tenant USD cap; circuit breaker on budget exceed; admin alert email |
| BYOM | Ollama integration tested + documented |
| Replicate | Postgres primary + 2 replicas; read-write split via routing aspect |
| Cluster | 3-broker Kafka with KRaft quorum |
| Cluster | 3-node Qdrant cluster |
| Templates | Workflow template library — "Customer Support", "Outreach", "Research", "Compliance Review" |
| Self-mutation | Sandboxed dynamic capability registration with Tribunal approval gate |
| Compliance | Audit log export (JSON/CSV per tenant); DPDP/GDPR data-erasure endpoint |
| Compliance | Vernacular drafting (Hindi/Hinglish) for Indian market |
| Mobile | Mobile companion app scope/spec doc |
| Channels | Slack + Discord + Email connectors |
| K8s | Helm chart + manifests; HPA on backend + workers |
| Observability | Loki for log aggregation; Tempo for trace storage (vs Jaeger in-mem) |
| Security | mTLS via service mesh (Linkerd/Istio) |
| Security | Egress allow-list; OpenAI / Anthropic / Twilio only |
| Security | Secret rotation playbook |

**Demo at end of v0.3:** Helm-install Nexus OS on a k3s cluster on AWS in 30 minutes; configure first tenant; send live WhatsApp traffic; show $X spent on Grafana; show audit log download.

---

## v1.0 — Public Launch (~3 months out)

**Goal:** Open-source-ready, multi-tenant SaaS-ready, production-hardened.

| Track | Item |
|---|---|
| Scale | Postgres sharding by `tenant_id` hash |
| Scale | Multi-region deploy (read-local, write-home) |
| Marketing | Public landing page + demo video |
| Docs | Docusaurus / VitePress public docs site |
| Marketplace | Agent marketplace (community agents installable per tenant) |
| Mobile | Mobile app v1 (iOS + Android via React Native or Flutter) |
| Compliance | SOC2 readiness checklist |
| License | MIT open-source release (post-capstone evaluation) |

---

## v1.x — Beyond launch (💤 future)

- Federated learning across tenants (privacy-preserving capability sharing)
- Custom embedding model fine-tuning per tenant
- Agent A/B testing framework
- Cost optimization advisor (Nexus suggests cheaper routes)
- Voice channels (Twilio Voice + Whisper)
- Image generation channels (DALL-E / Stable Diffusion)
- Video / audio summarization workflows

---

## Explicit non-goals (❌)

These will not be built, by design:

- ❌ Sub-100ms latency real-time chat (async by design)
- ❌ Custom LLM training (we orchestrate, not train)
- ❌ A consumer-facing chat app (we're enterprise infra)
- ❌ Strong cross-service consistency (sagas + eventual consistency only)
- ❌ Built-from-scratch infra (k8s controller, custom Kafka, etc.) — we compose existing systems
- ❌ Lock-in to any cloud (AWS-only or GCP-only) — multi-cloud-portable from day 1

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Temporal complexity slows feature delivery | medium | medium | Workflow templates library; thin activity wrappers |
| LangChain4j API churn (1.x major releases) | medium | low | Anti-corruption layer in `agents/` wraps LangChain types |
| OpenAI quota / pricing changes | high | medium | BYOM (Ollama) + ModelRouter cost-aware design |
| Multi-tenant data leak | low | catastrophic | RLS + repo-layer filtering + integration tests assert isolation |
| Capstone scope creep | high | medium | This roadmap is the cap; anything off-roadmap goes to v1.x list |
| Solo-developer bandwidth | high | high | Roadmap phases are independently demo-able; can ship at any point |
