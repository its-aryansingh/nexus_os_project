# ADR 0003 — Kafka KRaft Over Zookeeper-Backed Kafka

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect), DevOps Lead

## Context

Kafka 3.x supports two modes:
1. **Zookeeper-backed** (legacy, deprecated as of Kafka 3.5)
2. **KRaft** (Kafka Raft Metadata mode — Kafka's own consensus, no Zookeeper)

Nexus OS uses Kafka as the async backbone (inbound messages, agent events, outbound responses).

## Decision

Use **Kafka 3.7.0 in KRaft mode** for all deployments (dev and prod).

## Consequences

### Positive
- **One less stateful service** — no Zookeeper to operate, monitor, or upgrade.
- **Faster controller failover** — KRaft Raft consensus is more responsive than ZK ephemeral nodes.
- **Modern default** — Zookeeper-backed Kafka is being removed in Kafka 4.x; KRaft is future-proof.
- **Simpler docker-compose** — one container instead of two; ports cleaner.

### Negative / Trade-offs
- **Less battle-tested** at extreme scale (10K+ brokers) than ZK-mode — irrelevant for Nexus OS (we target tens of brokers max).
- **Migration tooling immature** for ZK → KRaft of pre-existing clusters — N/A for greenfield Nexus.

### Alternatives rejected
- **Zookeeper-backed Kafka:** deprecated; one more stateful service to operate; no benefit at our scale.
- **RabbitMQ / NATS / Redpanda:** see [`docs/ARCHITECTURE.md` §8.4](../ARCHITECTURE.md) for Kafka selection rationale.

## References
- `docker-compose.yml` (KRaft env vars: `KAFKA_PROCESS_ROLES=broker,controller`, `KAFKA_CONTROLLER_QUORUM_VOTERS`)
- Kafka KRaft GA blog (Apache Kafka 3.3)
