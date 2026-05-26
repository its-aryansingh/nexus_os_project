-- ════════════════════════════════════════════════════════════════════════════════
-- V005 — Row-Level Security policies (defense-in-depth multi-tenancy)
-- See: docs/SYSTEM_DESIGN.md §3.20; docs/ADRS/0006-rls-multi-tenancy.md
--
-- Strategy: every tenant-scoped table enables RLS. Policies USING the session
-- var 'app.current_tenant'. The TenantFilter sets this var via
-- `SET LOCAL app.current_tenant = '<uuid>'` at the start of each request tx.
--
-- The admin/migration role uses BYPASSRLS for full visibility (configured at
-- the role level, not in this migration).
-- ════════════════════════════════════════════════════════════════════════════════

-- ── helper: extract current tenant uuid; null-safe ─────────────────────────────
CREATE OR REPLACE FUNCTION current_tenant() RETURNS UUID AS $$
DECLARE
    raw TEXT;
BEGIN
    raw := current_setting('app.current_tenant', true);
    IF raw IS NULL OR raw = '' THEN
        RETURN NULL;
    END IF;
    RETURN raw::UUID;
EXCEPTION WHEN invalid_text_representation THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── users ──────────────────────────────────────────────────────────────────────
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── agents ─────────────────────────────────────────────────────────────────────
ALTER TABLE agents ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON agents;
CREATE POLICY tenant_isolation ON agents
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── workflows ──────────────────────────────────────────────────────────────────
ALTER TABLE workflows ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflows;
CREATE POLICY tenant_isolation ON workflows
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── workflow_runs ──────────────────────────────────────────────────────────────
ALTER TABLE workflow_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_runs;
CREATE POLICY tenant_isolation ON workflow_runs
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── activity_runs ──────────────────────────────────────────────────────────────
ALTER TABLE activity_runs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON activity_runs;
CREATE POLICY tenant_isolation ON activity_runs
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── tool_calls ─────────────────────────────────────────────────────────────────
ALTER TABLE tool_calls ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tool_calls;
CREATE POLICY tenant_isolation ON tool_calls
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── outbox ─────────────────────────────────────────────────────────────────────
ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON outbox;
CREATE POLICY tenant_isolation ON outbox
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── inbox ──────────────────────────────────────────────────────────────────────
ALTER TABLE inbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON inbox;
CREATE POLICY tenant_isolation ON inbox
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── cost_ledger ────────────────────────────────────────────────────────────────
ALTER TABLE cost_ledger ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON cost_ledger;
CREATE POLICY tenant_isolation ON cost_ledger
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── messages ───────────────────────────────────────────────────────────────────
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON messages;
CREATE POLICY tenant_isolation ON messages
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- ── memory_chunks ──────────────────────────────────────────────────────────────
ALTER TABLE memory_chunks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON memory_chunks;
CREATE POLICY tenant_isolation ON memory_chunks
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

-- NOTE: tenants table itself is NOT under RLS — admins query across tenants.
-- The application's API surface enforces that operators only see their own tenant.
