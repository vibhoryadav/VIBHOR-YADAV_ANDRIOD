-- LedgerLab PostgreSQL Authoritative Schema Migration (Idempotent)
-- Enforces double-entry accounting, BIGINT paise money, RBAC workspace isolation,
-- deferred balance verification triggers, and immutable posted financial rows.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    reference_code TEXT NOT NULL,
    segment TEXT NOT NULL DEFAULT 'Retail Wallet',
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'UNDER_REVIEW', 'SUSPENDED')),
    risk_score INTEGER NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, reference_code)
);

CREATE TABLE IF NOT EXISTS ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    account_number TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('ASSET', 'LIABILITY')),
    normal_balance TEXT NOT NULL CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    is_system_account BOOLEAN NOT NULL DEFAULT FALSE,
    cached_balance_paise BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    type TEXT NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER', 'WITHDRAWAL', 'REVERSAL')),
    status TEXT NOT NULL CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'REQUIRES_REVIEW', 'REVERSED')),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    sender_account_id UUID REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    receiver_account_id UUID REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    reference TEXT NOT NULL,
    description TEXT NOT NULL,
    failure_reason TEXT,
    idempotency_key TEXT NOT NULL,
    reversed_transaction_id UUID REFERENCES transactions(id) ON DELETE RESTRICT,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    reference TEXT NOT NULL,
    description TEXT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    is_reversal BOOLEAN NOT NULL DEFAULT FALSE,
    reverses_journal_id UUID REFERENCES journal_entries(id) ON DELETE RESTRICT,
    effective_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ledger_postings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    side TEXT NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    idempotency_key TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS risk_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    recommended_action TEXT NOT NULL,
    rules_triggered JSONB NOT NULL DEFAULT '[]'::jsonb,
    resolution_note TEXT,
    resolved_by_user_id UUID REFERENCES users(id),
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    key_prefix TEXT NOT NULL,
    key_hash_sha256 TEXT NOT NULL UNIQUE,
    scopes TEXT[] NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    description TEXT NOT NULL,
    subscribed_events TEXT[] NOT NULL,
    signing_secret_hash TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    payload JSONB NOT NULL,
    signature_header TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES webhook_events(id) ON DELETE RESTRICT,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    response_status INTEGER NOT NULL,
    response_body TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    succeeded BOOLEAN NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    accounts_checked INTEGER NOT NULL,
    total_debit_balance_paise BIGINT NOT NULL,
    total_credit_balance_paise BIGINT NOT NULL,
    matched_accounts INTEGER NOT NULL,
    mismatched_accounts INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('MATCHED', 'MISMATCH_DETECTED')),
    source_label TEXT NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reconciliation_issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    ledger_balance_paise BIGINT NOT NULL,
    compared_balance_paise BIGINT NOT NULL,
    variance_paise BIGINT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    actor_user_id UUID REFERENCES users(id),
    actor_email TEXT NOT NULL,
    event_type TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    source_ip INET,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS balance_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES ledger_accounts(id) ON DELETE RESTRICT,
    balance_paise BIGINT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for query performance
CREATE INDEX IF NOT EXISTS idx_tx_ws_created ON transactions (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_postings_acc ON ledger_postings (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_postings_journal ON ledger_postings (journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_audit_ws_created ON audit_logs (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_risk_ws_tx ON risk_evaluations (workspace_id, transaction_id);

-- Deferred constraint trigger: verify every posted journal balances (Debits == Credits) at commit time
CREATE OR REPLACE FUNCTION verify_balanced_journal() RETURNS TRIGGER AS $$
DECLARE
    v_debits BIGINT;
    v_credits BIGINT;
    v_count INTEGER;
BEGIN
    SELECT
        COALESCE(SUM(CASE WHEN side = 'DEBIT' THEN amount_paise ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN side = 'CREDIT' THEN amount_paise ELSE 0 END), 0),
        COUNT(*)
    INTO v_debits, v_credits, v_count
    FROM ledger_postings
    WHERE journal_entry_id = NEW.journal_entry_id;

    IF v_count < 2 OR v_debits <> v_credits THEN
        RAISE EXCEPTION 'Unbalanced journal % (debits=%, credits=%, lines=%)',
            NEW.journal_entry_id, v_debits, v_credits, v_count;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_verify_balanced_journal ON ledger_postings;
CREATE CONSTRAINT TRIGGER trg_verify_balanced_journal
    AFTER INSERT ON ledger_postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_balanced_journal();

-- Immutability trigger: prevent UPDATE or DELETE on posted ledger rows
CREATE OR REPLACE FUNCTION prevent_posted_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Posted ledger records are immutable. Create a reversal journal instead.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_immutable_journal_entries ON journal_entries;
CREATE TRIGGER trg_immutable_journal_entries
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_posted_ledger_mutation();

DROP TRIGGER IF EXISTS trg_immutable_ledger_postings ON ledger_postings;
CREATE TRIGGER trg_immutable_ledger_postings
    BEFORE UPDATE OR DELETE ON ledger_postings
    FOR EACH ROW EXECUTE FUNCTION prevent_posted_ledger_mutation();
