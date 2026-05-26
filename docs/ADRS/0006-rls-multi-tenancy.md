# ADR 0006 — Shared-Schema Multi-Tenancy with Postgres RLS

- **Status:** Accepted
- **Date:** 2026-05-26
- **Deciders:** Aryan Singh (Lead Architect)

## Context

Nexus OS is multi-tenant by construction. Three common approaches exist:

| Approach | Isolation | Ops cost | Cost-per-tenant |
|---|---|---|---|
| **Database per tenant** | Strongest | Highest | Highest |
| **Schema per tenant** | Strong | High | Medium |
| **Shared schema + `tenant_id` column** | Weakest (app-enforced) | Lowest | Lowest |

App-enforced isolation has a known weakness: a controller that forgets to filter leaks rows. Defense in depth solves this.

## Decision

Use **shared schema + `tenant_id` column** as the base, **plus Postgres Row-Level Security (RLS) policies** as defense in depth.

Implementation:
1. Every tenant-scoped table has a `tenant_id UUID NOT NULL` column with FK to `tenants`.
2. Every tenant-scoped table has an RLS policy: `USING (tenant_id = current_setting('app.current_tenant')::uuid)`.
3. JDBC connection wrapper runs `SET LOCAL app.current_tenant = ?` at the start of every request transaction, sourced from `TenantContext`.
4. Service-role bypass: a separate JDBC datasource for migrations / admin ops that explicitly sets `app.current_tenant = '00000000-...'` and uses a Postgres role with `BYPASSRLS`.

## Consequences

### Positive
- **Cheapest ops** — single DB, single schema, single migration set.
- **Defense in depth** — even if a service-layer filter is missed, RLS refuses to return cross-tenant rows.
- **Onboarding is fast** — new tenant = insert one row into `tenants`. No DB / schema provisioning.
- **Multi-tenant aggregations are easy** — admin can query across all tenants by using the admin datasource.

### Negative / Trade-offs
- **Per-tenant backup is harder** than schema-per-tenant — must export filtered. We accept this.
- **Per-tenant data residency is harder** — if a customer demands EU-only data, we need a separate Nexus deployment per region. Acceptable in v1.x.
- **Index sizes grow per-tenant** — every index is sorted by `(tenant_id, ...)`. We use composite indexes to mitigate.
- **A bug in RLS policy is catastrophic** — extensive integration testing required.

### Alternative rejected
- **Schema-per-tenant**: high ops cost for our scale; migration tooling complexity. Reconsider at > 1000 tenants.
- **Database-per-tenant**: only justified for regulated industries (banking, healthcare). Not v0.x target.

## Integration Testing

Codex MUST write integration tests that assert tenant A cannot see tenant B's data. Both the service-layer test (with TenantContext) AND a direct-SQL test (with `SET LOCAL app.current_tenant`) must pass.

## References
- `backend/src/main/java/com/nexus/os/tenancy/**`
- `backend/src/main/resources/db/migration/V005__init_rls_policies.sql` (planned)
- `docs/SYSTEM_DESIGN.md` §3.20 (Multi-Tenancy Strategy)
