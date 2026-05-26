-- ════════════════════════════════════════════════════════════════════════════════
-- V007 — FORCE Row-Level Security on every tenant-scoped table.
--
-- Why: by default, table OWNERS bypass their own RLS policies. The Spring
-- Boot app connects as the table owner (created by Flyway), so without
-- FORCE the RLS policies installed in V005/V006 are inert from the app's
-- point of view — a misconfigured query that forgets tenant filtering
-- would still leak rows.
--
-- FORCE flips that — the policy applies even to the owner. The migration
-- runner (Flyway) is still subject to RLS for DML, but DDL is unaffected
-- so future migrations work normally.
--
-- For admin/cross-tenant queries (analytics, support) we run a separate
-- datasource whose role has BYPASSRLS — that's the standard escape hatch
-- and is documented in docs/SYSTEM_DESIGN.md §3.20.
-- ════════════════════════════════════════════════════════════════════════════════

ALTER TABLE users                  FORCE ROW LEVEL SECURITY;
ALTER TABLE agents                 FORCE ROW LEVEL SECURITY;
ALTER TABLE workflows              FORCE ROW LEVEL SECURITY;
ALTER TABLE workflow_runs          FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_runs          FORCE ROW LEVEL SECURITY;
ALTER TABLE tool_calls             FORCE ROW LEVEL SECURITY;
ALTER TABLE outbox                 FORCE ROW LEVEL SECURITY;
ALTER TABLE inbox                  FORCE ROW LEVEL SECURITY;
ALTER TABLE cost_ledger            FORCE ROW LEVEL SECURITY;
ALTER TABLE messages               FORCE ROW LEVEL SECURITY;
ALTER TABLE memory_chunks          FORCE ROW LEVEL SECURITY;
ALTER TABLE mcp_servers            FORCE ROW LEVEL SECURITY;
ALTER TABLE a2a_endpoints          FORCE ROW LEVEL SECURITY;
ALTER TABLE channel_subscriptions  FORCE ROW LEVEL SECURITY;
