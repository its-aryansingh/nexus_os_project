-- ════════════════════════════════════════════════════════════════════════════════
-- V004 — Cost Ledger + Messages + Memory Chunks
-- See: docs/SYSTEM_DESIGN.md §4.12 Cost Guardrails; §4.8 Memory Hierarchies
-- ════════════════════════════════════════════════════════════════════════════════

-- ── cost_ledger (append-only) ──────────────────────────────────────────────────
-- One row per LLM invocation (or any meterable external call). Sum by tenant
-- gives spend; sum by tenant + model gives per-model breakdown.
CREATE TABLE IF NOT EXISTS cost_ledger (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    workflow_run_id         UUID            REFERENCES workflow_runs(id) ON DELETE SET NULL,
    agent_id                UUID            REFERENCES agents(id) ON DELETE SET NULL,
    provider                VARCHAR(64)     NOT NULL,        -- openai | anthropic | ollama | twilio
    model                   VARCHAR(64)     NOT NULL,        -- gpt-4o-mini, gpt-4o, claude-3-haiku, llama3, etc.
    operation               VARCHAR(32)     NOT NULL,        -- chat | embed | whisper | send_sms | etc.
    tokens_in               INTEGER         NOT NULL DEFAULT 0,
    tokens_out              INTEGER         NOT NULL DEFAULT 0,
    units                   INTEGER         NOT NULL DEFAULT 0,  -- for non-token providers (e.g. WhatsApp messages)
    usd                     NUMERIC(12, 6)  NOT NULL DEFAULT 0,
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Per-tenant rollups by time bucket — supports the cost dashboard.
CREATE INDEX IF NOT EXISTS idx_cost_ledger_tenant_time ON cost_ledger (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_ledger_model ON cost_ledger (tenant_id, model, occurred_at DESC);

-- Forbid UPDATE/DELETE — cost ledger is append-only.
CREATE OR REPLACE FUNCTION reject_modify_cost_ledger() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'cost_ledger is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS cost_ledger_no_modify ON cost_ledger;
CREATE TRIGGER cost_ledger_no_modify
    BEFORE UPDATE OR DELETE ON cost_ledger
    FOR EACH ROW EXECUTE FUNCTION reject_modify_cost_ledger();

-- ── messages (multi-channel inbound + outbound) ────────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    direction               VARCHAR(16)     NOT NULL,        -- inbound | outbound
    channel                 VARCHAR(32)     NOT NULL,        -- whatsapp | web | slack | discord | email | api
    external_id             VARCHAR(255),                    -- Twilio SID, Slack ts, etc.
    from_party              VARCHAR(255),                    -- phone, slack user id, email
    to_party                VARCHAR(255),
    body                    TEXT,
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    workflow_run_id         UUID            REFERENCES workflow_runs(id) ON DELETE SET NULL,
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT messages_direction_chk CHECK (direction IN ('inbound','outbound'))
);

CREATE INDEX IF NOT EXISTS idx_messages_tenant_time ON messages (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages (tenant_id, channel, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_external ON messages (external_id) WHERE external_id IS NOT NULL;

-- Full-text search over message bodies (Section 1.3 indexing)
CREATE INDEX IF NOT EXISTS idx_messages_body_fts
    ON messages USING gin (to_tsvector('english', coalesce(body, '')));

-- ── memory_chunks (Postgres-side metadata; vectors live in Qdrant) ─────────────
CREATE TABLE IF NOT EXISTS memory_chunks (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    agent_id                UUID            REFERENCES agents(id) ON DELETE SET NULL,
    source_uri              VARCHAR(1024),
    source_type             VARCHAR(64),                     -- doc | url | message | code
    chunk_index             INTEGER         NOT NULL,
    text                    TEXT            NOT NULL,
    token_count             INTEGER,
    qdrant_point_id         VARCHAR(64),                     -- 1:1 with Qdrant point
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_chunks_tenant ON memory_chunks (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_chunks_source ON memory_chunks (tenant_id, source_uri);
