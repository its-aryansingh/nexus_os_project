-- ════════════════════════════════════════════════════════════════════════════════
-- V003 — Outbox + Inbox (transactional outbox + consumer idempotency)
-- See: docs/SYSTEM_DESIGN.md §3.5 (Outbox), §3.6 (Inbox)
-- See: docs/ARCHITECTURE.md §4.1 (Write Path)
-- ════════════════════════════════════════════════════════════════════════════════

-- ── outbox ─────────────────────────────────────────────────────────────────────
-- Rows are written transactionally with the business event (atomic dual-write
-- avoidance). OutboxDispatcher polls undispatched rows on a 200ms tick and
-- publishes to Kafka, then marks dispatched_at.
CREATE TABLE IF NOT EXISTS outbox (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    aggregate_type          VARCHAR(64)     NOT NULL,   -- e.g. 'workflow_run'
    aggregate_id            VARCHAR(128)    NOT NULL,
    topic                   VARCHAR(128)    NOT NULL,   -- Kafka topic name
    partition_key           VARCHAR(255),               -- Kafka partition key (tenant_id usually)
    payload                 JSONB           NOT NULL,
    headers                 JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    dispatched_at           TIMESTAMPTZ,
    dispatch_attempts       INTEGER         NOT NULL DEFAULT 0,
    last_dispatch_error     TEXT
);

-- Partial index: dispatcher only scans undispatched rows.
-- Keeps the working set small as the table grows.
CREATE INDEX IF NOT EXISTS idx_outbox_undispatched
    ON outbox (created_at)
    WHERE dispatched_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON outbox (tenant_id, created_at DESC);

-- ── inbox ──────────────────────────────────────────────────────────────────────
-- Every Kafka consumer + webhook writes a row here BEFORE processing. The
-- PRIMARY KEY constraint causes duplicate deliveries to fail fast (caught and
-- silently dropped by the consumer).
CREATE TABLE IF NOT EXISTS inbox (
    event_id                VARCHAR(255)    PRIMARY KEY,    -- producer's unique id (Kafka offset, webhook id, etc.)
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    consumer                VARCHAR(128)    NOT NULL,       -- consumer group / handler name
    source_topic            VARCHAR(128),
    processed_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inbox_tenant ON inbox (tenant_id, processed_at DESC);

-- ── dlq (dead-letter) ──────────────────────────────────────────────────────────
-- Messages that fail processing after max retries land here for manual replay.
CREATE TABLE IF NOT EXISTS dlq (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            REFERENCES tenants(id) ON DELETE CASCADE,
    source_topic            VARCHAR(128)    NOT NULL,
    source_partition        INTEGER,
    source_offset           BIGINT,
    payload                 JSONB           NOT NULL,
    headers                 JSONB           NOT NULL DEFAULT '{}'::jsonb,
    error                   TEXT            NOT NULL,
    attempts                INTEGER         NOT NULL,
    occurred_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_dlq_unresolved ON dlq (occurred_at)
    WHERE resolved_at IS NULL;
