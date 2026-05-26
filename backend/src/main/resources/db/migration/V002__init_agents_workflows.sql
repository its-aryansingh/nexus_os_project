-- ════════════════════════════════════════════════════════════════════════════════
-- V002 — Agents + Workflows + Workflow Runs + Activity Runs + Tool Calls
-- See: docs/ARCHITECTURE.md §6 (Module Boundaries) — domain/ owns these tables.
-- ════════════════════════════════════════════════════════════════════════════════

-- ── agents ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agents (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    slug                    VARCHAR(64)     NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    description             TEXT,
    persona                 TEXT,                                       -- system prompt template
    capabilities            JSONB           NOT NULL DEFAULT '[]'::jsonb,
                                            -- e.g. [{"type":"TextGeneration","modelId":"gpt-4o-mini","temperature":0.7}]
    tools                   JSONB           NOT NULL DEFAULT '[]'::jsonb,
                                            -- e.g. ["search_memory","gh_search"]
    model_preference        VARCHAR(64)     NOT NULL DEFAULT 'gpt-4o-mini',
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT agents_slug_per_tenant UNIQUE (tenant_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_agents_tenant ON agents (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agents_active ON agents (tenant_id, is_active);

-- ── workflows ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflows (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    slug                    VARCHAR(64)     NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    description             TEXT,
    definition              JSONB           NOT NULL DEFAULT '{}'::jsonb,
                                            -- React Flow graph: { nodes:[], edges:[] }
    version                 INTEGER         NOT NULL DEFAULT 1,
    temporal_workflow_type  VARCHAR(255)    NOT NULL DEFAULT 'AgentOrchestrationWorkflow',
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT workflows_slug_per_tenant UNIQUE (tenant_id, slug, version)
);

CREATE INDEX IF NOT EXISTS idx_workflows_tenant ON workflows (tenant_id);
CREATE INDEX IF NOT EXISTS idx_workflows_active ON workflows (tenant_id, is_active);

-- ── workflow_runs ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflow_runs (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    workflow_id             UUID            NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    temporal_workflow_id    VARCHAR(255)    NOT NULL,
    temporal_run_id         VARCHAR(255)    NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'running',
                                            -- running | completed | failed | compensated | timed_out
    input_payload           JSONB,
    output_payload          JSONB,
    error                   TEXT,
    started_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    finished_at             TIMESTAMPTZ,
    duration_ms             INTEGER,
    CONSTRAINT workflow_runs_status_chk CHECK (status IN ('running','completed','failed','compensated','timed_out'))
);

-- "recent runs" page lookup (Section 1.3 indexing)
CREATE INDEX IF NOT EXISTS idx_workflow_runs_tenant_started ON workflow_runs (tenant_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_runs_workflow ON workflow_runs (workflow_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_runs_status ON workflow_runs (tenant_id, status);

-- ── activity_runs ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS activity_runs (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    workflow_run_id         UUID            NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
    activity_type           VARCHAR(255)    NOT NULL,
    step_index              INTEGER         NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'running',
                                            -- running | completed | failed | compensated
    input_payload           JSONB,
    output_payload          JSONB,
    error                   TEXT,
    attempts                INTEGER         NOT NULL DEFAULT 1,
    started_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    finished_at             TIMESTAMPTZ,
    duration_ms             INTEGER,
    CONSTRAINT activity_runs_status_chk CHECK (status IN ('running','completed','failed','compensated'))
);

CREATE INDEX IF NOT EXISTS idx_activity_runs_workflow ON activity_runs (workflow_run_id, step_index);
CREATE INDEX IF NOT EXISTS idx_activity_runs_tenant_started ON activity_runs (tenant_id, started_at DESC);

-- ── tool_calls ─────────────────────────────────────────────────────────────────
-- One row per LLM tool invocation (e.g. search_memory, gh_search). Useful for
-- debugging "what did the agent actually do?" and for usage analytics.
CREATE TABLE IF NOT EXISTS tool_calls (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    workflow_run_id         UUID            REFERENCES workflow_runs(id) ON DELETE CASCADE,
    agent_id                UUID            REFERENCES agents(id) ON DELETE SET NULL,
    tool_name               VARCHAR(128)    NOT NULL,
    arguments               JSONB           NOT NULL DEFAULT '{}'::jsonb,
    result                  JSONB,
    error                   TEXT,
    duration_ms             INTEGER,
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tool_calls_workflow_run ON tool_calls (workflow_run_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_tool_calls_tenant_tool ON tool_calls (tenant_id, tool_name, occurred_at DESC);
