# ADR 0007 — Saga Pattern Over 2PC for Multi-Step Workflows

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect)

## Context

Nexus OS workflows span multiple services and external APIs:
- LLM call (OpenAI / Anthropic / Ollama)
- Vector store read (Qdrant)
- Postgres writes (cost_ledger, workflow_runs)
- Kafka publishes (outbound responses)
- WhatsApp send (Twilio)

These are not within a single ACID transaction boundary. The classical options:

1. **Two-Phase Commit (2PC):** Coordinator prepares all participants; commits or aborts atomically.
2. **Three-Phase Commit (3PC):** Adds a pre-commit phase; reduces blocking but still synchronous.
3. **Saga Pattern:** Sequence of local transactions; compensating transactions on failure.

## Decision

Use the **Saga pattern**, implemented via Temporal workflows with compensation activities. **No 2PC anywhere.**

## Consequences

### Positive
- **Non-blocking** — each activity is a local transaction that releases its lock immediately.
- **No global coordinator failure mode** — Temporal as a workflow scheduler is not a 2PC coordinator.
- **Works with non-XA participants** — OpenAI, Twilio, Qdrant don't support XA. 2PC was impossible anyway.
- **Compensation is explicit and testable** — every activity ships with its compensating activity in `activities/compensations/`.
- **Native to Temporal** — try/catch in workflow code; Temporal guarantees compensation runs even after workflow worker crash.

### Negative / Trade-offs
- **Temporary inconsistency** — between Activity 2 and its compensation, the system is in an intermediate state. Mitigation: design activities idempotent; document expected intermediate states.
- **Compensation activity required for every side-effecting step** — engineering discipline needed.
- **Reads during a saga may see stale data** — readers tolerate eventual consistency or explicitly read-after-write through the primary.

### Saga design rules (enforced via review)
1. **Every activity that mutates external state ships with a compensating activity.**
2. **All activities are idempotent** — Temporal may retry; compensations may also be retried.
3. **Compensations run in reverse order** — undo last-in, first-out.
4. **Compensations cannot fail** — if a compensation activity throws non-retryable, escalate to a human via alerting.
5. **No nested sagas** unless the parent owns the child's compensation logic.

### Alternatives rejected
- **2PC:** Blocks resources; can't include non-XA participants; coordinator failure is fatal.
- **3PC:** Reduces blocking but still synchronous; still needs XA. Doesn't help us.
- **Eventually-consistent retry-forever:** Fine for some flows (cost ledger writes) but unsafe for billing-relevant workflows where partial completion = lost money.

## References
- `backend/src/main/java/com/nexus/os/temporal/workflows/**`
- `backend/src/main/java/com/nexus/os/temporal/activities/compensations/**`
- `docs/SYSTEM_DESIGN.md` §2.5 (Saga), §3.4 (vs 2PC)
- Garcia-Molina & Salem, "Sagas" (1987) — the canonical reference
