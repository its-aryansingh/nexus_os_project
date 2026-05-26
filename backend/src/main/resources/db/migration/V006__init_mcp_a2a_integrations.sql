-- ════════════════════════════════════════════════════════════════════════════════
-- V006 — MCP Servers + A2A Endpoints + Channel Integrations
-- See: docs/ARCHITECTURE.md §9.3 (MCP), §9.4 (A2A); docs/ADRS/0005-mcp-and-a2a-from-day-1.md
-- ════════════════════════════════════════════════════════════════════════════════

-- ── mcp_servers (external MCP servers configured per tenant) ───────────────────
CREATE TABLE IF NOT EXISTS mcp_servers (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                    VARCHAR(255)    NOT NULL,
    base_url                VARCHAR(1024)   NOT NULL,
    transport               VARCHAR(32)     NOT NULL DEFAULT 'sse',    -- sse | http | stdio
    auth_type               VARCHAR(32),                                -- none | bearer | basic | oauth2
    auth_secret_ref         VARCHAR(255),                               -- reference to secret store
    allowed_tools           JSONB           NOT NULL DEFAULT '[]'::jsonb,   -- allowlist of tools to import
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    last_handshake_at       TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT mcp_servers_name_per_tenant UNIQUE (tenant_id, name),
    CONSTRAINT mcp_servers_transport_chk CHECK (transport IN ('sse', 'http', 'stdio'))
);

CREATE INDEX IF NOT EXISTS idx_mcp_servers_tenant ON mcp_servers (tenant_id);

-- ── a2a_endpoints (A2A peer endpoints configured per tenant) ───────────────────
CREATE TABLE IF NOT EXISTS a2a_endpoints (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                    VARCHAR(255)    NOT NULL,
    well_known_url          VARCHAR(1024)   NOT NULL,                   -- e.g. https://peer.example/.well-known/agent.json
    agent_card              JSONB,                                      -- cached agent.json contents
    auth_type               VARCHAR(32),
    auth_secret_ref         VARCHAR(255),
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    last_handshake_at       TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT a2a_endpoints_name_per_tenant UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_a2a_endpoints_tenant ON a2a_endpoints (tenant_id);

-- ── channel_subscriptions (which channels deliver to which agents) ─────────────
CREATE TABLE IF NOT EXISTS channel_subscriptions (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    channel                 VARCHAR(32)     NOT NULL,           -- whatsapp | web | slack | discord | email
    channel_address         VARCHAR(255),                       -- e.g. +14155238886 for WhatsApp
    agent_id                UUID            REFERENCES agents(id) ON DELETE SET NULL,
    workflow_id             UUID            REFERENCES workflows(id) ON DELETE SET NULL,
    config                  JSONB           NOT NULL DEFAULT '{}'::jsonb,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT channel_subs_unique UNIQUE (tenant_id, channel, channel_address)
);

CREATE INDEX IF NOT EXISTS idx_channel_subs_lookup ON channel_subscriptions (channel, channel_address) WHERE is_active;

-- ── apply RLS to v006 tables ───────────────────────────────────────────────────
ALTER TABLE mcp_servers ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mcp_servers;
CREATE POLICY tenant_isolation ON mcp_servers
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

ALTER TABLE a2a_endpoints ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON a2a_endpoints;
CREATE POLICY tenant_isolation ON a2a_endpoints
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

ALTER TABLE channel_subscriptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON channel_subscriptions;
CREATE POLICY tenant_isolation ON channel_subscriptions
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());
