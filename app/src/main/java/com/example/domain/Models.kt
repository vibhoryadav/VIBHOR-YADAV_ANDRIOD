package com.example.domain

enum class WorkspaceRole(val level: Int, val label: String) {
    OWNER(4, "Owner"),
    ADMIN(3, "Admin"),
    DEVELOPER(2, "Developer"),
    VIEWER(1, "Viewer");

    fun canMutateFinancials(): Boolean = this == OWNER || this == ADMIN || this == DEVELOPER
    fun canReviewRisk(): Boolean = this == OWNER || this == ADMIN
    fun canManageKeysAndWebhooks(): Boolean = this == OWNER || this == ADMIN || this == DEVELOPER
    fun canManageMembers(): Boolean = this == OWNER || this == ADMIN
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Long
)

data class UserSession(
    val id: String,
    val userId: String,
    val tokenPreview: String,
    val createdAt: Long,
    val expiresAt: Long,
    val revokedAt: Long? = null
)

data class Workspace(
    val id: String,
    val name: String,
    val environmentLabel: String,
    val currency: String = Money.CURRENCY_INR,
    val createdAt: Long
)

data class WorkspaceMember(
    val workspaceId: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val role: WorkspaceRole,
    val joinedAt: Long
)

enum class CustomerStatus(val label: String) {
    ACTIVE("Active"),
    UNDER_REVIEW("Under Review"),
    SUSPENDED("Suspended")
}

data class Customer(
    val id: String,
    val workspaceId: String,
    val fullName: String,
    val email: String,
    val referenceCode: String,
    val segment: String,
    val status: CustomerStatus,
    val riskScore: Int,
    val createdAt: Long,
    val updatedAt: Long
)

enum class AccountCategory(val label: String) {
    ASSET("Asset"),
    LIABILITY("Liability")
}

enum class NormalBalance(val label: String) {
    DEBIT("Debit"),
    CREDIT("Credit")
}

enum class AccountStatus(val label: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    CLOSED("Closed")
}

data class LedgerAccount(
    val id: String,
    val workspaceId: String,
    val customerId: String?,
    val accountNumber: String,
    val name: String,
    val category: AccountCategory,
    val normalBalance: NormalBalance,
    val currency: String = Money.CURRENCY_INR,
    val status: AccountStatus,
    val isSystemAccount: Boolean = false,
    val cachedBalancePaise: Long = 0L,
    val createdAt: Long
)

enum class TransactionType(val label: String) {
    DEPOSIT("Deposit"),
    TRANSFER("Transfer"),
    WITHDRAWAL("Withdrawal"),
    REVERSAL("Reversal")
}

enum class TransactionStatus(val label: String) {
    CREATED("Created"),
    PROCESSING("Processing"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    REQUIRES_REVIEW("Requires Review"),
    REVERSED("Reversed")
}

data class LifecycleEvent(
    val status: TransactionStatus,
    val note: String,
    val timestamp: Long
)

data class Transaction(
    val id: String,
    val workspaceId: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val amountPaise: Long,
    val currency: String = Money.CURRENCY_INR,
    val senderAccountId: String?,
    val receiverAccountId: String?,
    val reference: String,
    val description: String,
    val failureReason: String? = null,
    val idempotencyKey: String,
    val idempotencyReplayed: Boolean = false,
    val riskEvaluationId: String? = null,
    val riskScore: Int = 0,
    val journalEntryId: String? = null,
    val reversedTransactionId: String? = null,
    val lifecycleTimeline: List<LifecycleEvent>,
    val createdByUserId: String,
    val createdAt: Long,
    val completedAt: Long? = null
)

enum class PostingSide(val label: String) {
    DEBIT("Debit"),
    CREDIT("Credit")
}

data class JournalEntry(
    val id: String,
    val workspaceId: String,
    val transactionId: String,
    val reference: String,
    val description: String,
    val currency: String = Money.CURRENCY_INR,
    val effectiveDate: Long,
    val postedAt: Long,
    val isReversal: Boolean = false,
    val reversesJournalId: String? = null
)

data class LedgerPosting(
    val id: String,
    val workspaceId: String,
    val journalEntryId: String,
    val accountId: String,
    val side: PostingSide,
    val amountPaise: Long,
    val currency: String = Money.CURRENCY_INR,
    val createdAt: Long
)

data class IdempotencyRecord(
    val workspaceId: String,
    val actorId: String,
    val key: String,
    val payloadHash: String,
    val transactionId: String,
    val responseStatus: Int,
    val createdAt: Long
)

enum class RiskSeverity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical")
}

enum class RiskAction(val label: String) {
    ALLOW("Allow"),
    FLAG_MONITOR("Flag & Monitor"),
    HOLD_FOR_REVIEW("Hold for Review"),
    BLOCK("Block")
}

data class RiskRuleConfig(
    val ruleCode: String,
    val title: String,
    val description: String,
    val weight: Int,
    val enabled: Boolean = true
)

data class RiskRuleMatch(
    val ruleCode: String,
    val title: String,
    val scoreContribution: Int,
    val reason: String
)

data class RiskEvaluation(
    val id: String,
    val workspaceId: String,
    val transactionId: String,
    val customerId: String?,
    val score: Int,
    val severity: RiskSeverity,
    val recommendedAction: RiskAction,
    val rulesTriggered: List<RiskRuleMatch>,
    val resolutionNote: String? = null,
    val resolvedByUserId: String? = null,
    val evaluatedAt: Long,
    val resolvedAt: Long? = null
)

enum class ReconciliationStatus(val label: String) {
    MATCHED("Matched"),
    MISMATCH_DETECTED("Mismatch Detected")
}

data class ReconciliationIssue(
    val id: String,
    val runId: String,
    val workspaceId: String,
    val accountId: String,
    val accountNumber: String,
    val accountName: String,
    val ledgerBalancePaise: Long,
    val comparedBalancePaise: Long,
    val variancePaise: Long,
    val source: String,
    val resolved: Boolean = false
)

data class ReconciliationRun(
    val id: String,
    val workspaceId: String,
    val accountsChecked: Int,
    val totalDebitBalancePaise: Long,
    val totalCreditBalancePaise: Long,
    val matchedAccounts: Int,
    val mismatchedAccounts: Int,
    val status: ReconciliationStatus,
    val sourceLabel: String,
    val issues: List<ReconciliationIssue>,
    val createdByUserId: String,
    val createdAt: Long
)

enum class WebhookEventType(val eventName: String) {
    TRANSACTION_CREATED("transaction.created"),
    TRANSACTION_PROCESSING("transaction.processing"),
    TRANSACTION_SUCCEEDED("transaction.succeeded"),
    TRANSACTION_FAILED("transaction.failed"),
    TRANSACTION_REQUIRES_REVIEW("transaction.requires_review"),
    TRANSACTION_REVERSED("transaction.reversed"),
    ACCOUNT_CREATED("account.created"),
    RISK_ALERT_CREATED("risk.alert.created")
}

data class WebhookEndpoint(
    val id: String,
    val workspaceId: String,
    val url: String,
    val description: String,
    val subscribedEvents: Set<WebhookEventType>,
    val signingSecretHash: String,
    val signingSecretPreview: String,
    val enabled: Boolean,
    val createdAt: Long
)

data class WebhookEvent(
    val id: String,
    val workspaceId: String,
    val eventType: WebhookEventType,
    val resourceId: String,
    val payloadJson: String,
    val signatureHeader: String,
    val createdAt: Long
)

data class WebhookDelivery(
    val id: String,
    val workspaceId: String,
    val eventId: String,
    val endpointId: String,
    val endpointUrl: String,
    val responseStatus: Int,
    val responseBody: String,
    val attemptCount: Int,
    val succeeded: Boolean,
    val deliveredAt: Long
)

data class ApiKeyRecord(
    val id: String,
    val workspaceId: String,
    val name: String,
    val keyPrefix: String,
    val keyHashSha256: String,
    val scopes: List<String>,
    val createdByUserId: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val revokedAt: Long? = null
)

data class ApiKeyCreationResult(
    val record: ApiKeyRecord,
    val plaintextKeyOnce: String
)

enum class AuditEventType(val label: String) {
    LOGIN("auth.login"),
    LOGOUT("auth.logout"),
    AUTH_FAILED("auth.failed"),
    ACCOUNT_CREATED("account.created"),
    CUSTOMER_CREATED("customer.created"),
    CUSTOMER_UPDATED("customer.updated"),
    TRANSACTION_CREATED("transaction.created"),
    TRANSACTION_APPROVED("transaction.approved"),
    TRANSACTION_REJECTED("transaction.rejected"),
    TRANSACTION_REVERSED("transaction.reversed"),
    API_KEY_CREATED("api_key.created"),
    API_KEY_REVOKED("api_key.revoked"),
    WEBHOOK_CREATED("webhook.created"),
    WORKSPACE_ROLE_CHANGED("workspace.role_changed"),
    RECONCILIATION_RUN("reconciliation.executed")
}

data class AuditLogEntry(
    val id: String,
    val workspaceId: String,
    val actorUserId: String?,
    val actorEmail: String,
    val eventType: AuditEventType,
    val resourceType: String,
    val resourceId: String,
    val sourceIp: String,
    val metadata: Map<String, String>,
    val createdAt: Long
)

enum class DateRangeFilter(val label: String, val days: Int?) {
    TODAY("Today", 1),
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30),
    LAST_90_DAYS("Last 90 days", 90),
    ALL("All time", null)
}

enum class SimulationScenario(
    val title: String,
    val subtitle: String,
    val expectedOutcome: String
) {
    SUCCEEDED("Successful Payment", "Atomic debit/credit with balanced journal & webhook outbox", "SUCCEEDED"),
    INSUFFICIENT_FUNDS("Insufficient Funds", "Amount exceeds wallet balance; ledger remains untouched", "FAILED (422)"),
    INVALID_ACCOUNT("Invalid Account", "Target account does not exist in this workspace", "FAILED (404)"),
    DUPLICATE_REQUEST("Duplicate Idempotency Key", "Replays same key with altered payload to trigger 409 Conflict", "CONFLICT (409)"),
    ACCOUNT_SUSPENDED("Account Suspended", "Suspended sender/receiver account blocks settlement", "FAILED (422)"),
    RISK_REVIEW_REQUIRED("Risk Review Required", "High-risk velocity/amount holds funds in REQUIRES_REVIEW", "REQUIRES_REVIEW"),
    PENDING_PROCESSING("Pending Payment", "Simulated asynchronous clearing hold with 0 postings", "PROCESSING"),
    SIMULATED_PROCESSING_FAILURE("Simulated Processing Failure", "Clearing rail timeout; rollback guarantees zero partial posts", "FAILED (500)")
}

data class DailyVolumePoint(
    val label: String,
    val volumePaise: Long,
    val succeededCount: Int,
    val failedCount: Int
)

data class OverviewMetrics(
    val totalVolumePaise: Long,
    val totalTransactions: Int,
    val successfulTransactions: Int,
    val failedTransactions: Int,
    val pendingReviewCount: Int,
    val successRatePercent: Double,
    val openRiskAlerts: Int,
    val criticalRiskAlerts: Int,
    val depositCount: Int,
    val transferCount: Int,
    val withdrawalCount: Int,
    val reversalCount: Int,
    val dailySeries: List<DailyVolumePoint>
)

data class TrialBalanceRow(
    val accountId: String,
    val accountNumber: String,
    val accountName: String,
    val category: AccountCategory,
    val normalBalance: NormalBalance,
    val totalDebitsPaise: Long,
    val totalCreditsPaise: Long,
    val netBalancePaise: Long
)

data class ApiRequestResult(
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val responseBody: String,
    val timestamp: Long
)
