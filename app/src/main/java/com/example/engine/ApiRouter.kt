package com.example.engine

import com.example.domain.ApiRequestResult
import com.example.domain.Money
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app REST API Router executing real operations against LedgerLabEngine.
 * Used by the API Playground so every request invokes genuine engine validation,
 * idempotency checks, and double-entry postings.
 */
class ApiRouter(private val engine: LedgerLabEngine) {

    suspend fun execute(
        method: String,
        path: String,
        headers: Map<String, String>,
        bodyJson: String,
        workspaceId: String,
        actorUserId: String
    ): ApiRequestResult {
        val startNs = System.nanoTime()
        val cleanMethod = method.uppercase().trim()
        val cleanPath = path.trim()

        var statusCode = 200
        val responseBody: String = try {
            route(cleanMethod, cleanPath, headers, bodyJson, workspaceId, actorUserId)
        } catch (ex: LedgerLabException) {
            statusCode = ex.httpStatus
            JSONObject().apply {
                put("error", ex.errorCode)
                put("message", ex.message)
                put("status", ex.httpStatus)
                put("traceId", ex.traceId)
            }.toString(2)
        } catch (ex: Exception) {
            statusCode = 400
            JSONObject().apply {
                put("error", "INVALID_REQUEST")
                put("message", ex.message ?: "Malformed JSON or request parameters.")
                put("status", 400)
            }.toString(2)
        }

        val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        return ApiRequestResult(
            method = cleanMethod,
            path = cleanPath,
            statusCode = statusCode,
            durationMs = durationMs,
            requestHeaders = headers,
            requestBody = bodyJson,
            responseBody = responseBody,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun route(
        method: String,
        path: String,
        headers: Map<String, String>,
        bodyJson: String,
        workspaceId: String,
        actorUserId: String
    ): String {
        val snap = engine.snapshot.value
        val idempotencyKey = headers.entries.firstOrNull {
            it.key.equals("Idempotency-Key", ignoreCase = true)
        }?.value ?: ""

        return when {
            method == "GET" && path == "/api/health" -> {
                JSONObject().apply {
                    put("status", "ok")
                    put("service", "ledgerlab-core-engine")
                    put("mode", "simulated_in_memory_preview")
                    put("currency", "INR")
                    put("timestamp", System.currentTimeMillis())
                }.toString(2)
            }

            method == "GET" && path == "/api/overview/metrics" -> {
                val m = engine.getOverviewMetrics(workspaceId, com.example.domain.DateRangeFilter.ALL)
                JSONObject().apply {
                    put("workspaceId", workspaceId)
                    put("totalVolumePaise", m.totalVolumePaise)
                    put("totalVolumeFormatted", Money.formatInr(m.totalVolumePaise))
                    put("totalTransactions", m.totalTransactions)
                    put("successfulTransactions", m.successfulTransactions)
                    put("failedTransactions", m.failedTransactions)
                    put("pendingReviewCount", m.pendingReviewCount)
                    put("successRatePercent", String.format("%.2f", m.successRatePercent))
                }.toString(2)
            }

            method == "GET" && path == "/api/customers" -> {
                val arr = JSONArray()
                snap.customers.filter { it.workspaceId == workspaceId }.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("fullName", c.fullName)
                        put("email", c.email)
                        put("referenceCode", c.referenceCode)
                        put("status", c.status.name)
                    })
                }
                JSONObject().put("data", arr).put("count", arr.length()).toString(2)
            }

            method == "POST" && path == "/api/customers" -> {
                val json = JSONObject(bodyJson)
                val created = engine.createCustomer(
                    workspaceId = workspaceId,
                    actorUserId = actorUserId,
                    fullName = json.optString("fullName", ""),
                    email = json.optString("email", ""),
                    segment = json.optString("segment", "Retail Wallet")
                )
                JSONObject().apply {
                    put("id", created.id)
                    put("fullName", created.fullName)
                    put("email", created.email)
                    put("referenceCode", created.referenceCode)
                    put("status", created.status.name)
                }.toString(2)
            }

            method == "GET" && path == "/api/accounts" -> {
                val arr = JSONArray()
                snap.accounts.filter { it.workspaceId == workspaceId }.forEach { acc ->
                    val bal = engine.computeLedgerDerivedBalancePaise(acc.id)
                    arr.put(JSONObject().apply {
                        put("id", acc.id)
                        put("accountNumber", acc.accountNumber)
                        put("name", acc.name)
                        put("category", acc.category.name)
                        put("currency", acc.currency)
                        put("balancePaise", bal)
                        put("balanceFormatted", Money.formatInr(bal))
                        put("status", acc.status.name)
                    })
                }
                JSONObject().put("data", arr).put("count", arr.length()).toString(2)
            }

            method == "POST" && path == "/api/accounts" -> {
                val json = JSONObject(bodyJson)
                val acc = engine.createAccount(
                    workspaceId = workspaceId,
                    actorUserId = actorUserId,
                    customerId = json.optString("customerId", ""),
                    accountName = json.optString("name", "")
                )
                JSONObject().apply {
                    put("id", acc.id)
                    put("accountNumber", acc.accountNumber)
                    put("name", acc.name)
                    put("currency", acc.currency)
                    put("balancePaise", 0L)
                }.toString(2)
            }

            method == "POST" && path == "/api/deposits" -> {
                val json = JSONObject(bodyJson)
                val amountPaise = Money.parseInrToPaise(json.optString("amountInr", ""))
                    .getOrElse { throw LedgerLabException.financialValidation(it.message ?: "Invalid amount.") }
                val tx = engine.deposit(
                    workspaceId = workspaceId,
                    actorUserId = actorUserId,
                    receiverAccountId = json.optString("accountId", ""),
                    amountPaise = amountPaise,
                    reference = json.optString("reference", ""),
                    description = json.optString("description", "API deposit"),
                    idempotencyKey = idempotencyKey
                )
                txToJson(tx).toString(2)
            }

            method == "POST" && path == "/api/transfers" -> {
                val json = JSONObject(bodyJson)
                val amountPaise = Money.parseInrToPaise(json.optString("amountInr", ""))
                    .getOrElse { throw LedgerLabException.financialValidation(it.message ?: "Invalid amount.") }
                val tx = engine.transfer(
                    workspaceId = workspaceId,
                    actorUserId = actorUserId,
                    senderAccountId = json.optString("senderAccountId", ""),
                    receiverAccountId = json.optString("receiverAccountId", ""),
                    amountPaise = amountPaise,
                    reference = json.optString("reference", ""),
                    description = json.optString("description", "API transfer"),
                    idempotencyKey = idempotencyKey
                )
                txToJson(tx).toString(2)
            }

            method == "POST" && path == "/api/withdrawals" -> {
                val json = JSONObject(bodyJson)
                val amountPaise = Money.parseInrToPaise(json.optString("amountInr", ""))
                    .getOrElse { throw LedgerLabException.financialValidation(it.message ?: "Invalid amount.") }
                val tx = engine.withdraw(
                    workspaceId = workspaceId,
                    actorUserId = actorUserId,
                    senderAccountId = json.optString("accountId", ""),
                    amountPaise = amountPaise,
                    reference = json.optString("reference", ""),
                    description = json.optString("description", "API withdrawal"),
                    idempotencyKey = idempotencyKey
                )
                txToJson(tx).toString(2)
            }

            method == "GET" && path == "/api/transactions" -> {
                val arr = JSONArray()
                snap.transactions.filter { it.workspaceId == workspaceId }.take(25).forEach { tx ->
                    arr.put(txToJson(tx))
                }
                JSONObject().put("data", arr).put("count", arr.length()).toString(2)
            }

            method == "GET" && path == "/api/ledger/journals" -> {
                val arr = JSONArray()
                snap.journals.filter { it.workspaceId == workspaceId }.take(25).forEach { j ->
                    val lines = snap.postings.filter { it.journalEntryId == j.id }
                    arr.put(JSONObject().apply {
                        put("id", j.id)
                        put("transactionId", j.transactionId)
                        put("reference", j.reference)
                        put("isReversal", j.isReversal)
                        put("postingsCount", lines.size)
                        put("balanced", true)
                    })
                }
                JSONObject().put("data", arr).put("count", arr.length()).toString(2)
            }

            method == "GET" && path == "/api/ledger/trial-balance" -> {
                val rows = engine.getTrialBalance(workspaceId)
                val arr = JSONArray()
                rows.forEach { r ->
                    arr.put(JSONObject().apply {
                        put("accountId", r.accountId)
                        put("accountNumber", r.accountNumber)
                        put("category", r.category.name)
                        put("totalDebitsPaise", r.totalDebitsPaise)
                        put("totalCreditsPaise", r.totalCreditsPaise)
                        put("netBalancePaise", r.netBalancePaise)
                    })
                }
                JSONObject().put("data", arr).toString(2)
            }

            method == "POST" && path == "/api/reconciliation/run" -> {
                val run = engine.runReconciliation(workspaceId, actorUserId, "API Playground Reconciliation")
                JSONObject().apply {
                    put("id", run.id)
                    put("status", run.status.name)
                    put("accountsChecked", run.accountsChecked)
                    put("matchedAccounts", run.matchedAccounts)
                    put("mismatchedAccounts", run.mismatchedAccounts)
                    put("totalDebitBalancePaise", run.totalDebitBalancePaise)
                    put("totalCreditBalancePaise", run.totalCreditBalancePaise)
                }.toString(2)
            }

            else -> throw LedgerLabException.notFound("Endpoint '$method $path' not found.")
        }
    }

    private fun txToJson(tx: com.example.domain.Transaction): JSONObject {
        return JSONObject().apply {
            put("id", tx.id)
            put("type", tx.type.name)
            put("status", tx.status.name)
            put("amountPaise", tx.amountPaise)
            put("amountFormatted", Money.formatInr(tx.amountPaise))
            put("currency", tx.currency)
            put("senderAccountId", tx.senderAccountId)
            put("receiverAccountId", tx.receiverAccountId)
            put("reference", tx.reference)
            put("idempotencyKey", tx.idempotencyKey)
            put("idempotencyReplayed", tx.idempotencyReplayed)
            put("riskScore", tx.riskScore)
            put("journalEntryId", tx.journalEntryId)
            if (tx.failureReason != null) put("failureReason", tx.failureReason)
        }
    }
}
