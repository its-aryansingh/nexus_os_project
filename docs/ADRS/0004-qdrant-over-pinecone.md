# ADR 0004 — Qdrant Over Pinecone (and Weaviate, Milvus)

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect), AI Specialist

## Context

Nexus OS needs a vector database for RAG. Options in 2026:

| DB | Hosted | Self-hostable | Java SDK | Multi-tenant | HNSW |
|---|---|---|---|---|---|
| Qdrant | ✅ | ✅ | ✅ | ✅ (collections) | ✅ |
| Pinecone | ✅ | ❌ | ✅ | ✅ (namespaces) | ✅ |
| Weaviate | ✅ | ✅ | ✅ | ✅ | ✅ |
| Milvus | ✅ | ✅ | ✅ | partial | ✅ |
| pgvector | n/a | ✅ (part of PG) | n/a | ✅ | ❌ (IVF) |

## Decision

Use **Qdrant v1.9+** as the vector store. Single Qdrant cluster shared across tenants, with one collection per tenant (`tenant_<id>`).

## Consequences

### Positive
- **Self-hostable** — Pinecone is SaaS-only; we need on-prem-deployable for enterprises.
- **HNSW with tunable parameters** — recall vs. latency tradeoff under our control.
- **gRPC API** — lower overhead than REST for hot-path retrieval.
- **Collection-per-tenant** — clean isolation; cheap to create + delete.
- **Hybrid search** (dense + sparse) — useful for keyword+semantic mixed queries.

### Negative / Trade-offs
- **Less polished than Pinecone** — Pinecone SaaS has slicker UX. Mitigation: we don't need the UX (it's an internal service).
- **One more stateful service** to operate. Mitigation: docker-compose ships it; backup via snapshot to S3 in prod.
- **No native LLM integration UI** (unlike Weaviate's modules). N/A — we compose at the LangChain4j layer.

### Why not pgvector?
- pgvector uses IVF by default (HNSW since pg-vector 0.5 but with caveats). For 10M+ vectors with sub-10ms p99 query latency, Qdrant outperforms.
- Keeping vector ops out of the primary Postgres avoids contention with transactional traffic.
- That said: pgvector is a viable v0.3+ "small tenant" tier — we may add it as a backend choice for tenants with < 100K vectors.

## References
- `backend/src/main/java/com/nexus/os/agents/rag/QdrantStore.java`
- `docs/SYSTEM_DESIGN.md` §4.2 (HNSW vs IVF)
- Qdrant docs (qdrant.tech)
