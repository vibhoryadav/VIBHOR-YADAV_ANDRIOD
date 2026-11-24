# LedgerLab — Fintech Infrastructure Simulator

**Tagline:** *Financial infrastructure. Made understandable.*

LedgerLab is an open-source educational financial infrastructure simulator built for Android (Kotlin + Jetpack Compose) with an accompanying PostgreSQL double-entry ledger schema. It enables developers to explore, simulate, and inspect double-entry accounting, transaction lifecycles, idempotency guarantees, explainable risk scoring, reconciliation, HMAC-SHA256 webhook outbox events, and REST API workflows.

---

## 1. Project Objectives

- Demonstrate production-grade fintech invariants: double-entry balancing (`Debits == Credits`), integer paise arithmetic, deterministic account lock ordering, and immutable ledger history.
- Provide an inspectable, developer-first Android dashboard modeled after modern SaaS developer consoles.
- Enforce strict separation between **non-persistent in-memory Preview Mode** and **authoritative PostgreSQL persistence** (no silent substitution with SQLite or browser/local storage).

---

## 2. Screenshots

> Preview screenshots can be captured from the AI Studio Streaming Android Emulator or a local Pixel emulator running `app-debug.apk`.

| Overview Dashboard | Double-Entry Ledger Explorer | API Playground & Webhooks |
| --- | --- | --- |
| *[Placeholder: Overview KPIs & Charts]* | *[Placeholder: Balanced Journal Detail]* | *[Placeholder: REST Console & HMAC Verifier]* |

---

## 3. Implemented Features vs. Planned Roadmap

### Implemented in Current Repository
- **Double-Entry Financial Engine (`LedgerLabEngine.kt`)**: Chart of accounts (`Simulated External Clearing Account` Asset/Debit + `Customer Wallet Accounts` Liability/Credit), 64-bit integer `Long` paise storage & Indian INR formatting, atomic deposits/transfers/withdrawals, and opposing reversal journals.
- **Concurrency & Idempotency**: Deterministic sorted per-account locking (`Mutex`), SHA-256 request payload hashing (`Idempotency-Key` replay + `409 Conflict` on mutated payloads), and strict transaction state-machine transitions.
- **Authentication & Multi-Workspace RBAC**: PBKDF2-HMAC-SHA256 password hashing, session tracking, 4 roles (`Owner`, `Admin`, `Developer`, `Viewer`), and workspace isolation guards.
- **Explainable 7-Rule Risk Engine (`RiskEngine.kt`)**: Deterministic scoring (`0–100`) across high value, frequency spikes, failure streaks, new recipient accounts, unusual size, fan-out transfers, and 24h velocity caps with Admin/Owner review workflows.
- **Operational & Developer Screens (14 routes)**: Overview, Accounts, Customers, Transactions, Transfers & 8-Scenario Payment Simulator, Ledger Explorer, Reconciliation (with strict CSV statement importer), Risk & Fraud, API Playground (`ApiRouter`), OpenAPI 3.1 Docs, Webhooks (outbox + HMAC-SHA256 signature verifier), API Keys (SHA-256 hashed, shown once), Audit Logs, and Settings.
- **PostgreSQL Schema & CI**: 19-table SQL migration (`db/migrations/001_initial_schema.sql`) with `DEFERRABLE INITIALLY DEFERRED` balance triggers and immutability triggers, idempotent seed (`db/seeds/001_demo_seed.sql`), and GitHub Actions CI (`.github/workflows/ci.yml`).
- **Automated Test Suite**: 15 financial/security invariant tests (`LedgerLabFinancialEngineTest.kt`) + Robolectric Compose UI CUJ tests (`ExampleRobolectricTest.kt`).

### Planned / External Backend Roadmap
- Standalone Node.js + Express TypeScript server runtime implementation wired to `db/migrations/001_initial_schema.sql` (Android app currently includes `LedgerLabRemoteClient` `/api/health` connector and runs local engine in clearly labelled Preview Mode when no remote server is attached).
- Live PostgreSQL-backed mutation syncing from the Android client when connected to Cloud Run.

---

## 4. Technology Stack

- **Android Client & Core Engine**: Kotlin, Jetpack Compose, Material Design 3, Navigation Compose, Coroutines & StateFlow, Retrofit 2, OkHttp, Moshi, JUnit 4, Robolectric.
- **Typography & Assets**: Local bundled `Inter` and `JetBrains Mono` fonts (`res/font/`), adaptive launcher icon.
- **Database Schema & CI**: PostgreSQL 16 DDL (`BIGINT` paise, UUID PKs, deferred constraint triggers), GitHub Actions.

---

## 5. Application Architecture

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for full details on layered design (`domain`, `engine`, `network`, `ui`), deadlock-free account lock ordering, and PostgreSQL trigger guarantees.

---

## 6. Repository Structure

```text
.
├── .github/workflows/ci.yml        # Android tests/build + PostgreSQL migration CI
├── .env.example                    # Android Secrets plugin template (commented placeholders)
├── .gitignore                      # Excludes .env, keystores, caches, and build outputs
├── ARCHITECTURE.md                 # System & concurrency architecture
├── README.md                       # Project documentation
├── app/
│   ├── build.gradle.kts            # App module Gradle configuration
│   └── src/
│       ├── main/java/com/example/
│       │   ├── MainActivity.kt     # Root Compose host & navigation
│       │   ├── domain/             # Money (paise) & domain models
│       │   ├── engine/             # Double-entry engine, Risk, Crypto, Seed, ApiRouter
│       │   ├── network/            # Retrofit remote /api/health client
│       │   └── ui/                 # ViewModel, DesignSystem, AppShell, 14 screens
│       └── test/java/com/example/  # Financial integrity & Robolectric CUJ tests
├── backend/.env.example            # Server-only PostgreSQL/session env template
├── db/
│   ├── migrations/001_initial_schema.sql
│   └── seeds/001_demo_seed.sql
├── docs/
│   ├── API.md                      # OpenAPI 3.1 endpoint & error reference
│   └── DEPLOYMENT.md               # PostgreSQL & Cloud Run deployment guide
└── gradle/libs.versions.toml       # Version catalog
```

---

## 7. Installation & Development Commands

### Prerequisites
- JDK 17+
- Gradle 9.3.1 (or Android Studio)
- PostgreSQL 16+ (optional, for database migration & remote server setup)

### Build & Run Tests

```bash
# Run all financial integrity unit tests and Robolectric Compose UI tests
gradle :app:testDebugUnitTest

# Build debug APK
gradle :app:assembleDebug
```

---

## 8. Environment Configuration

- Android build secrets use `.env.example` (never commit `.env`).
- Server-side PostgreSQL & session secrets are templated in `backend/.env.example` and kept strictly out of Android `BuildConfig` / frontend bundles.

---

## 9. PostgreSQL Setup, Migrations & Seed Data

```bash
createdb ledgerlab
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/migrations/001_initial_schema.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/seeds/001_demo_seed.sql
```

---

## 10. Testing Instructions

Run the complete automated test suite:

```bash
gradle :app:testDebugUnitTest
```

Tests verify:
1. Balanced deposit, transfer, and withdrawal postings.
2. Zero balance/journal mutation on failed, pending, or review-held transactions.
3. Overdraft prevention and concurrent transfer race protection.
4. `Idempotency-Key` replay and `409 Conflict` payload-mismatch detection.
5. Opposing reversal journal generation and posted record immutability.
6. Cross-workspace isolation, RBAC role enforcement, API key revocation, and HMAC-SHA256 webhook verification.

---

## 11. API & Deployment Documentation

- API Reference: [`docs/API.md`](docs/API.md)
- Deployment & Cloud Run Guide: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)

---

## 12. Security Considerations

- Passwords hashed with PBKDF2-HMAC-SHA256 (per-user 16-byte random salt) in the Android engine and Argon2id in the PostgreSQL schema specification.
- API keys stored strictly as SHA-256 hashes; full key revealed only once on creation.
- Webhook payloads signed via HMAC-SHA256 (`t=<ts>,v1=<hex>`) and verified with constant-time comparison.
- No `.env`, `DATABASE_URL`, session tokens, or private keys are committed or bundled in frontend assets.

---

## 13. Educational Simulation Disclaimer

LedgerLab is an **educational financial infrastructure simulator**. It never connects to real banking rails, payment processors, or card networks, and never represents simulated INR balances as real funds.
