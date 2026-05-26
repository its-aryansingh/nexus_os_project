-- ════════════════════════════════════════════════════════════════════════════════
-- V001 — Tenants + Users (root multi-tenancy partition)
-- See: docs/SYSTEM_DESIGN.md §3.20 Multi-Tenancy; docs/ADRS/0006-rls-multi-tenancy.md
-- Idempotent: every CREATE uses IF NOT EXISTS.
-- ════════════════════════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── tenants ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tenants (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug                    VARCHAR(64)     NOT NULL UNIQUE,
    name                    VARCHAR(255)    NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'active',  -- active | suspended | trial
    monthly_usd_budget      NUMERIC(12, 2)  NOT NULL DEFAULT 50.00,
    plan                    VARCHAR(32)     NOT NULL DEFAULT 'starter', -- starter | pro | enterprise
    settings                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT tenants_status_chk CHECK (status IN ('active', 'suspended', 'trial')),
    CONSTRAINT tenants_plan_chk   CHECK (plan   IN ('starter', 'pro', 'enterprise'))
);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants (status);

-- ── users ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email                   VARCHAR(255)    NOT NULL,
    display_name            VARCHAR(255),
    role                    VARCHAR(32)     NOT NULL DEFAULT 'operator', -- owner | admin | operator | viewer
    external_subject        VARCHAR(255),                                -- IdP subject for OIDC mapping (v0.2)
    last_seen_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_per_tenant UNIQUE (tenant_id, email),
    CONSTRAINT users_role_chk CHECK (role IN ('owner', 'admin', 'operator', 'viewer'))
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_external_subject ON users (external_subject) WHERE external_subject IS NOT NULL;

-- ── default dev tenant (v0.1 only — auth lands in v0.2) ────────────────────────
INSERT INTO tenants (id, slug, name, plan, monthly_usd_budget)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default (dev)', 'starter', 50.00)
ON CONFLICT (id) DO NOTHING;
