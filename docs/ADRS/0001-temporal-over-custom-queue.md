# ADR 0001 — Use Temporal for Orchestration (Not a Custom Retry Queue)

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect)

## Context

Nexus OS orchestrates multi-step workflows that:
- Run for seconds to hours (LLM calls, external APIs, human waits)
- Must survive node crashes, network blips, and provider outages
- Must compose Saga-style compensating actions on failure
- Need time-travel replay for debugging
- Must scale fan-out across many concurrent workflows per tenant

Options considered:
1. **Custom retry queue** (Postgres + cron + manual retry logic)
2. **AWS Step Functions** (managed state machine)
3. **Camunda BPMN** (workflow engine)
4. **Temporal** (durable execution platform)

## Decision

Use **Temporal 1.24+** as the orchestration substrate.

## Consequences

### Positive
- **Durable execution** is free: a workflow that's halfway done survives JVM restarts and resumes from the last completed activity.
- **Saga compensation** is first-class via try/catch in workflow code.
- **Time-travel replay** lets us debug a workflow that ran 6 hours ago step-by-step.
- **Self-hostable** — runs in our Docker Compose and our k8s; no AWS lock-in (unlike Step Functions).
- **Code-first** — workflow definitions are testable Java methods, not YAML / BPMN XML.
- **Java SDK** is mature.

### Negative / Trade-offs
- **Operational complexity** — Temporal needs Postgres backing store, controllers, workers; +1 stateful service.
- **Workflow code is restricted** — must be deterministic (no `LocalDateTime.now()`, no random; use `Workflow.now()`, `Workflow.newRandom()` instead). Requires team discipline.
- **Activity boundary mental model** — engineers need to understand "side effects only in activities".

### Alternatives rejected
- **Step Functions:** AWS lock-in. We must be multi-cloud (self-hostable for enterprises).
- **Camunda:** BPMN XML is a step backwards from code-first Java workflows. Java SDK weaker than Temporal's.
- **Custom queue:** Reinventing what Temporal does in 100x more code. Capstone wants depth, not reinvention.

## References
- `backend/src/main/java/com/nexus/os/temporal/**`
- `docs/ARCHITECTURE.md` §3 (request flow)
- `docs/SYSTEM_DESIGN.md` §3.4 (Saga vs 2PC)
