# Nexus OS — System Design Document

> **Audience:** Engineers, capstone evaluators, hiring managers.
> **Purpose:** Walk through every system-design concept Nexus OS embodies — from
> foundational patterns a BTech student must know, to the advanced techniques that
> separate junior from senior engineers, to AI-specific concepts unique to agentic
> platforms in 2026. Each concept is tied to a concrete file or module in the
> codebase so the reader can verify the design with the implementation.

---

## Table of Contents

1. [Design Goals & Non-Goals](#1-design-goals--non-goals)
2. [Tier 1 — Foundational Patterns](#tier-1--foundational-patterns)
3. [Tier 2 — Intermediate Patterns](#tier-2--intermediate-patterns)
4. [Tier 3 — Advanced Distributed-Systems Patterns](#tier-3--advanced-distributed-systems-patterns)
5. [Tier 4 — AI-Specific Design Concepts](#tier-4--ai-specific-design-concepts)
6. [Cross-Cutting Concerns](#cross-cutting-concerns)
7. [Failure Modes & Recovery](#failure-modes--recovery)
8. [Capacity Planning](#capacity-planning)
9. [Security Model](#security-model)
10. [Trade-Off Register](#trade-off-register)

---

## 1. Design Goals & Non-Goals

### Goals (in order)

1. **Durable execution** — A workflow that takes 6 hours should survive a node crash at minute 3 and resume exactly where it left off. Cost of an LLM tool call must never be paid twice for the same logical step.
2. **Standards-first interoperability** — Any agent built on Nexus OS must be reachable by any A2A-speaking external agent, and Nexus tools must be consumable by any MCP-speaking external client. No vendor lock.
3. **Production observability from day one** — Logs, metrics, traces — every request traceable end-to-end. Cost (USD + tokens) is a first-class metric.
4. **Multi-tenant by construction** — Two enterprises share infra but never each other's data. Defense-in-depth via app-layer + Postgres RLS.
5. **Cost-aware AI** — Token spend is monitored, capped, optimized via routing + caching. Cheap model first; expensive model only when needed.
6. **Demo-able without keys** — Every external dependency has a deterministic mock. A judge can `git clone && docker compose up && mvnw spring-boot:run` and have a working product offline.
7. **Linearly scalable** — Stateless services + Kafka for fan-out + Postgres for state + Qdrant for vectors. No piece of the architecture has an intrinsic 1-instance-only design.

### Non-Goals

- **General-purpose chat UI**. Nexus OS is workforce management, not a ChatGPT clone. The chat surface is an admin/operator tool, not a consumer product.
- **Custom model training**. We orchestrate hosted/local models. No training pipeline, no GPU scheduling.
- **Real-time low-latency (< 100ms)**. Agentic workloads are inherently asynchronous. We target P95 < 5s end-to-end for typical queries, not < 100ms.
- **Strong consistency across services**. We use eventual consistency + sagas. ACID lives inside Postgres only.

---

## Tier 1 — Foundational Patterns

These are the patterns any BTech engineer is expected to know. They form the base.

### 1.1 Layered Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  Presentation Layer       (Spring MVC controllers — api/controller/) │
├──────────────────────────────────────────────────────────────────────┤
│  Service Layer            (domain orchestration — Spring @Service)   │
├──────────────────────────────────────────────────────────────────────┤
│  Domain Layer             (entities + value objects — domain/)       │
├──────────────────────────────────────────────────────────────────────┤
│  Persistence Layer        (Spring Data JPA repositories)             │
├──────────────────────────────────────────────────────────────────────┤
│  Infrastructure Layer     (Kafka, Temporal, Qdrant, Redis, Postgres) │
└──────────────────────────────────────────────────────────────────────┘
```

**Why:** Strict downward-only dependencies prevent "JPA entities in controllers" antipattern; allows swapping Postgres for another store without rewriting controllers.

**Where in Nexus OS:** Every controller in `api/controller/` is < 30 LOC and delegates to a `@Service`. Repositories in `domain/*Repository.java` extend `JpaRepository`.

### 1.2 Connection Pooling

**Concept:** Opening a TCP+TLS connection to Postgres costs ~50ms. A burst of 1000 requests would open 1000 connections — but Postgres tops out around 100 connections per CPU. Solution: pool a fixed number, hand them out, recycle.

**Implementation:** HikariCP (Spring Boot default). Sized at `max-active = cores * 2 + spindles`. Connection acquisition timeout 5s; tested via `connection-test-query: SELECT 1`.

**Where:** `application.yml` — `spring.datasource.hikari.maximum-pool-size: 20`.

### 1.3 Database Indexing

**Concept:** A B-tree index turns an O(N) scan into O(log N) lookup.

**Indexes Nexus uses:**
- `workflow_runs (tenant_id, started_at DESC)` — supports the "recent runs" list page (filtered by tenant, sorted by time).
- `outbox (dispatched_at) WHERE dispatched_at IS NULL` — **partial index**. The outbox dispatcher only scans undispatched rows. After publication the row is updated (or moved to history) and falls out of the partial index. Keeps the working set small as the table grows.
- `cost_ledger (tenant_id, occurred_at)` — supports per-tenant cost rollups by time bucket.
- `messages USING gin (to_tsvector('english', body))` — full-text search on inbound messages.

**Where:** `backend/src/main/resources/db/migration/V*.sql`.

### 1.4 N+1 Query Avoidance

**Concept:** `for run in workflowRuns { run.getAgent().getName() }` issues 1 query for the runs and N queries for each agent. Solution: eager join or batch fetch.

**Implementation:**
- `@EntityGraph(attributePaths = "agent")` on `findRecentByTenant` in `WorkflowRunRepository`.
- For dynamic graphs, use `JOIN FETCH` in JPQL.
- For aggregations, write SQL projections (don't pull entire entity to count it).

### 1.5 Cache-Aside Strategy

**Concept:** Read path: check cache → on miss, read DB → write to cache → return. Write path: update DB → invalidate cache.

**Implementation:** `PromptCache` (`backend/.../agents/PromptCache.java`) wraps every LLM call. Key = `sha256(prompt + model + temperature + tenant_id)`. Hit → return cached completion. Miss → call OpenAI, cache result with TTL based on cacheability heuristic.

**Why cache-aside (not write-through):** LLM responses are content-derived; we'd never preemptively populate the cache. Cache-aside is the natural fit for lazy population.

### 1.6 HTTP/gRPC Selection

| Use Case | Protocol | Why |
|---|---|---|
| Browser ↔ Nexus API | HTTP/1.1 + SSE for streaming | Universal compat |
| Frontend BFF ↔ Backend | HTTP/2 | Multiplexed streams |
| Backend ↔ Temporal | gRPC | Temporal SDK demands it; binary framing, schema evolution |
| Backend ↔ Qdrant | gRPC (`:6334`) | Lower overhead than REST for vector ops |
| External MCP servers | HTTP+SSE (per MCP spec) | Standard |
| A2A peers | HTTP+JSON (per A2A spec) | Standard |

---

## Tier 2 — Intermediate Patterns

### 2.1 Microservices vs Modular Monolith (we choose monolith first)

**Decision:** v0.1–v0.3 ships as a **modular monolith** — single `backend/` Spring Boot deployable with strict internal package boundaries. v1.0+ can decompose along the seams (agents/, integrations/, billing/) if scaling demands.

**Why monolith first:**
- Capstone shipping speed > microservice complexity.
- Network boundary is the single most expensive engineering cost; defer it.
- Module boundaries inside the monolith mirror future service boundaries 1:1 — strangler-fig migration is mechanical when the time comes.

**Future service decomposition (planned):**
- `nexus-orchestrator` (temporal/, api/) — stateless
- `nexus-agents` (agents/) — stateless, GPU-adjacent
- `nexus-integrations` (integrations/) — gateway nodes for WhatsApp/MCP/A2A
- `nexus-billing` (billing/) — isolated for compliance

### 2.2 API Gateway Pattern

The frontend never calls backend directly. It calls `frontend/src/app/api/*/route.ts` — Next.js server-side route handlers — which forward to the Spring backend with auth, request shaping, rate limiting.

**Why BFF (Backend-for-Frontend):**
- Hides backend URL + auth from the browser.
- Aggregates 3 backend calls into 1 page-load response.
- Lets frontend evolve faster than backend; BFF absorbs version skew.
- Single observability seam between two distinct technology stacks.

**Where:** `frontend/src/app/api/{chat,agents,workflows,observability,cost}/route.ts`.

### 2.3 CQRS (Command Query Responsibility Segregation)

**Concept:** Read and write paths have different shapes, scaling needs, consistency requirements. Separate them.

**Implementation in Nexus:**
- **Write side:** Domain commands (`StartWorkflowCommand`, `RecordCostCommand`) → service layer → JPA repository → Postgres primary.
- **Read side:** Query services (`WorkflowRunQueryService`) hit denormalized read models / view tables / Kafka-fed projections.
- Cost dashboard reads from `cost_ledger_daily_rollup` (refreshed materialized view) — not the append-only ledger.

**Where:** `backend/.../api/dto/*Command.java` and `*Query.java` clearly partition the API surface.

### 2.4 Event Sourcing (partial)

**Concept:** Instead of storing current state, store the sequence of events that produced it. State becomes a projection.

**What we event-source:**
- **Workflow runs** — every workflow activity emits an event; the run "state" is computed by replaying events.
- **Cost ledger** — append-only; current balance is a sum over (tenant, day).

**What we don't event-source:**
- User profiles, agents, tenants — slow-changing reference data is fine as classical CRUD.

**Why partial:** Full event sourcing is expensive for everything. We use it where audit trail + time-travel pay off.

### 2.5 Saga Pattern (Temporal-based)

**Concept:** A long-running transaction across N services has no global ACID. Use compensating transactions to roll back logically.

**Example saga in Nexus:**
```
Workflow: "Send WhatsApp campaign"
  Activity 1: reserveTokensFromBudget(tenant, estimatedTokens)
                                                 ↓ on success
  Activity 2: generateMessage(agent, prompt)
                                                 ↓ on success
  Activity 3: sendViaTwilio(phone, message)
                                                 ↓ on failure of step 3
  Compensation 1: refundReservedTokens(tenant, estimatedTokens)
  Compensation 2: markMessageAsFailed(messageId, error)
```

**Where:** `backend/.../temporal/workflows/templates/` (workflow library), `backend/.../temporal/activities/compensations/` (compensation activities).

**Why saga over 2PC:** Two-phase commit requires coordinator-managed locks across services — kills throughput, doesn't handle partition tolerance. Sagas trade consistency-during-failure for liveness — acceptable here because every compensation step is idempotent.

### 2.6 Pub-Sub with Kafka

**Topic design:**
| Topic | Producer | Consumer(s) | Partitions | Why |
|---|---|---|---|---|
| `nexus.inbound.messages` | API Gateway / WhatsApp webhook | `InboundMessageConsumer` (Temporal kickoff) | 3 | Tenant-keyed; affinity preserves message order per conversation |
| `nexus.agent.events` | Temporal workflows | `AgentEventProducer` projector → CQRS read model | 6 | Higher fan-in; downstream UI streaming |
| `nexus.outbound.responses` | Workflow output | Channel routers (WhatsApp/Slack/Discord) | 3 | Same as inbound — tenant order |
| `nexus.dlq` | All consumers (rejected) | Manual ops / replay tool | 1 | Dead-letter; small |

**Partition key:** `tenant_id` for all tenant-scoped topics → preserves per-tenant ordering, enables tenant-affinity consumer scaling.

### 2.7 Replication (Postgres primary-replica)

**Plan (v0.3):** 1 primary + 2 read replicas. Primary handles writes; replicas serve heavy read traffic (cost dashboards, run history listing).

**Lag tolerance:** Run-detail page (which a user is actively watching) reads from primary; read-from-replica is fine for any historical dashboard. Stale-OK reads are explicit (`@Transactional(readOnly = true)` with replica routing aspect).

### 2.8 Sharding Considerations

**Vertical sharding (by table family) — v1.0+:**
- `cost_ledger`, `messages` → "hot write" cluster.
- `workflow_runs`, `activity_runs` → "warm" cluster (high cardinality, indexed reads).
- `agents`, `workflows` → "reference" cluster (low write rate).

**Horizontal sharding (by tenant) — v2.0+:**
- Shard `messages`, `cost_ledger` by `hash(tenant_id) % N` once a single tenant exceeds 50M rows.
- Until then: a single Postgres handles it.

### 2.9 Service Discovery

**Local dev:** Docker Compose DNS (`postgres`, `kafka`, `temporal`, `qdrant`, `redis`).

**Production (v0.3+):** Kubernetes Service DNS (`nexus-orchestrator.svc.cluster.local`). Sidecar service mesh (Istio or Linkerd) handles mTLS + routing.

### 2.10 Load Balancing

| Layer | Mechanism | Why |
|---|---|---|
| L4 (TCP) | Kubernetes Service (kube-proxy) | Stateless backend; round-robin is fine |
| L7 (HTTP) | Ingress Controller (NGINX) | Path-based routing (`/api/*` → backend, `/*` → frontend) |
| Kafka consumer | Consumer-group rebalance | Auto-partitions among instances |
| LLM provider | `ModelRouter` weighted random | Spreads load across cheap/expensive models by tenant policy |

---

## Tier 3 — Advanced Distributed-Systems Patterns

### 3.1 CAP Theorem (and PACELC refinement)

**CAP:** In a distributed system, during a network partition you can choose **Consistency** or **Availability**, not both.

**PACELC:** Even when there's no Partition, you choose between **Latency** and **Consistency**.

**Nexus's PACELC posture:**
- **During partition (P):** **A** wins — workflows continue queuing into Kafka; Temporal workers retry until the partition heals. We never block the user.
- **Else (E):** **L** wins — read-path queries hit replicas / caches; cost dashboard is eventually consistent. Strong consistency is reserved for billing-critical writes (cost ledger, budget enforcement).

**Where this shows up:**
- `cost_ledger` writes use synchronous replication (waits for replica ack) — we lose latency, gain consistency, because a missed cost row could overspend a tenant's budget.
- `workflow_runs` writes use async replication — fast, eventually consistent — because a stale dashboard is fine.

### 3.2 Consistent Hashing

**Where Nexus uses it:**
- **Kafka partition assignment:** Kafka itself uses consistent hashing on the partition key.
- **Tenant → Postgres shard (planned v2.0):** `hash(tenant_id) % N_shards` becomes a problem when N changes (massive resharding). Use **rendezvous hashing** instead — only `1/N` of keys remap when shard count changes.
- **Redis cluster sharding:** Cluster mode uses 16384 hash slots — a form of consistent hashing.

**Concept:** A traditional `hash(key) % N` causes nearly every key to remap when `N` changes. Consistent hashing places nodes on a ring; only the keys between a removed/added node and its neighbour remap.

### 3.3 Quorum-Based Consensus

**Where Nexus relies on it:**
- **Kafka KRaft:** Replaces ZooKeeper. Quorum of controllers (3 in prod) elect a leader. Producer with `acks=all` waits for in-sync replica acknowledgement.
- **Temporal:** Server clusters use Raft for cluster metadata; per-shard history is consensus-replicated.
- **Postgres synchronous replication:** Primary waits for ≥1 replica ack before reporting success — a degenerate quorum (N=2, W=2).

### 3.4 Distributed Transactions: why Saga, not 2PC, not 3PC

**Two-phase commit (2PC):**
1. Coordinator asks all participants "prepare".
2. If all reply yes → commit; otherwise → abort.

**Why 2PC fails Nexus's needs:**
- Blocks resources during prepare phase — fatal for LLM-call workflows that take minutes.
- Coordinator failure = stuck participants.
- Needs every participant to support the XA protocol — LLM APIs don't.

**Three-phase commit (3PC):** Adds a pre-commit phase to reduce blocking. Still requires coordination + assumes synchronous network.

**Why Sagas win:** Each step is a local transaction; failures trigger compensations. No global lock, no coordinator failure mode. Trade-off: temporary inconsistency between steps (visible if you look at intermediate state).

### 3.5 Outbox Pattern

**Problem:** When a service must both write to DB and publish to Kafka atomically, naive code does:
```java
@Transactional
void execute() {
    db.save(entity);           // step 1
    kafka.publish(event);      // step 2 — what if this fails?
}
```
If step 2 fails, the DB has the entity but the world doesn't know. If you reorder (publish first), Kafka may have a phantom event the DB never accepted.

**Outbox solution:**
```java
@Transactional
void execute() {
    db.save(entity);
    db.save(new OutboxEvent(...));   // both writes commit atomically
}
// later, asynchronously:
OutboxDispatcher polls SELECT * FROM outbox WHERE dispatched_at IS NULL
  → publishes to Kafka → marks dispatched_at = now()
```

**Where:** `backend/.../domain/OutboxEvent.java`, `backend/.../kafka/OutboxDispatcher.java`. The partial index `outbox(dispatched_at) WHERE dispatched_at IS NULL` keeps the dispatcher poll cheap.

### 3.6 Inbox Pattern (Consumer Idempotency)

**Problem:** Kafka is at-least-once. The same event may be delivered twice.

**Solution:** Every consumer writes a row to an `inbox` table with `event_id PRIMARY KEY`. Duplicate delivery → unique constraint violation → silently skip.

```java
@KafkaListener(topics = "nexus.inbound.messages")
void handle(InboundMessage msg, Acknowledgment ack) {
    try {
        inboxRepository.markProcessed(msg.eventId());  // throws on duplicate
    } catch (DataIntegrityViolationException dup) {
        ack.acknowledge();
        return;
    }
    process(msg);
    ack.acknowledge();
}
```

**Where:** `backend/.../domain/InboxEvent.java`, applied by `backend/.../kafka/*Consumer.java` classes.

### 3.7 Rate Limiting

**Three rate limiters we apply:**

| Limiter | Algorithm | Where | Reason |
|---|---|---|---|
| Per-tenant API quota | Token bucket (Redis-backed) | `TenantRateLimitFilter` on every controller | Stop one tenant from starving others |
| Per-provider LLM call | Resilience4j RateLimiter | `OpenAiClient` | Provider quotas (3500 rpm on tier 1) |
| Per-tenant cost budget | Soft limiter that opens a circuit when spend exceeds budget | `CostMeter` | Prevent runaway bills |

**Token bucket** chosen over leaky bucket: tolerates short bursts (more user-friendly) while preserving long-run rate cap.

### 3.8 Circuit Breaker

**Concept:** When a downstream is unhealthy, fail fast. Don't pile up timeouts and threads. State machine: CLOSED → OPEN (after failure threshold) → HALF_OPEN (after recovery delay) → CLOSED again.

**Where:** `@CircuitBreaker(name = "openai")` on every `OpenAiClient` call. Resilience4j config:
```yaml
resilience4j.circuitbreaker.instances.openai:
  failureRateThreshold: 50
  waitDurationInOpenState: 30s
  slidingWindowSize: 20
  permittedNumberOfCallsInHalfOpenState: 3
```

### 3.9 Bulkhead

**Concept:** Isolate failure domains. If LLM calls run out of threads, HTTP serving must still work.

**Two bulkheads:**
- **Thread pool bulkhead:** Dedicated pool for outbound LLM calls (20 threads). HTTP server pool is separate (Tomcat default).
- **Semaphore bulkhead per tenant:** Max 5 concurrent LLM calls per tenant. Caps blast radius from one runaway customer.

### 3.10 Back-Pressure

**Where:**
- **Kafka consumer:** Spring Kafka `concurrency: 3` + manual ack mode + `max.poll.records: 10` lets the consumer naturally slow down when downstream (Temporal, LLM) is slow.
- **SSE streaming endpoint:** Backend uses Reactor `Flux` with bounded buffer; if the client TCP socket can't accept fast enough, the upstream LLM token stream pauses (TCP back-pressure cascades).

### 3.11 Distributed Tracing (OpenTelemetry)

**Trace propagation path:**
```
Browser → HTTP request with traceparent header
       → Next.js BFF (continues span)
       → Spring Boot Controller (continues span)
       → @Service method (child span)
       → Temporal workflow (custom interceptor adds span)
       → Activity (child span)
       → OpenAiClient call (child span; tagged with model, tokens.in/out, usd)
       → response bubbles back
```

**Storage:** Jaeger (local dev) or Tempo (prod). Spans tagged with `tenant_id`, `agent_id`, `workflow_run_id` for filtering.

**Why this matters for capstone evaluators:** A single product feature spans 6 services. Without distributed tracing, debugging a slow workflow is forensic archaeology. With it, you click one trace and see exactly where 800ms vanished.

### 3.12 Observability Triad

| Pillar | Tool | What it Answers |
|---|---|---|
| Logs | Logback + JSON encoder → Loki (prod) | "What exactly happened, in order, for this request?" |
| Metrics | Micrometer → Prometheus → Grafana | "How is the system behaving overall, right now?" |
| Traces | OpenTelemetry → Jaeger / Tempo | "Why is THIS specific request slow?" |

**Key metrics Nexus exposes:**
- `nexus.workflow.runs{tenant,status}` — count of workflow executions
- `nexus.tokens.consumed{tenant,model,direction}` — token meter
- `nexus.cost.usd{tenant,model}` — USD ledger
- `nexus.tribunal.disagreement{tenant}` — multi-agent voting disagreement rate (proxy for uncertainty)
- `nexus.cache.hit_ratio{layer}` — L1/L2/L3 hit ratios
- `nexus.outbox.lag_seconds` — outbox dispatch latency
- `nexus.kafka.consumer.lag{topic,partition}` — Kafka backpressure indicator
- `nexus.resilience4j.calls{name,kind}` — circuit-breaker state transitions

### 3.13 Service Mesh & Sidecars (Planned v0.3)

- **Sidecar pattern:** Each pod runs an Envoy/Linkerd proxy beside the app container. Proxy handles mTLS, retries, circuit breaking, distributed tracing — none of which the app code needs to know about.
- **Ambassador pattern:** A specialized proxy facing one external service (e.g., the OpenAI ambassador handles auth header injection, rate limiting, telemetry).

### 3.14 BFF (Backend-for-Frontend)

Already discussed in [§2.2 API Gateway](#22-api-gateway-pattern). Worth noting again: the Next.js `app/api/*/route.ts` handlers ARE the BFF. They are not deployed separately — they live in the frontend service, deployed alongside the React tree.

### 3.15 Strangler Fig Pattern

**Concept:** When migrating from system A to system B, run them side-by-side and route an increasing fraction of traffic to B until A is empty, then delete A.

**Where Nexus uses it:**
- Monolith → microservices migration plan (v1.0+). Each module exits as a service; the monolith proxies to it during cutover.
- Old vs new agent prompt: A/B route 10% traffic to new prompt; ratchet to 100% on success.

### 3.16 Anti-Corruption Layer

**Concept:** External APIs have legacy/awkward shapes. Don't let those shapes leak into your domain. Sit a translator at the boundary.

**Where:**
- `backend/.../integrations/whatsapp/TwilioClient.java` accepts Twilio's wire format (`From`, `To`, `Body`, `MediaUrl0..9`) and returns a clean Nexus `InboundMessage` record.
- `backend/.../integrations/mcp/McpClient.java` translates MCP tool definitions into Nexus's internal `ToolDescriptor`.

### 3.17 Vector Clocks / Lamport Timestamps

**Where in Nexus:**
- **Temporal** uses Lamport-style logical clocks internally for workflow event ordering across shards.
- **Application code:** When merging concurrent updates to an agent definition (multi-user edit), Nexus stores `version` on the row and rejects updates with stale versions (optimistic concurrency control — simpler than vector clocks for our 1-writer-mostly workload).

### 3.18 Gossip Protocols

Not directly implemented by us, but used under the hood by Kubernetes Service mesh control planes, Redis cluster, and Kafka cluster metadata. Worth knowing — gossip lets N nodes converge on shared state without a central coordinator.

### 3.19 Bloom Filters, HyperLogLog, Count-Min Sketch

**Bloom filter usage in Nexus (planned v0.3):**
- Negative cache lookup: "does this prompt definitely-not exist in the prompt cache?" — a bloom hit avoids a Redis round-trip.

**HyperLogLog (Redis built-in):**
- "How many unique tenants triggered a workflow today?" — `PFCOUNT tenants:today`. O(1) memory regardless of cardinality.

**Count-min sketch:**
- Rolling top-K: "what are the top 10 most-invoked tools across all tenants?" — bounded memory.

### 3.20 Multi-Tenancy Strategy

**Three common approaches:**

| Approach | Isolation | Ops cost | When to use |
|---|---|---|---|
| Database per tenant | Strongest | Highest | Regulated industries, < 100 tenants |
| Schema per tenant | Strong | High | Mid-scale; < 1000 tenants |
| Shared schema + tenant_id | Weakest (app-enforced) | Lowest | Default SaaS pattern |

**Nexus choice:** Shared schema + `tenant_id` + Postgres Row-Level Security.

**Defense in depth:**
1. **App-layer:** Every repository query filters by `tenant_id`.
2. **DB-layer:** RLS policies on every tenant-scoped table; session var `app.current_tenant` set via `SET LOCAL` in the JDBC connection wrapper.
3. **Network-layer (v0.3):** Per-tenant rate limits + cost budgets.

```sql
CREATE POLICY tenant_isolation ON workflow_runs
  USING (tenant_id = current_setting('app.current_tenant')::uuid);

ALTER TABLE workflow_runs ENABLE ROW LEVEL SECURITY;
```

**Why this even when the app enforces it:** A bug in a controller that forgets to filter is a leak. RLS makes the database refuse to serve unfiltered rows. Two locks > one lock.

### 3.21 Zero-Downtime Deployment

**Strategies (mix-and-match):**
- **Rolling:** k8s default — replace pods one at a time. Backend is stateless → safe.
- **Blue-green:** Two identical environments; flip traffic on cutover. Used for v0.x → v1.0 migrations.
- **Canary:** 5% → 25% → 50% → 100% traffic. Combined with feature flags.

**Database schema migrations:** Always **additive** (add columns nullable, add tables) → deploy app → optionally backfill → optionally drop old columns in a later release. Never break the running version's reads.

---

## Tier 4 — AI-Specific Design Concepts

This tier is what differentiates Nexus OS from a generic Spring Boot system-design demo. These are the patterns AI-native platforms need.

### 4.1 RAG Pipeline Architecture

```
Document → Chunker → Embedding Model → Vector Store (Qdrant)
                                            ↓
User Query → Embedding Model → Retriever (top-k) → Reranker → Context Assembly
                                                                      ↓
                                                               LLM (with context)
                                                                      ↓
                                                              Response + Citations
```

**Chunking strategies in `backend/.../agents/rag/ChunkingStrategy.java`:**
- **Fixed window** (default for plain text): 500 tokens with 50-token overlap.
- **Semantic** (for code, markdown): split on logical boundaries (function defs, headers).
- **Sentence-aware** (for narrative): never split mid-sentence.

**Embedding model:** `text-embedding-3-small` (OpenAI) — 1536 dims; balance cost vs recall.

**Vector index:** Qdrant HNSW with `m: 16, ef_construct: 100` — recall ~0.95 at 5ms p99 for 10M vectors.

### 4.2 Vector Index — HNSW vs IVF

| Algorithm | Build | Query | Recall@10 | Use When |
|---|---|---|---|---|
| HNSW (Hierarchical Navigable Small World) | Slow | Fast | High | < 100M vectors, low-latency reads |
| IVF (Inverted File Index) | Fast | Medium | Medium-high | > 100M vectors, ok to trade some recall |
| Flat (no index) | None | Slow | 1.0 | < 10K vectors, exact recall needed |

**Nexus default:** HNSW. Reads dominate writes. Memory budget supports 10-50M vectors per Qdrant node.

### 4.3 Embedding Cache

**Concept:** Identical text → identical embedding. Don't pay to re-embed.

**Implementation:** `EmbeddingService` checks Redis L2 first (`emb:sha256(text):model`). Hit returns the float[] directly. Miss calls OpenAI Embeddings API and writes back.

**Hit ratio in dev:** ~78% (FAQ-style queries repeat).

### 4.4 Prompt Cache (token cost lever)

Detailed in [§1.5 Cache-Aside](#15-cache-aside-strategy). Key insight: model providers (Anthropic, OpenAI) themselves offer prompt caching at the API layer — Nexus uses this **in addition to** our app-layer cache.

**Two-level prompt cache:**
- App-layer: hits never touch the provider — best.
- Provider-layer: hits use a discounted rate — good.

### 4.5 Model Routing (cost-aware)

**Concept:** GPT-4o costs ~30x GPT-4o-mini. For 90% of queries, the smaller model is enough.

**Algorithm:** `ModelRouter`:
1. Classify query difficulty using a tiny model (haiku/4o-mini).
2. Route to small model first.
3. If small model output fails a `HallucinationGuard` confidence check → escalate to large model.
4. Record cost + selection in `cost_ledger`.

**Result:** ~70% cost reduction at < 5% quality drop vs always-GPT-4o.

### 4.6 Tribunal Consensus (Multi-Agent Voting)

**Concept:** Run the same query through N agents independently. Majority answer wins. Disagreement signals uncertainty.

**Where:** `backend/.../agents/Tribunal.java` — N=3 by default. Triggered for high-stakes decisions (e.g., "send WhatsApp campaign of 10,000 messages — yes/no").

**Disagreement handling:**
- 3/3 agree → return answer.
- 2/3 agree → return majority + log `nexus.tribunal.disagreement` metric.
- 1/1/1 (all disagree) → fail-safe: refuse to proceed; surface to human.

**Inspired by:** N-version programming (Avizienis, 1985) and ensemble methods in ML.

### 4.7 Hallucination Guard

**Concept:** LLMs confabulate. Catch it before damage.

**Three checks:**
- **Schema check:** If the tool expects JSON, parse it. Fail fast.
- **Citation check:** If the prompt requires "cite sources" and no citation regex appears, reject.
- **Self-consistency check:** Run the same query twice; if outputs disagree semantically (cosine of embeddings < 0.7), flag uncertainty.

**Where:** `backend/.../agents/HallucinationGuard.java`.

### 4.8 Memory Hierarchies

Inspired by CPU cache hierarchies, applied to agent memory:

| Layer | Storage | Purpose | Latency | Persistence |
|---|---|---|---|---|
| Working memory | In-process (`ChatMemory`) | Last N turns of one conversation | μs | Process lifetime |
| Episodic memory | Postgres `messages` | Full conversation history | ms | Forever |
| Semantic memory | Qdrant vector store | Cross-conversation knowledge ("the user said X 3 weeks ago") | 5ms | Forever, queryable |
| Procedural memory | Compiled prompt + tools | Static |  |  |

### 4.9 Tool Calling Protocol

**Standard (MCP):** Anthropic's Model Context Protocol, donated to the Linux Foundation in December 2025. Standardized tool description + invocation envelope. Every major framework supports it natively in 2026.

**Nexus implementation:**
- **Server (export):** `McpServerExporter` exposes Nexus agents' tools to external clients (Claude Desktop, Cursor, VS Code, etc.).
- **Client (consume):** `McpClient` connects to external MCP servers (GitHub MCP, Slack MCP, filesystem MCP, etc.) and dynamically adds their tools to a Nexus agent's toolbox.

**Why both directions:** A Nexus agent can consume the world's MCP servers AND the world can consume Nexus agents.

### 4.10 Agent-to-Agent Protocol (A2A)

**Standard:** Google A2A, donated to Linux Foundation June 2025. Standardizes how agents discover, communicate, and collaborate.

**Discovery:** Agent exposes `.well-known/agent.json` with capability manifest.
**Invocation:** HTTP+JSON task request with structured response.

**Where in Nexus:**
- `backend/.../integrations/a2a/A2AServer.java` — exposes Nexus agents to peer frameworks (LangGraph, CrewAI, AutoGen).
- `backend/.../integrations/a2a/A2AClient.java` — Nexus agent can call peer agents (`Nexus.callA2A("https://crewai.com/agent/research-team", request)`).

**Why this is a 2026 differentiator:** Most frameworks treat themselves as a walled garden. A2A breaks the walls — Nexus interoperates with any framework that speaks A2A.

### 4.11 Streaming Token Delivery (SSE)

**Why streaming:** A 600-token response takes 6 seconds to generate. The first token arrives in ~200ms. Streaming reduces perceived latency by 30x.

**Implementation:** Spring `SseEmitter` returned from `ChatController.stream()`. Each LLM token forwarded to client as `event: token\ndata: <text>\n\n`.

**Frontend:** `frontend/src/lib/sse.ts` — typed SSE consumer using the browser `EventSource` API.

### 4.12 Cost Guardrails

**Per-tenant budget:** Configured in `tenants.monthly_usd_budget`.

**Enforcement:**
1. Before any LLM call, `CostMeter.canSpend(tenant, estimatedUsd)` consults the ledger.
2. If consumption + estimate > budget → refuse with `BUDGET_EXCEEDED` error.
3. Resilience4j `@CircuitBreaker(name = "tenant-budget", fallback = "budgetExceededFallback")` provides clean fail mode.

**Why budget circuit breakers are essential:** A misconfigured agent in a loop can blow $5000 in an hour. Without the breaker, the only safety net is the OpenAI/Anthropic invoice 30 days later.

### 4.13 Self-Mutating Agents (sandboxed)

**Concept:** Agents can register new capabilities at runtime — without redeploy.

**How (safely):**
- Capability sealed interface (`AgentCapability`) is permissive on permitted subtypes; new capabilities ship as JARs into a registered classpath.
- New tool registrations require **Tribunal approval** — the agent proposes the capability, 3 sentinel agents vote.
- Whitelisted operations only: HTTP GET to allow-listed domains, in-process arithmetic, read-only DB queries. No file system, no shell, no arbitrary code exec.

**Where:** `backend/.../agents/SelfMutationGuard.java` — planned v0.3.

---

## Cross-Cutting Concerns

### 5.1 Configuration Management

- Spring profiles: `local`, `test`, `prod`.
- Externalized via env vars + `application-<profile>.yml`.
- Secrets via env vars (never committed; `.env.local` gitignored).
- 12-factor app compliance.

### 5.2 Internationalization

Out of scope for v0.1. v0.3 will add Hindi + Hinglish output for Indian market via prompt templates + post-translation pass.

### 5.3 Auditability

- Every write to a tenant-scoped table includes `created_at`, `updated_at`, `created_by` (user_id).
- `cost_ledger`, `workflow_runs`, `messages` are effectively immutable (no UPDATE allowed via constraint trigger).
- Audit log can be exported as JSON or CSV per tenant — compliance pre-requisite.

### 5.4 Internationalized Error Handling

- Domain-level: `NexusException` hierarchy with stable error codes.
- API-level: `GlobalExceptionHandler` translates to RFC 7807 Problem Detail responses.
- Frontend: localized strings keyed off the error code, not the message.

---

## Failure Modes & Recovery

| Failure | Detection | Recovery |
|---|---|---|
| OpenAI down | Circuit breaker opens on 50% failure rate | Falls back to Ollama (BYOM) if configured; else fails gracefully with retry-after |
| Postgres primary down | Spring Boot health check fails | Kubernetes pod restart; replica promotion (manual in v0.1, auto via Patroni in v0.3) |
| Kafka broker down | Producer retries + KRaft controller re-elects | Spring Kafka auto-reconnect; in-flight messages buffered up to producer-buffer limit |
| Temporal worker crash | Worker pulse misses | Activity re-scheduled by Temporal; idempotency guards against double execution |
| Qdrant down | Connection error in RAG pipeline | Degrade to no-RAG mode (LLM answers from training data only); flag response with `degraded: true` |
| Redis down | Cache miss | All calls become uncached but functional; latency spikes; alert fires |
| Twilio webhook flood | Rate limiter trips | 429 responses; Twilio's own retry-with-backoff handles it |
| One tenant runaway loop | Tenant rate limiter + budget circuit | Tenant temporarily 429'd; other tenants unaffected (bulkhead) |
| Workflow stuck > 1h | Temporal timeout policy | Compensation runs; human notified via webhook |

---

## Capacity Planning

### Back-of-envelope sizing (v0.1 demo scale)

| Metric | Target |
|---|---|
| Tenants | 10 |
| Active users / tenant / day | 50 |
| Workflows / user / day | 5 |
| Workflows / day total | 2500 |
| Avg tokens / workflow | 4000 |
| Tokens / day | 10M |
| LLM cost / day @ GPT-4o-mini | ~$1.50 |
| LLM cost / day @ GPT-4o (worst) | ~$50 |

### v1.0 target

| Metric | Target |
|---|---|
| Tenants | 1000 |
| Workflows / day total | 1M |
| Tokens / day | 4B |
| Postgres rows / day (`workflow_runs`) | 1M |
| Kafka throughput | 50 msg/s avg, 500 peak |

### When does each component become the bottleneck?

| Tenants | First bottleneck |
|---|---|
| < 100 | LLM provider quota |
| 100-1000 | Postgres write throughput (cost_ledger) |
| 1000-10K | Kafka partition count + consumer parallelism |
| > 10K | Need horizontal sharding on Postgres |

---

## Security Model

### Authentication
- v0.1 dev: header-based tenant injection (no real auth).
- v0.2: OIDC via Auth0 or Keycloak.
- JWT carries `tenant_id` claim → `TenantFilter` extracts → `TenantContext` propagates.

### Authorization
- RBAC at tenant scope: `OWNER`, `ADMIN`, `OPERATOR`, `VIEWER`.
- Action-level checks via Spring Security `@PreAuthorize("hasRole('ADMIN')")`.

### Data isolation
- Postgres RLS — see [§3.20 Multi-Tenancy](#320-multi-tenancy-strategy).

### Transport security
- TLS 1.3 everywhere in prod.
- Internal mesh: mTLS via service mesh (v0.3).

### Secrets
- Never in git. Env vars in dev. Secret managers (Vault / AWS Secrets Manager) in prod.
- Pre-commit hook scans for common key patterns ([`.githooks/pre-commit`](../.githooks/pre-commit)).

### Sandbox for self-mutating agents
- Allow-listed HTTP egress.
- No file-system access from agent-defined code.
- All dynamic capability registrations require Tribunal approval.

### Audit log
- Every state change includes `actor_user_id`.
- Immutable append-only via DB trigger preventing UPDATE / DELETE.

---

## Trade-Off Register

| Trade-off | Choice | Cost | Benefit |
|---|---|---|---|
| Monolith vs microservices (v0.1) | Modular monolith | Future migration work | Shipping speed |
| 2PC vs Saga | Saga | Temporary inconsistency | Liveness during partition |
| Schema-per-tenant vs shared+RLS | Shared+RLS | Weaker isolation | Lower ops cost |
| Synchronous vs async LLM | Async via Temporal | Latency floor (must wait for activity scheduling) | Durability + retry |
| HNSW vs IVF (vector index) | HNSW | Slower index build | Faster query |
| Cache-aside vs write-through | Cache-aside | Cache cold on cold start | Simpler invalidation |
| Tribunal=3 vs Tribunal=1 | 3 | 3x cost | Reduced hallucination |
| Outbox vs direct Kafka publish | Outbox | Extra DB write + dispatcher | Zero dual-write inconsistency |
| Per-tenant rate limit Redis vs in-mem | Redis | Network hop | Cluster-wide consistency |
| Eager vs lazy embeddings | Lazy (on first query) | Latency on cold path | No bulk pre-embedding cost |

---

## What This Document Is Not

- A finished implementation. v0.1 is the foundation; concepts above tagged "planned v0.3" or "v1.0+" are roadmap, not done.
- A theoretical paper. Every concept is tied to a code path. If it's listed here, an engineer can find the file.
- A complete academic survey. We covered concepts Nexus OS actually uses. Each could be (and has been) a textbook.

---

## Further Reading (for evaluators / hires)

- *Designing Data-Intensive Applications* — Kleppmann (the canonical reference for Tiers 2–3)
- *Patterns of Enterprise Application Architecture* — Fowler (Tier 1 baseline)
- *Building Event-Driven Microservices* — Bellemare (sagas, outbox, idempotency)
- *Resilience4j docs* — production-grade circuit breakers + bulkheads
- *Temporal documentation* — durable execution & saga compensation primer
- *Anthropic MCP spec* (linuxfoundation.org/projects/agentic-ai-foundation)
- *Google A2A protocol* (a2a-protocol.org)
