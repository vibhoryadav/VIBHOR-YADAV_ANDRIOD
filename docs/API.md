# LedgerLab API Documentation (OpenAPI 3.1 Reference)

## Base URL

- Local Android Emulator -> Host API: `http://10.0.2.2:3000/api`
- In-App Playground Router: Executes directly against `com.example.engine.ApiRouter`

## Authentication & Headers

- **Session Auth:** `Cookie` (HttpOnly, SameSite=Lax) + `X-CSRF-Token` for write operations.
- **Programmatic API Keys:** `Authorization: Bearer ll_test_...`
- **Idempotency:** Required on `POST /api/deposits`, `POST /api/transfers`, and `POST /api/withdrawals` via `Idempotency-Key: <unique-key>`.

## Monetary Representation

All monetary inputs use decimal strings with at most 2 decimal places (`"1500.50"`). Storage and API responses use integer paise (`BIGINT`, e.g., `150050`).

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/health` | Service and database health check |
| `GET` | `/api/overview/metrics` | Workspace KPIs and 7-day volume series |
| `GET` | `/api/customers` | List workspace-scoped customers |
| `POST` | `/api/customers` | Create a synthetic customer |
| `GET` | `/api/accounts` | List accounts with ledger-derived INR balances |
| `POST` | `/api/accounts` | Create a Customer Wallet Liability account |
| `POST` | `/api/deposits` | Deposit simulated INR (Debits Clearing, Credits Wallet) |
| `POST` | `/api/transfers` | Atomic P2P wallet transfer with risk check & idempotency |
| `POST` | `/api/withdrawals` | Withdraw simulated INR (Debits Wallet, Credits Clearing) |
| `GET` | `/api/transactions` | List transactions |
| `GET` | `/api/ledger/journals` | List balanced immutable journals |
| `GET` | `/api/ledger/trial-balance` | Account-level debits, credits, and net balances |
| `POST` | `/api/reconciliation/run` | Compare ledger-derived balances against cached snapshots |

## Standard Error Codes

- `400 INVALID_REQUEST` — Malformed JSON, missing header, or schema failure
- `401 UNAUTHORIZED` — Invalid credentials, expired session, or revoked API key
- `403 FORBIDDEN` — Role permission denied or cross-workspace access attempt
- `404 RESOURCE_NOT_FOUND` — Resource ID does not exist in active workspace
- `409 CONFLICT` — `Idempotency-Key` reused with a different request payload
- `422 FINANCIAL_VALIDATION_ERROR` — Insufficient funds, self-transfer, invalid decimal, or invalid state transition
- `429 RATE_LIMITED` — Login or API rate limit exceeded
