# LedgerLab Deployment & PostgreSQL Setup Guide

## 1. PostgreSQL Database Setup

Provision PostgreSQL 16+ (e.g., Cloud SQL for PostgreSQL, Neon, Supabase, or local Docker):

```bash
createdb ledgerlab
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/migrations/001_initial_schema.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/seeds/001_demo_seed.sql
```

### Enforced Database Triggers

- `trg_verify_balanced_journal` (`DEFERRABLE INITIALLY DEFERRED`): Guarantees at transaction commit time that every journal has `>= 2` posting lines and `SUM(DEBIT) == SUM(CREDIT)`.
- `trg_immutable_journal_entries` & `trg_immutable_ledger_postings`: Rejects `UPDATE` and `DELETE` on posted ledger rows.

## 2. Backend Service Configuration (Google Cloud Run)

Store secrets in **Google Cloud Secret Manager** (never commit `.env` files):
- `DATABASE_URL`
- `SESSION_SECRET`
- `CSRF_SECRET`
- `WEBHOOK_SIGNING_MASTER_KEY`

Ensure the Cloud Run service:
- Listens on `process.env.PORT` (`0.0.0.0`)
- Exposes `GET /api/health`
- Handles `SIGTERM` / `SIGINT` for graceful PostgreSQL pool shutdown

## 3. Android Client Build & Backend Connection

Build debug or release Android artifacts:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

To point the Android app at a deployed backend server:
1. Launch **LedgerLab** on the device/emulator.
2. Navigate to **Settings**.
3. Enter your API base URL (e.g., `https://ledgerlab-api-xxxx.run.app/api` or `http://10.0.2.2:3000/api`).
4. Tap **Test /api/health**.
