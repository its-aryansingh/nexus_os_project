-- ════════════════════════════════════════════════════════════════════════════════
-- V008 — Chat sessions + chat messages.
-- Operators want multi-turn conversations with history; per-message rows
-- let us replay any session into Temporal as a "what would the agent
-- say if I changed step N?" workflow (v0.3 time-travel UX).
-- ════════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS chat_sessions (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id                 UUID            REFERENCES users(id) ON DELETE SET NULL,
    title                   VARCHAR(255),
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_message_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_tenant_last
    ON chat_sessions (tenant_id, last_message_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    session_id              UUID            NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role                    VARCHAR(16)     NOT NULL,        -- user | agent | system | tool
    content                 TEXT            NOT NULL,
    workflow_run_id         UUID            REFERENCES workflow_runs(id) ON DELETE SET NULL,
    specialist              VARCHAR(64),                      -- which specialist generated this turn
    model                   VARCHAR(64),
    tokens_in               INTEGER         NOT NULL DEFAULT 0,
    tokens_out              INTEGER         NOT NULL DEFAULT 0,
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_messages_role_chk CHECK (role IN ('user', 'agent', 'system', 'tool'))
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session
    ON chat_messages (session_id, occurred_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_tenant_time
    ON chat_messages (tenant_id, occurred_at DESC);

-- RLS (both tables)
ALTER TABLE chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_sessions FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON chat_sessions;
CREATE POLICY tenant_isolation ON chat_sessions
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON chat_messages;
CREATE POLICY tenant_isolation ON chat_messages
    USING (tenant_id = current_tenant())
    WITH CHECK (tenant_id = current_tenant());
