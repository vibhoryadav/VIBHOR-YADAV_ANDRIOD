-- LedgerLab Idempotent Demo Seed Script (PostgreSQL)
-- Seeds synthetic workspaces, users, customers, chart of accounts, and a balanced opening deposit.
-- Safe to run repeatedly; uses fixed deterministic UUIDs and ON CONFLICT DO NOTHING.

BEGIN;

INSERT INTO workspaces (id, name, currency)
VALUES
    ('11111111-1111-4111-8111-111111111101', 'LedgerLab Sandbox (INR)', 'INR'),
    ('11111111-1111-4111-8111-111111111102', 'Marketplace Escrow Sim', 'INR')
ON CONFLICT (id) DO NOTHING;

-- Demo users (password hashes are non-functional placeholders; replace via auth registration/seed script)
INSERT INTO users (id, name, email, password_hash)
VALUES
    ('22222222-2222-4222-8222-222222222201', 'Aarav Mehta', 'aarav@ledgerlab.dev', '$argon2id$v=19$m=65536,t=3,p=1$placeholder_salt$placeholder_hash'),
    ('22222222-2222-4222-8222-222222222202', 'Priya Sharma', 'priya.admin@ledgerlab.dev', '$argon2id$v=19$m=65536,t=3,p=1$placeholder_salt$placeholder_hash'),
    ('22222222-2222-4222-8222-222222222203', 'Rohan Kulkarni', 'rohan.dev@ledgerlab.dev', '$argon2id$v=19$m=65536,t=3,p=1$placeholder_salt$placeholder_hash'),
    ('22222222-2222-4222-8222-222222222204', 'Neha Kapoor', 'neha.audit@ledgerlab.dev', '$argon2id$v=19$m=65536,t=3,p=1$placeholder_salt$placeholder_hash')
ON CONFLICT (id) DO NOTHING;

INSERT INTO workspace_members (workspace_id, user_id, role)
VALUES
    ('11111111-1111-4111-8111-111111111101', '22222222-2222-4222-8222-222222222201', 'OWNER'),
    ('11111111-1111-4111-8111-111111111101', '22222222-2222-4222-8222-222222222202', 'ADMIN'),
    ('11111111-1111-4111-8111-111111111101', '22222222-2222-4222-8222-222222222203', 'DEVELOPER'),
    ('11111111-1111-4111-8111-111111111101', '22222222-2222-4222-8222-222222222204', 'VIEWER'),
    ('11111111-1111-4111-8111-111111111102', '22222222-2222-4222-8222-222222222201', 'OWNER')
ON CONFLICT (workspace_id, user_id) DO NOTHING;

INSERT INTO customers (id, workspace_id, full_name, email, reference_code, segment, status, risk_score)
VALUES
    ('33333333-3333-4333-8333-333333333301', '11111111-1111-4111-8111-111111111101', 'Ananya Verma', 'ananya.verma@example.in', 'CUST-1001', 'Merchant Treasury', 'ACTIVE', 12),
    ('33333333-3333-4333-8333-333333333302', '11111111-1111-4111-8111-111111111101', 'Vikramaditya Rao', 'vikram.rao@example.in', 'CUST-1002', 'SMB Payouts', 'ACTIVE', 24)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_accounts (id, workspace_id, customer_id, account_number, name, category, normal_balance, currency, status, is_system_account, cached_balance_paise)
VALUES
    ('44444444-4444-4444-8444-444444444400', '11111111-1111-4111-8111-111111111101', NULL, 'SYS-CLR-SBOX', 'Simulated External Clearing Account', 'ASSET', 'DEBIT', 'INR', 'ACTIVE', TRUE, 12500000),
    ('44444444-4444-4444-8444-444444444401', '11111111-1111-4111-8111-111111111101', '33333333-3333-4333-8333-333333333301', 'LL4091827364', 'Ananya Merchant Operating INR', 'LIABILITY', 'CREDIT', 'INR', 'ACTIVE', FALSE, 12500000),
    ('44444444-4444-4444-8444-444444444402', '11111111-1111-4111-8111-111111111101', '33333333-3333-4333-8333-333333333302', 'LL5182930475', 'Vikram SMB Settlement Wallet', 'LIABILITY', 'CREDIT', 'INR', 'ACTIVE', FALSE, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO transactions (id, workspace_id, type, status, amount_paise, currency, sender_account_id, receiver_account_id, reference, description, idempotency_key, created_by_user_id, completed_at)
VALUES
    ('55555555-5555-4555-8555-555555555501', '11111111-1111-4111-8111-111111111101', 'DEPOSIT', 'SUCCEEDED', 12500000, 'INR', '44444444-4444-4444-8444-444444444400', '44444444-4444-4444-8444-444444444401', 'DEP-OPEN-01', 'Initial treasury funding (₹1,25,000.00)', 'seed_dep_001', '22222222-2222-4222-8222-222222222201', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO journal_entries (id, workspace_id, transaction_id, reference, description, currency)
VALUES
    ('66666666-6666-4666-8666-666666666601', '11111111-1111-4111-8111-111111111101', '55555555-5555-4555-8555-555555555501', 'DEP-OPEN-01', 'Initial treasury funding (₹1,25,000.00)', 'INR')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger_postings (id, workspace_id, journal_entry_id, account_id, side, amount_paise, currency)
VALUES
    ('77777777-7777-4777-8777-777777777701', '11111111-1111-4111-8111-111111111101', '66666666-6666-4666-8666-666666666601', '44444444-4444-4444-8444-444444444400', 'DEBIT', 12500000, 'INR'),
    ('77777777-7777-4777-8777-777777777702', '11111111-1111-4111-8111-111111111101', '66666666-6666-4666-8666-666666666601', '44444444-4444-4444-8444-444444444401', 'CREDIT', 12500000, 'INR')
ON CONFLICT (id) DO NOTHING;

COMMIT;
