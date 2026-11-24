# LedgerLab Architecture

## 1. Layered Design

- **Domain Layer (`com.example.domain`)**
  - `Money.kt`: Integer `Long` paise conversions and Indian numbering formatter. No `Double`/`Float` arithmetic is ever used for monetary values.
  - `Models.kt`: Immutable data classes representing workspaces, RBAC roles, chart of accounts, transaction lifecycles, double-entry postings, risk evaluations, reconciliation runs, outbox webhooks, API keys, and audit logs.
- **Financial & Security Engine (`com.example.engine`)**
  - `LedgerLabEngine.kt`: Serializes mutations with a top-level `Mutex` and acquires per-account `Mutex` locks in sorted account-ID order (`withDeterministicAccountLocks`) to mirror PostgreSQL `SELECT ... FOR UPDATE` deadlock-free locking.
  - `RiskEngine.kt`: Stateless, deterministic 7-rule behavioural scorer producing explainable `RiskRuleMatch` entries.
  - `CryptoUtils.kt`: PBKDF2-HMAC-SHA256 password hashing, SHA-256 payload/API-key hashing, HMAC-SHA256 webhook signing, and constant-time verification.
  - `ApiRouter.kt`: In-process REST dispatcher backing the API Playground with real engine execution and latency measurement.
- **Network Layer (`com.example.network`)**
  - `LedgerLabRemoteClient.kt`: Retrofit + Moshi client for connecting to an external PostgreSQL-backed LedgerLab API server (`GET /api/health`).
- **UI Layer (`com.example.ui`)**
  - `LedgerLabViewModel.kt`: Manages session state, workspace scoping, and reactive `StateFlow<EngineSnapshot>`.
  - `AppShell.kt`: Responsive layout switching between a collapsible persistent desktop sidebar (`>= 840.dp`) and a mobile `ModalNavigationDrawer`.
  - `screens/*`: 14 dedicated functional screens sharing the crisp light design system (`#FFFFFF` / `#F6F8FA` / `#172033` / `#635BFF`).

## 2. Database & Consistency Guarantees

When deployed with PostgreSQL (`db/migrations/001_initial_schema.sql`):
- All financial mutations run inside `SERIALIZABLE` / `READ COMMITTED` transactions with deterministic `SELECT ... FOR UPDATE` ordering on `ledger_accounts`.
- `trg_verify_balanced_journal` asserts at commit time that every journal entry has `>= 2` posting lines and `SUM(DEBIT) == SUM(CREDIT)`.
- `trg_immutable_journal_entries` and `trg_immutable_ledger_postings` reject `UPDATE` and `DELETE` statements on posted ledger tables.
