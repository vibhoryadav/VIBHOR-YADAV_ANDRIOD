package com.example.engine

import com.example.domain.AccountCategory
import com.example.domain.AccountStatus
import com.example.domain.ApiKeyCreationResult
import com.example.domain.ApiKeyRecord
import com.example.domain.AuditEventType
import com.example.domain.AuditLogEntry
import com.example.domain.Customer
import com.example.domain.CustomerStatus
import com.example.domain.DailyVolumePoint
import com.example.domain.DateRangeFilter
import com.example.domain.IdempotencyRecord
import com.example.domain.JournalEntry
import com.example.domain.LedgerAccount
import com.example.domain.LedgerPosting
import com.example.domain.LifecycleEvent
import com.example.domain.Money
import com.example.domain.NormalBalance
import com.example.domain.OverviewMetrics
import com.example.domain.PostingSide
import com.example.domain.ReconciliationIssue
import com.example.domain.ReconciliationRun
import com.example.domain.ReconciliationStatus
import com.example.domain.RiskAction
import com.example.domain.RiskEvaluation
import com.example.domain.RiskRuleConfig
import com.example.domain.RiskSeverity
import com.example.domain.SimulationScenario
import com.example.domain.Transaction
import com.example.domain.TransactionStatus
import com.example.domain.TransactionType
import com.example.domain.TrialBalanceRow
import com.example.domain.User
import com.example.domain.UserSession
import com.example.domain.WebhookDelivery
import com.example.domain.WebhookEndpoint
import com.example.domain.WebhookEvent
import com.example.domain.WebhookEventType
import com.example.domain.Workspace
import com.example.domain.WorkspaceMember
import com.example.domain.WorkspaceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class EngineSnapshot(
    val version: Long = 0L,
    val workspaces: List<Workspace> = emptyList(),
    val members: List<WorkspaceMember> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val accounts: List<LedgerAccount> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val journals: List<JournalEntry> = emptyList(),
    val postings: List<LedgerPosting> = emptyList(),
    val riskEvaluations: List<RiskEvaluation> = emptyList(),
    val riskRules: List<RiskRuleConfig> = emptyList(),
    val reconciliationRuns: List<ReconciliationRun> = emptyList(),
    val webhookEndpoints: List<WebhookEndpoint> = emptyList(),
    val webhookEvents: List<WebhookEvent> = emptyList(),
    val webhookDeliveries: List<WebhookDelivery> = emptyList(),
    val apiKeys: List<ApiKeyRecord> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList()
)

/**
 * Core Double-Entry Financial Engine, RBAC Guard, Idempotency Store,
 * Transactional Outbox, Reconciliation & Audit Trail.
 *
 * All mutations run under serialized locking and per-account deterministic lock ordering.
 */
class LedgerLabEngine(seedOnInit: Boolean = true) {
    private val globalLock = Mutex()
    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    private val riskEngine = RiskEngine()

    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, UserSession>()
    private val workspaces = mutableMapOf<String, Workspace>()
    private val members = mutableListOf<WorkspaceMember>()
    private val customers = mutableMapOf<String, Customer>()
    private val accounts = mutableMapOf<String, LedgerAccount>()
    private val transactions = mutableMapOf<String, Transaction>()
    private val journals = mutableMapOf<String, JournalEntry>()
    // Append-only immutable posting history
    private val postings = mutableListOf<LedgerPosting>()
    private val idempotencyRecords = mutableMapOf<String, IdempotencyRecord>()
    private val riskEvaluations = mutableMapOf<String, RiskEvaluation>()
    private val enabledRiskRules = riskEngine.defaultRules.map { it.ruleCode }.toMutableSet()
    private val reconciliationRuns = mutableListOf<ReconciliationRun>()
    private val webhookEndpoints = mutableMapOf<String, WebhookEndpoint>()
    private val webhookEndpointSecrets = mutableMapOf<String, String>()
    private val webhookEvents = mutableListOf<WebhookEvent>()
    private val webhookDeliveries = mutableListOf<WebhookDelivery>()
    private val apiKeys = mutableMapOf<String, ApiKeyRecord>()
    private val auditLogs = mutableListOf<AuditLogEntry>()

    private val loginFailureTracker = mutableMapOf<String, MutableList<Long>>()
    private var snapshotVersion = 0L
    private val _snapshot = MutableStateFlow(EngineSnapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

    init {
        if (seedOnInit) {
            SeedDataFactory.seed(this)
            publishSnapshot()
        }
    }

    internal suspend fun resetToDemoSeed() {
        globalLock.withLock {
            users.clear()
            sessions.clear()
            workspaces.clear()
            members.clear()
            customers.clear()
            accounts.clear()
            transactions.clear()
            journals.clear()
            postings.clear()
            idempotencyRecords.clear()
            riskEvaluations.clear()
            reconciliationRuns.clear()
            webhookEndpoints.clear()
            webhookEndpointSecrets.clear()
            webhookEvents.clear()
            webhookDeliveries.clear()
            apiKeys.clear()
            auditLogs.clear()
            loginFailureTracker.clear()
            SeedDataFactory.seed(this)
            publishSnapshot()
        }
    }

    // ---- Internal seeding helpers (used only during initial seed) ----
    internal fun seedUser(user: User) { users[user.id] = user }
    internal fun seedWorkspace(ws: Workspace) { workspaces[ws.id] = ws }
    internal fun seedMember(member: WorkspaceMember) { members += member }
    internal fun seedCustomer(customer: Customer) { customers[customer.id] = customer }
    internal fun seedAccount(account: LedgerAccount) { accounts[account.id] = account }
    internal fun seedWebhookEndpoint(endpoint: WebhookEndpoint, rawSecret: String) {
        webhookEndpoints[endpoint.id] = endpoint
        webhookEndpointSecrets[endpoint.id] = rawSecret
    }
    internal fun seedApiKey(record: ApiKeyRecord) { apiKeys[record.id] = record }

    // ---- Authentication & Sessions ----
    suspend fun registerUser(name: String, email: String, password: String): Pair<User, UserSession> =
        globalLock.withLock {
            val cleanName = name.trim()
            val cleanEmail = email.trim().lowercase(Locale.US)
            if (cleanName.length < 2) throw LedgerLabException.badRequest("Name must be at least 2 characters.")
            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                throw LedgerLabException.badRequest("Enter a valid email address.")
            }
            if (password.length < 8) {
                throw LedgerLabException.badRequest("Password must be at least 8 characters.")
            }
            if (users.values.any { it.email == cleanEmail }) {
                throw LedgerLabException.conflict("An account with this email already exists.")
            }
            val now = System.currentTimeMillis()
            val user = User(
                id = CryptoUtils.generatePrefixedId("usr"),
                name = cleanName,
                email = cleanEmail,
                passwordHash = CryptoUtils.hashPassword(password),
                createdAt = now
            )
            users[user.id] = user
            // Create a personal workspace + grant membership to demo sandbox as Developer
            val personalWs = Workspace(
                id = CryptoUtils.generatePrefixedId("ws"),
                name = "$cleanName's Workspace",
                environmentLabel = "PREVIEW SANDBOX",
                createdAt = now
            )
            workspaces[personalWs.id] = personalWs
            provisionClearingAccountInternal(personalWs.id, now)
            members += WorkspaceMember(
                workspaceId = personalWs.id,
                userId = user.id,
                userName = user.name,
                userEmail = user.email,
                role = WorkspaceRole.OWNER,
                joinedAt = now
            )
            val session = createSessionInternal(user.id, now)
            recordAuditInternal(
                workspaceId = personalWs.id,
                actor = user,
                eventType = AuditEventType.LOGIN,
                resourceType = "session",
                resourceId = session.id,
                metadata = mapOf("method" to "register")
            )
            publishSnapshot()
            user to session
        }

    suspend fun login(email: String, password: String): Pair<User, UserSession> = globalLock.withLock {
        val cleanEmail = email.trim().lowercase(Locale.US)
        val now = System.currentTimeMillis()
        val recentAttempts = loginFailureTracker.getOrPut(cleanEmail) { mutableListOf() }
        recentAttempts.removeAll { now - it > 60_000L }
        if (recentAttempts.size >= 5) {
            throw LedgerLabException.rateLimited("Too many failed login attempts. Try again in 60 seconds.")
        }

        val user = users.values.firstOrNull { it.email == cleanEmail }
        if (user == null || !CryptoUtils.verifyPassword(password, user.passwordHash)) {
            recentAttempts += now
            val defaultWsId = workspaces.keys.firstOrNull() ?: "ws_sandbox"
            recordAuditInternal(
                workspaceId = defaultWsId,
                actor = user,
                actorEmailOverride = cleanEmail,
                eventType = AuditEventType.AUTH_FAILED,
                resourceType = "auth",
                resourceId = cleanEmail,
                metadata = mapOf("reason" to "invalid_credentials")
            )
            publishSnapshot()
            throw LedgerLabException.unauthorized("Invalid email or password.")
        }
        recentAttempts.clear()
        val session = createSessionInternal(user.id, now)
        val wsId = members.firstOrNull { it.userId == user.id }?.workspaceId ?: "ws_sandbox"
        recordAuditInternal(
            workspaceId = wsId,
            actor = user,
            eventType = AuditEventType.LOGIN,
            resourceType = "session",
            resourceId = session.id,
            metadata = mapOf("method" to "password")
        )
        publishSnapshot()
        user to session
    }

    suspend fun loginDemoOwner(): Pair<User, UserSession> = globalLock.withLock {
        val user = users["usr_demo_owner"]
            ?: throw LedgerLabException.notFound("Demo user not initialized.")
        val now = System.currentTimeMillis()
        val session = createSessionInternal(user.id, now)
        recordAuditInternal(
            workspaceId = "ws_sandbox",
            actor = user,
            eventType = AuditEventType.LOGIN,
            resourceType = "session",
            resourceId = session.id,
            metadata = mapOf("method" to "demo_sandbox_entry")
        )
        publishSnapshot()
        user to session
    }

    suspend fun switchDemoUser(userId: String): Pair<User, UserSession> = globalLock.withLock {
        val user = users[userId] ?: throw LedgerLabException.notFound("User not found.")
        val now = System.currentTimeMillis()
        val session = createSessionInternal(user.id, now)
        val wsId = members.firstOrNull { it.userId == user.id }?.workspaceId ?: "ws_sandbox"
        recordAuditInternal(
            workspaceId = wsId,
            actor = user,
            eventType = AuditEventType.LOGIN,
            resourceType = "session",
            resourceId = session.id,
            metadata = mapOf("method" to "actor_switch")
        )
        publishSnapshot()
        user to session
    }

    suspend fun logout(sessionId: String, workspaceId: String) = globalLock.withLock {
        val session = sessions[sessionId] ?: return@withLock
        sessions[sessionId] = session.copy(revokedAt = System.currentTimeMillis())
        val user = users[session.userId]
        recordAuditInternal(
            workspaceId = workspaceId,
            actor = user,
            eventType = AuditEventType.LOGOUT,
            resourceType = "session",
            resourceId = sessionId,
            metadata = emptyMap()
        )
        publishSnapshot()
    }

    fun getAllDemoUsers(): List<User> = users.values.sortedBy { it.createdAt }

    // ---- RBAC & Workspace Isolation Guards ----
    private fun requireMembership(workspaceId: String, userId: String): WorkspaceMember {
        if (!workspaces.containsKey(workspaceId)) {
            throw LedgerLabException.notFound("Workspace '$workspaceId' not found.")
        }
        return members.firstOrNull { it.workspaceId == workspaceId && it.userId == userId }
            ?: throw LedgerLabException.forbidden("Access denied: user is not a member of workspace $workspaceId.")
    }

    private fun requireRole(
        workspaceId: String,
        userId: String,
        predicate: (WorkspaceRole) -> Boolean,
        actionDescription: String
    ): Pair<User, WorkspaceMember> {
        val user = users[userId] ?: throw LedgerLabException.unauthorized("User session is invalid.")
        val membership = requireMembership(workspaceId, userId)
        if (!predicate(membership.role)) {
            throw LedgerLabException.forbidden(
                "Role '${membership.role.label}' is not permitted to $actionDescription."
            )
        }
        return user to membership
    }

    suspend fun updateMemberRole(
        workspaceId: String,
        actorUserId: String,
        targetUserId: String,
        newRole: WorkspaceRole
    ) = globalLock.withLock {
        val (actor, _) = requireRole(
            workspaceId,
            actorUserId,
            { it.canManageMembers() },
            "modify workspace roles"
        )
        val idx = members.indexOfFirst { it.workspaceId == workspaceId && it.userId == targetUserId }
        if (idx < 0) throw LedgerLabException.notFound("Target member not found in workspace.")
        val existing = members[idx]
        members[idx] = existing.copy(role = newRole)
        recordAuditInternal(
            workspaceId = workspaceId,
            actor = actor,
            eventType = AuditEventType.WORKSPACE_ROLE_CHANGED,
            resourceType = "workspace_member",
            resourceId = targetUserId,
            metadata = mapOf("oldRole" to existing.role.name, "newRole" to newRole.name)
        )
        publishSnapshot()
    }

    // ---- Customers & Accounts ----
    suspend fun createCustomer(
        workspaceId: String,
        actorUserId: String,
        fullName: String,
        email: String,
        segment: String
    ): Customer = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "create customers")
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanName.length < 2) throw LedgerLabException.badRequest("Customer name must be at least 2 characters.")
        if (!cleanEmail.contains("@")) throw LedgerLabException.badRequest("Valid customer email is required.")
        val now = System.currentTimeMillis()
        val customer = Customer(
            id = CryptoUtils.generatePrefixedId("cus"),
            workspaceId = workspaceId,
            fullName = cleanName,
            email = cleanEmail,
            referenceCode = "CUST-${(1000 + customers.size + 1)}",
            segment = segment.ifBlank { "Retail Wallet" },
            status = CustomerStatus.ACTIVE,
            riskScore = 10,
            createdAt = now,
            updatedAt = now
        )
        customers[customer.id] = customer
        recordAuditInternal(
            workspaceId = workspaceId,
            actor = actor,
            eventType = AuditEventType.CUSTOMER_CREATED,
            resourceType = "customer",
            resourceId = customer.id,
            metadata = mapOf("referenceCode" to customer.referenceCode)
        )
        publishSnapshot()
        customer
    }

    suspend fun updateCustomer(
        workspaceId: String,
        actorUserId: String,
        customerId: String,
        fullName: String,
        segment: String,
        status: CustomerStatus
    ): Customer = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "update customers")
        val existing = customers[customerId]
            ?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Customer not found in this workspace.")
        if (fullName.trim().length < 2) throw LedgerLabException.badRequest("Name must be at least 2 characters.")
        val updated = existing.copy(
            fullName = fullName.trim(),
            segment = segment.trim().ifBlank { existing.segment },
            status = status,
            updatedAt = System.currentTimeMillis()
        )
        customers[customerId] = updated
        recordAuditInternal(
            workspaceId = workspaceId,
            actor = actor,
            eventType = AuditEventType.CUSTOMER_UPDATED,
            resourceType = "customer",
            resourceId = customerId,
            metadata = mapOf("status" to status.name)
        )
        publishSnapshot()
        updated
    }

    suspend fun createAccount(
        workspaceId: String,
        actorUserId: String,
        customerId: String,
        accountName: String
    ): LedgerAccount = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "create accounts")
        val customer = customers[customerId]
            ?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Customer not found in workspace.")
        val cleanName = accountName.trim().ifBlank { "${customer.fullName} INR Wallet" }
        val now = System.currentTimeMillis()
        var accNumber = CryptoUtils.generateAccountNumber()
        while (accounts.values.any { it.accountNumber == accNumber }) {
            accNumber = CryptoUtils.generateAccountNumber()
        }
        val account = LedgerAccount(
            id = CryptoUtils.generatePrefixedId("acc"),
            workspaceId = workspaceId,
            customerId = customer.id,
            accountNumber = accNumber,
            name = cleanName,
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            currency = Money.CURRENCY_INR,
            status = AccountStatus.ACTIVE,
            isSystemAccount = false,
            cachedBalancePaise = 0L,
            createdAt = now
        )
        accounts[account.id] = account
        emitWebhookEventInternal(
            workspaceId = workspaceId,
            eventType = WebhookEventType.ACCOUNT_CREATED,
            resourceId = account.id,
            payloadJson = """{"event":"account.created","accountId":"${account.id}","accountNumber":"${account.accountNumber}","currency":"INR"}""",
            nowMs = now
        )
        recordAuditInternal(
            workspaceId = workspaceId,
            actor = actor,
            eventType = AuditEventType.ACCOUNT_CREATED,
            resourceType = "ledger_account",
            resourceId = account.id,
            metadata = mapOf("accountNumber" to account.accountNumber, "customerId" to customer.id)
        )
        publishSnapshot()
        account
    }

    suspend fun updateAccountStatus(
        workspaceId: String,
        actorUserId: String,
        accountId: String,
        newStatus: AccountStatus
    ): LedgerAccount = globalLock.withLock {
        requireRole(workspaceId, actorUserId, { it.canReviewRisk() }, "change account status")
        val acc = accounts[accountId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Account not found in workspace.")
        if (acc.isSystemAccount) throw LedgerLabException.financialValidation("System clearing account cannot be modified.")
        val updated = acc.copy(status = newStatus)
        accounts[accountId] = updated
        publishSnapshot()
        updated
    }

    // ---- Balance Derivation from Immutable Ledger Postings ----
    fun computeLedgerDerivedBalancePaise(accountId: String): Long {
        val acc = accounts[accountId] ?: return 0L
        var debits = 0L
        var credits = 0L
        for (p in postings) {
            if (p.accountId == accountId) {
                if (p.side == PostingSide.DEBIT) debits += p.amountPaise
                else credits += p.amountPaise
            }
        }
        return when (acc.normalBalance) {
            NormalBalance.CREDIT -> credits - debits
            NormalBalance.DEBIT -> debits - credits
        }
    }

    fun computeAccountTotals(accountId: String): Pair<Long, Long> {
        var debits = 0L
        var credits = 0L
        for (p in postings) {
            if (p.accountId == accountId) {
                if (p.side == PostingSide.DEBIT) debits += p.amountPaise
                else credits += p.amountPaise
            }
        }
        return debits to credits
    }

    // ---- Core Double-Entry Financial Transactions ----
    suspend fun deposit(
        workspaceId: String,
        actorUserId: String,
        receiverAccountId: String,
        amountPaise: Long,
        reference: String,
        description: String,
        idempotencyKey: String,
        nowOverrideMs: Long? = null
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "execute deposits")
        val now = nowOverrideMs ?: System.currentTimeMillis()
        validatePositiveAmount(amountPaise)
        val cleanKey = validateIdempotencyKey(idempotencyKey)
        val payloadHash = CryptoUtils.sha256Hex("DEPOSIT|$workspaceId|$receiverAccountId|$amountPaise|${reference.trim()}")

        checkIdempotencyInternal(workspaceId, cleanKey, payloadHash)?.let { replayedTx ->
            return@withLock replayedTx.copy(idempotencyReplayed = true)
        }

        val clearing = getOrCreateClearingAccountInternal(workspaceId, now)
        val receiver = accounts[receiverAccountId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Target wallet account not found in workspace.")

        return@withLock withDeterministicAccountLocks(listOf(clearing.id, receiver.id)) {
            if (receiver.status != AccountStatus.ACTIVE) {
                return@withDeterministicAccountLocks recordFailedTransactionInternal(
                    workspaceId, actor, TransactionType.DEPOSIT, amountPaise,
                    clearing.id, receiver.id, reference, description, cleanKey, payloadHash,
                    "Target account ${receiver.accountNumber} is ${receiver.status.label}.", now
                )
            }
            if (receiver.isSystemAccount) {
                throw LedgerLabException.financialValidation("Cannot deposit directly into clearing account.")
            }

            val txId = CryptoUtils.generatePrefixedId("tx")
            val riskEval = evaluateAndStoreRiskInternal(
                workspaceId, txId, TransactionType.DEPOSIT, amountPaise, null, receiver, now
            )

            val journal = postBalancedJournalInternal(
                workspaceId = workspaceId,
                transactionId = txId,
                reference = reference.ifBlank { "DEP-${txId.takeLast(6)}" },
                description = description.ifBlank { "Simulated INR wallet deposit" },
                lines = listOf(
                    Triple(clearing.id, PostingSide.DEBIT, amountPaise),
                    Triple(receiver.id, PostingSide.CREDIT, amountPaise)
                ),
                nowMs = now
            )

            val tx = Transaction(
                id = txId,
                workspaceId = workspaceId,
                type = TransactionType.DEPOSIT,
                status = TransactionStatus.SUCCEEDED,
                amountPaise = amountPaise,
                currency = Money.CURRENCY_INR,
                senderAccountId = clearing.id,
                receiverAccountId = receiver.id,
                reference = journal.reference,
                description = journal.description,
                idempotencyKey = cleanKey,
                riskEvaluationId = riskEval.id,
                riskScore = riskEval.score,
                journalEntryId = journal.id,
                lifecycleTimeline = listOf(
                    LifecycleEvent(TransactionStatus.CREATED, "Deposit request validated", now),
                    LifecycleEvent(TransactionStatus.PROCESSING, "Clearing funds from external settlement", now),
                    LifecycleEvent(TransactionStatus.SUCCEEDED, "Posted balanced journal ${journal.id}", now)
                ),
                createdByUserId = actor.id,
                createdAt = now,
                completedAt = now
            )
            transactions[tx.id] = tx
            storeIdempotencyInternal(workspaceId, actor.id, cleanKey, payloadHash, tx.id, 201, now)
            emitWebhookEventInternal(
                workspaceId, WebhookEventType.TRANSACTION_SUCCEEDED, tx.id,
                buildTxWebhookPayload("transaction.succeeded", tx), now
            )
            recordAuditInternal(
                workspaceId, actor, AuditEventType.TRANSACTION_CREATED, "transaction", tx.id,
                metadata = mapOf("type" to "DEPOSIT", "amountPaise" to amountPaise.toString(), "status" to tx.status.name)
            )
            publishSnapshot()
            tx
        }
    }

    suspend fun transfer(
        workspaceId: String,
        actorUserId: String,
        senderAccountId: String,
        receiverAccountId: String,
        amountPaise: Long,
        reference: String,
        description: String,
        idempotencyKey: String,
        forceReviewHold: Boolean = false,
        nowOverrideMs: Long? = null
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "execute transfers")
        val now = nowOverrideMs ?: System.currentTimeMillis()
        validatePositiveAmount(amountPaise)
        val cleanKey = validateIdempotencyKey(idempotencyKey)
        val payloadHash = CryptoUtils.sha256Hex(
            "TRANSFER|$workspaceId|$senderAccountId|$receiverAccountId|$amountPaise|${reference.trim()}"
        )

        checkIdempotencyInternal(workspaceId, cleanKey, payloadHash)?.let { replayedTx ->
            return@withLock replayedTx.copy(idempotencyReplayed = true)
        }

        if (senderAccountId == receiverAccountId) {
            throw LedgerLabException.financialValidation("Sender and recipient accounts must be different.")
        }
        val sender = accounts[senderAccountId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Sender account not found in workspace.")
        val receiver = accounts[receiverAccountId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Recipient account not found in workspace.")

        return@withLock withDeterministicAccountLocks(listOf(sender.id, receiver.id)) {
            if (sender.currency != receiver.currency) {
                throw LedgerLabException.financialValidation("Currency mismatch between sender and recipient accounts.")
            }
            if (sender.status != AccountStatus.ACTIVE || receiver.status != AccountStatus.ACTIVE) {
                return@withDeterministicAccountLocks recordFailedTransactionInternal(
                    workspaceId, actor, TransactionType.TRANSFER, amountPaise,
                    sender.id, receiver.id, reference, description, cleanKey, payloadHash,
                    "Account is not active (sender=${sender.status.label}, recipient=${receiver.status.label}).",
                    now
                )
            }

            // Recompute authoritative balance inside account locks to prevent concurrent overdraft
            val senderBalance = computeLedgerDerivedBalancePaise(sender.id)
            if (!sender.isSystemAccount && senderBalance < amountPaise) {
                return@withDeterministicAccountLocks recordFailedTransactionInternal(
                    workspaceId, actor, TransactionType.TRANSFER, amountPaise,
                    sender.id, receiver.id, reference, description, cleanKey, payloadHash,
                    "Insufficient available funds (${Money.formatInr(senderBalance)} available, ${Money.formatInr(amountPaise)} requested).",
                    now
                )
            }

            val txId = CryptoUtils.generatePrefixedId("tx")
            val riskEval = evaluateAndStoreRiskInternal(
                workspaceId, txId, TransactionType.TRANSFER, amountPaise, sender, receiver, now
            )

            // Hold for manual review if risk >= 60 (HIGH / CRITICAL) or explicitly requested
            if (forceReviewHold || riskEval.recommendedAction == RiskAction.HOLD_FOR_REVIEW) {
                val tx = Transaction(
                    id = txId,
                    workspaceId = workspaceId,
                    type = TransactionType.TRANSFER,
                    status = TransactionStatus.REQUIRES_REVIEW,
                    amountPaise = amountPaise,
                    currency = Money.CURRENCY_INR,
                    senderAccountId = sender.id,
                    receiverAccountId = receiver.id,
                    reference = reference.ifBlank { "TRF-${txId.takeLast(6)}" },
                    description = description.ifBlank { "Simulated wallet transfer" },
                    idempotencyKey = cleanKey,
                    riskEvaluationId = riskEval.id,
                    riskScore = riskEval.score,
                    journalEntryId = null, // No ledger posting until approved!
                    lifecycleTimeline = listOf(
                        LifecycleEvent(TransactionStatus.CREATED, "Transfer request validated", now),
                        LifecycleEvent(TransactionStatus.PROCESSING, "Risk evaluation executed (score ${riskEval.score})", now),
                        LifecycleEvent(
                            TransactionStatus.REQUIRES_REVIEW,
                            "Held for manual compliance review; 0 ledger postings created",
                            now
                        )
                    ),
                    createdByUserId = actor.id,
                    createdAt = now
                )
                transactions[tx.id] = tx
                storeIdempotencyInternal(workspaceId, actor.id, cleanKey, payloadHash, tx.id, 202, now)
                emitWebhookEventInternal(
                    workspaceId, WebhookEventType.TRANSACTION_REQUIRES_REVIEW, tx.id,
                    buildTxWebhookPayload("transaction.requires_review", tx), now
                )
                emitWebhookEventInternal(
                    workspaceId, WebhookEventType.RISK_ALERT_CREATED, riskEval.id,
                    """{"event":"risk.alert.created","evaluationId":"${riskEval.id}","transactionId":"${tx.id}","score":${riskEval.score},"severity":"${riskEval.severity.name}"}""",
                    now
                )
                recordAuditInternal(
                    workspaceId, actor, AuditEventType.TRANSACTION_CREATED, "transaction", tx.id,
                    metadata = mapOf("type" to "TRANSFER", "status" to "REQUIRES_REVIEW", "riskScore" to riskEval.score.toString())
                )
                publishSnapshot()
                return@withDeterministicAccountLocks tx
            }

            val journal = postBalancedJournalInternal(
                workspaceId = workspaceId,
                transactionId = txId,
                reference = reference.ifBlank { "TRF-${txId.takeLast(6)}" },
                description = description.ifBlank { "Simulated P2P wallet transfer" },
                lines = listOf(
                    Triple(sender.id, PostingSide.DEBIT, amountPaise),
                    Triple(receiver.id, PostingSide.CREDIT, amountPaise)
                ),
                nowMs = now
            )

            val tx = Transaction(
                id = txId,
                workspaceId = workspaceId,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.SUCCEEDED,
                amountPaise = amountPaise,
                currency = Money.CURRENCY_INR,
                senderAccountId = sender.id,
                receiverAccountId = receiver.id,
                reference = journal.reference,
                description = journal.description,
                idempotencyKey = cleanKey,
                riskEvaluationId = riskEval.id,
                riskScore = riskEval.score,
                journalEntryId = journal.id,
                lifecycleTimeline = listOf(
                    LifecycleEvent(TransactionStatus.CREATED, "Transfer request validated", now),
                    LifecycleEvent(TransactionStatus.PROCESSING, "Funds reserved & risk cleared (score ${riskEval.score})", now),
                    LifecycleEvent(TransactionStatus.SUCCEEDED, "Posted balanced journal ${journal.id}", now)
                ),
                createdByUserId = actor.id,
                createdAt = now,
                completedAt = now
            )
            transactions[tx.id] = tx
            storeIdempotencyInternal(workspaceId, actor.id, cleanKey, payloadHash, tx.id, 201, now)
            emitWebhookEventInternal(
                workspaceId, WebhookEventType.TRANSACTION_SUCCEEDED, tx.id,
                buildTxWebhookPayload("transaction.succeeded", tx), now
            )
            recordAuditInternal(
                workspaceId, actor, AuditEventType.TRANSACTION_CREATED, "transaction", tx.id,
                metadata = mapOf("type" to "TRANSFER", "amountPaise" to amountPaise.toString(), "status" to "SUCCEEDED")
            )
            publishSnapshot()
            tx
        }
    }

    suspend fun withdraw(
        workspaceId: String,
        actorUserId: String,
        senderAccountId: String,
        amountPaise: Long,
        reference: String,
        description: String,
        idempotencyKey: String,
        nowOverrideMs: Long? = null
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "execute withdrawals")
        val now = nowOverrideMs ?: System.currentTimeMillis()
        validatePositiveAmount(amountPaise)
        val cleanKey = validateIdempotencyKey(idempotencyKey)
        val payloadHash = CryptoUtils.sha256Hex("WITHDRAWAL|$workspaceId|$senderAccountId|$amountPaise|${reference.trim()}")

        checkIdempotencyInternal(workspaceId, cleanKey, payloadHash)?.let { replayedTx ->
            return@withLock replayedTx.copy(idempotencyReplayed = true)
        }

        val sender = accounts[senderAccountId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Source wallet account not found in workspace.")
        val clearing = getOrCreateClearingAccountInternal(workspaceId, now)

        return@withLock withDeterministicAccountLocks(listOf(sender.id, clearing.id)) {
            if (sender.status != AccountStatus.ACTIVE) {
                return@withDeterministicAccountLocks recordFailedTransactionInternal(
                    workspaceId, actor, TransactionType.WITHDRAWAL, amountPaise,
                    sender.id, clearing.id, reference, description, cleanKey, payloadHash,
                    "Account ${sender.accountNumber} is ${sender.status.label}.", now
                )
            }
            val senderBalance = computeLedgerDerivedBalancePaise(sender.id)
            if (senderBalance < amountPaise) {
                return@withDeterministicAccountLocks recordFailedTransactionInternal(
                    workspaceId, actor, TransactionType.WITHDRAWAL, amountPaise,
                    sender.id, clearing.id, reference, description, cleanKey, payloadHash,
                    "Insufficient available funds (${Money.formatInr(senderBalance)} available, ${Money.formatInr(amountPaise)} requested).",
                    now
                )
            }

            val txId = CryptoUtils.generatePrefixedId("tx")
            val riskEval = evaluateAndStoreRiskInternal(
                workspaceId, txId, TransactionType.WITHDRAWAL, amountPaise, sender, clearing, now
            )

            val journal = postBalancedJournalInternal(
                workspaceId = workspaceId,
                transactionId = txId,
                reference = reference.ifBlank { "WDR-${txId.takeLast(6)}" },
                description = description.ifBlank { "Simulated INR wallet withdrawal" },
                lines = listOf(
                    Triple(sender.id, PostingSide.DEBIT, amountPaise),
                    Triple(clearing.id, PostingSide.CREDIT, amountPaise)
                ),
                nowMs = now
            )

            val tx = Transaction(
                id = txId,
                workspaceId = workspaceId,
                type = TransactionType.WITHDRAWAL,
                status = TransactionStatus.SUCCEEDED,
                amountPaise = amountPaise,
                currency = Money.CURRENCY_INR,
                senderAccountId = sender.id,
                receiverAccountId = clearing.id,
                reference = journal.reference,
                description = journal.description,
                idempotencyKey = cleanKey,
                riskEvaluationId = riskEval.id,
                riskScore = riskEval.score,
                journalEntryId = journal.id,
                lifecycleTimeline = listOf(
                    LifecycleEvent(TransactionStatus.CREATED, "Withdrawal request validated", now),
                    LifecycleEvent(TransactionStatus.PROCESSING, "Funds reserved from wallet", now),
                    LifecycleEvent(TransactionStatus.SUCCEEDED, "Posted balanced journal ${journal.id}", now)
                ),
                createdByUserId = actor.id,
                createdAt = now,
                completedAt = now
            )
            transactions[tx.id] = tx
            storeIdempotencyInternal(workspaceId, actor.id, cleanKey, payloadHash, tx.id, 201, now)
            emitWebhookEventInternal(
                workspaceId, WebhookEventType.TRANSACTION_SUCCEEDED, tx.id,
                buildTxWebhookPayload("transaction.succeeded", tx), now
            )
            recordAuditInternal(
                workspaceId, actor, AuditEventType.TRANSACTION_CREATED, "transaction", tx.id,
                metadata = mapOf("type" to "WITHDRAWAL", "amountPaise" to amountPaise.toString(), "status" to "SUCCEEDED")
            )
            publishSnapshot()
            tx
        }
    }

    // ---- Transaction Lifecycle Transitions (Approve / Reject / Reverse) ----
    suspend fun approveTransaction(
        workspaceId: String,
        actorUserId: String,
        transactionId: String,
        note: String = "Approved after manual risk review"
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canReviewRisk() }, "approve flagged transactions")
        val existing = transactions[transactionId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Transaction not found in workspace.")
        validateStateTransition(existing.status, TransactionStatus.SUCCEEDED)

        val senderId = existing.senderAccountId ?: throw LedgerLabException.financialValidation("Missing sender account.")
        val receiverId = existing.receiverAccountId ?: throw LedgerLabException.financialValidation("Missing receiver account.")
        val sender = accounts[senderId] ?: throw LedgerLabException.notFound("Sender account missing.")
        val receiver = accounts[receiverId] ?: throw LedgerLabException.notFound("Receiver account missing.")
        val now = System.currentTimeMillis()

        return@withLock withDeterministicAccountLocks(listOf(sender.id, receiver.id)) {
            if (!sender.isSystemAccount && computeLedgerDerivedBalancePaise(sender.id) < existing.amountPaise) {
                throw LedgerLabException.financialValidation("Cannot approve: sender balance is now insufficient.")
            }
            val journal = postBalancedJournalInternal(
                workspaceId = workspaceId,
                transactionId = existing.id,
                reference = existing.reference,
                description = "${existing.description} (Review Approved)",
                lines = listOf(
                    Triple(sender.id, PostingSide.DEBIT, existing.amountPaise),
                    Triple(receiver.id, PostingSide.CREDIT, existing.amountPaise)
                ),
                nowMs = now
            )
            val updated = existing.copy(
                status = TransactionStatus.SUCCEEDED,
                journalEntryId = journal.id,
                completedAt = now,
                lifecycleTimeline = existing.lifecycleTimeline + LifecycleEvent(
                    TransactionStatus.SUCCEEDED,
                    "Manual review approved by ${actor.email}; posted ${journal.id}",
                    now
                )
            )
            transactions[updated.id] = updated
            existing.riskEvaluationId?.let { rskId ->
                riskEvaluations[rskId]?.let { eval ->
                    riskEvaluations[rskId] = eval.copy(
                        resolutionNote = note,
                        resolvedByUserId = actor.id,
                        resolvedAt = now
                    )
                }
            }
            emitWebhookEventInternal(
                workspaceId, WebhookEventType.TRANSACTION_SUCCEEDED, updated.id,
                buildTxWebhookPayload("transaction.succeeded", updated), now
            )
            recordAuditInternal(
                workspaceId, actor, AuditEventType.TRANSACTION_APPROVED, "transaction", updated.id,
                metadata = mapOf("journalId" to journal.id, "note" to note)
            )
            publishSnapshot()
            updated
        }
    }

    suspend fun rejectTransaction(
        workspaceId: String,
        actorUserId: String,
        transactionId: String,
        reason: String = "Rejected during manual risk review"
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canReviewRisk() }, "reject flagged transactions")
        val existing = transactions[transactionId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Transaction not found in workspace.")
        validateStateTransition(existing.status, TransactionStatus.FAILED)
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = TransactionStatus.FAILED,
            failureReason = reason,
            completedAt = now,
            lifecycleTimeline = existing.lifecycleTimeline + LifecycleEvent(
                TransactionStatus.FAILED,
                "Rejected by ${actor.email}: $reason",
                now
            )
        )
        transactions[updated.id] = updated
        existing.riskEvaluationId?.let { rskId ->
            riskEvaluations[rskId]?.let { eval ->
                riskEvaluations[rskId] = eval.copy(
                    resolutionNote = reason,
                    resolvedByUserId = actor.id,
                    resolvedAt = now
                )
            }
        }
        emitWebhookEventInternal(
            workspaceId, WebhookEventType.TRANSACTION_FAILED, updated.id,
            buildTxWebhookPayload("transaction.failed", updated), now
        )
        recordAuditInternal(
            workspaceId, actor, AuditEventType.TRANSACTION_REJECTED, "transaction", updated.id,
            metadata = mapOf("reason" to reason)
        )
        publishSnapshot()
        updated
    }

    suspend fun reverseTransaction(
        workspaceId: String,
        actorUserId: String,
        transactionId: String,
        reason: String = "Operator-initiated reversal"
    ): Transaction = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canReviewRisk() }, "reverse settled transactions")
        val original = transactions[transactionId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("Transaction not found in workspace.")
        validateStateTransition(original.status, TransactionStatus.REVERSED)
        val origJournalId = original.journalEntryId
            ?: throw LedgerLabException.financialValidation("Only settled transactions with posted journals can be reversed.")
        val origPostings = postings.filter { it.journalEntryId == origJournalId }
        if (origPostings.size < 2) {
            throw LedgerLabException.internal("Original journal lines missing.")
        }
        val affectedAccountIds = origPostings.map { it.accountId }.distinct()
        val now = System.currentTimeMillis()

        return@withLock withDeterministicAccountLocks(affectedAccountIds) {
            // Create opposing postings: invert DEBIT <-> CREDIT
            val reversalTxId = CryptoUtils.generatePrefixedId("tx")
            val opposingLines = origPostings.map { p ->
                val invertedSide = if (p.side == PostingSide.DEBIT) PostingSide.CREDIT else PostingSide.DEBIT
                Triple(p.accountId, invertedSide, p.amountPaise)
            }
            val reversalJournal = postBalancedJournalInternal(
                workspaceId = workspaceId,
                transactionId = reversalTxId,
                reference = "REV-${original.reference}",
                description = "Reversal of ${original.id}: $reason",
                lines = opposingLines,
                nowMs = now,
                isReversal = true,
                reversesJournalId = origJournalId
            )

            val updatedOriginal = original.copy(
                status = TransactionStatus.REVERSED,
                lifecycleTimeline = original.lifecycleTimeline + LifecycleEvent(
                    TransactionStatus.REVERSED,
                    "Reversed via ${reversalTxId} (${reversalJournal.id})",
                    now
                )
            )
            transactions[updatedOriginal.id] = updatedOriginal

            val reversalTx = Transaction(
                id = reversalTxId,
                workspaceId = workspaceId,
                type = TransactionType.REVERSAL,
                status = TransactionStatus.SUCCEEDED,
                amountPaise = original.amountPaise,
                currency = original.currency,
                senderAccountId = original.receiverAccountId,
                receiverAccountId = original.senderAccountId,
                reference = reversalJournal.reference,
                description = reversalJournal.description,
                idempotencyKey = "rev_${original.id}_$now",
                riskScore = 0,
                journalEntryId = reversalJournal.id,
                reversedTransactionId = original.id,
                lifecycleTimeline = listOf(
                    LifecycleEvent(TransactionStatus.CREATED, "Reversal requested for ${original.id}", now),
                    LifecycleEvent(TransactionStatus.SUCCEEDED, "Opposing journal ${reversalJournal.id} posted", now)
                ),
                createdByUserId = actor.id,
                createdAt = now,
                completedAt = now
            )
            transactions[reversalTx.id] = reversalTx
            emitWebhookEventInternal(
                workspaceId, WebhookEventType.TRANSACTION_REVERSED, original.id,
                buildTxWebhookPayload("transaction.reversed", updatedOriginal), now
            )
            recordAuditInternal(
                workspaceId, actor, AuditEventType.TRANSACTION_REVERSED, "transaction", original.id,
                metadata = mapOf("reversalTxId" to reversalTx.id, "reversalJournalId" to reversalJournal.id, "reason" to reason)
            )
            publishSnapshot()
            reversalTx
        }
    }

    // ---- Payment Simulation Studio Scenarios ----
    suspend fun simulateScenario(
        workspaceId: String,
        actorUserId: String,
        scenario: SimulationScenario,
        senderAccountId: String?,
        receiverAccountId: String?,
        amountPaise: Long
    ): Transaction {
        val key = CryptoUtils.generatePrefixedId("sim_idem")
        return when (scenario) {
            SimulationScenario.SUCCEEDED -> transfer(
                workspaceId, actorUserId,
                senderAccountId ?: throw LedgerLabException.badRequest("Select sender account."),
                receiverAccountId ?: throw LedgerLabException.badRequest("Select recipient account."),
                amountPaise, "SIM-OK", "Simulated successful payment", key
            )
            SimulationScenario.INSUFFICIENT_FUNDS -> {
                val sId = senderAccountId ?: throw LedgerLabException.badRequest("Select sender account.")
                val rId = receiverAccountId ?: throw LedgerLabException.badRequest("Select recipient account.")
                val currentBal = computeLedgerDerivedBalancePaise(sId)
                transfer(
                    workspaceId, actorUserId, sId, rId,
                    currentBal + 50_000L, "SIM-NSF", "Simulated insufficient funds scenario", key
                )
            }
            SimulationScenario.INVALID_ACCOUNT -> {
                globalLock.withLock {
                    val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "run simulations")
                    val now = System.currentTimeMillis()
                    val payloadHash = CryptoUtils.sha256Hex("SIM_INVALID|$workspaceId|$key")
                    val tx = recordFailedTransactionInternal(
                        workspaceId, actor, TransactionType.TRANSFER, amountPaise,
                        senderAccountId, "acc_nonexistent_999", "SIM-INV",
                        "Simulated invalid recipient account", key, payloadHash,
                        "404 Resource Not Found: Account 'acc_nonexistent_999' does not exist in workspace.",
                        now
                    )
                    tx
                }
            }
            SimulationScenario.DUPLICATE_REQUEST -> {
                val sId = senderAccountId ?: throw LedgerLabException.badRequest("Select sender account.")
                val rId = receiverAccountId ?: throw LedgerLabException.badRequest("Select recipient account.")
                // First request succeeds with key, second request with SAME key and DIFFERENT amount throws 409 Conflict!
                transfer(workspaceId, actorUserId, sId, rId, amountPaise, "SIM-IDEM", "First idempotent attempt", key)
                transfer(workspaceId, actorUserId, sId, rId, amountPaise + 100L, "SIM-IDEM", "Mutated duplicate payload", key)
            }
            SimulationScenario.ACCOUNT_SUSPENDED -> {
                globalLock.withLock {
                    val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "run simulations")
                    val now = System.currentTimeMillis()
                    val payloadHash = CryptoUtils.sha256Hex("SIM_SUSPENDED|$workspaceId|$key")
                    recordFailedTransactionInternal(
                        workspaceId, actor, TransactionType.TRANSFER, amountPaise,
                        senderAccountId, receiverAccountId, "SIM-SUSP",
                        "Simulated suspended account block", key, payloadHash,
                        "Account is SUSPENDED by compliance; debit blocked with 0 ledger postings.",
                        now
                    )
                }
            }
            SimulationScenario.RISK_REVIEW_REQUIRED -> {
                val sId = senderAccountId ?: throw LedgerLabException.badRequest("Select sender account.")
                val rId = receiverAccountId ?: throw LedgerLabException.badRequest("Select recipient account.")
                transfer(
                    workspaceId, actorUserId, sId, rId, amountPaise,
                    "SIM-RISK", "Simulated high-risk compliance hold", key,
                    forceReviewHold = true
                )
            }
            SimulationScenario.PENDING_PROCESSING -> {
                globalLock.withLock {
                    val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "run simulations")
                    val now = System.currentTimeMillis()
                    val txId = CryptoUtils.generatePrefixedId("tx")
                    val tx = Transaction(
                        id = txId,
                        workspaceId = workspaceId,
                        type = TransactionType.TRANSFER,
                        status = TransactionStatus.PROCESSING,
                        amountPaise = amountPaise,
                        senderAccountId = senderAccountId,
                        receiverAccountId = receiverAccountId,
                        reference = "SIM-PEND-${txId.takeLast(4)}",
                        description = "Simulated pending settlement hold (0 postings)",
                        idempotencyKey = key,
                        riskScore = 15,
                        journalEntryId = null,
                        lifecycleTimeline = listOf(
                            LifecycleEvent(TransactionStatus.CREATED, "Payment initiated", now),
                            LifecycleEvent(TransactionStatus.PROCESSING, "Awaiting simulated clearing rail ack; balances unchanged", now)
                        ),
                        createdByUserId = actor.id,
                        createdAt = now
                    )
                    transactions[tx.id] = tx
                    emitWebhookEventInternal(
                        workspaceId, WebhookEventType.TRANSACTION_PROCESSING, tx.id,
                        buildTxWebhookPayload("transaction.processing", tx), now
                    )
                    publishSnapshot()
                    tx
                }
            }
            SimulationScenario.SIMULATED_PROCESSING_FAILURE -> {
                globalLock.withLock {
                    val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "run simulations")
                    val now = System.currentTimeMillis()
                    val payloadHash = CryptoUtils.sha256Hex("SIM_FAIL|$workspaceId|$key")
                    recordFailedTransactionInternal(
                        workspaceId, actor, TransactionType.TRANSFER, amountPaise,
                        senderAccountId, receiverAccountId, "SIM-500",
                        "Simulated clearing processor timeout", key, payloadHash,
                        "500 Clearing Rail Timeout: DB transaction rolled back; 0 postings committed.",
                        now
                    )
                }
            }
        }
    }

    // ---- Double-Entry Journal Integrity & Immutability ----
    private fun postBalancedJournalInternal(
        workspaceId: String,
        transactionId: String,
        reference: String,
        description: String,
        lines: List<Triple<String, PostingSide, Long>>,
        nowMs: Long,
        isReversal: Boolean = false,
        reversesJournalId: String? = null
    ): JournalEntry {
        if (lines.size < 2) {
            throw LedgerLabException.financialValidation("Journal entry requires at least 2 posting lines.")
        }
        var totalDebits = 0L
        var totalCredits = 0L
        for ((_, side, amt) in lines) {
            if (amt <= 0L) throw LedgerLabException.financialValidation("Posting line amount must be positive.")
            if (side == PostingSide.DEBIT) totalDebits += amt else totalCredits += amt
        }
        if (totalDebits != totalCredits) {
            throw LedgerLabException.financialValidation(
                "Unbalanced journal rejected: debits=$totalDebits != credits=$totalCredits."
            )
        }

        val journal = JournalEntry(
            id = CryptoUtils.generatePrefixedId("jrn"),
            workspaceId = workspaceId,
            transactionId = transactionId,
            reference = reference,
            description = description,
            currency = Money.CURRENCY_INR,
            effectiveDate = nowMs,
            postedAt = nowMs,
            isReversal = isReversal,
            reversesJournalId = reversesJournalId
        )
        journals[journal.id] = journal

        for ((accountId, side, amt) in lines) {
            val posting = LedgerPosting(
                id = CryptoUtils.generatePrefixedId("pst"),
                workspaceId = workspaceId,
                journalEntryId = journal.id,
                accountId = accountId,
                side = side,
                amountPaise = amt,
                currency = Money.CURRENCY_INR,
                createdAt = nowMs
            )
            postings += posting
            accounts[accountId]?.let { acc ->
                accounts[accountId] = acc.copy(cachedBalancePaise = computeLedgerDerivedBalancePaise(accountId))
            }
        }
        return journal
    }

    /**
     * Enforces immutability of posted ledger entries (for testing and explicit guardrails).
     */
    fun attemptModifyPostedJournal(journalId: String): Nothing {
        throw LedgerLabException.forbidden(
            "Immutable Ledger Violation: Posted journal '$journalId' cannot be modified or deleted. Create a reversal journal instead."
        )
    }

    // ---- Reconciliation Engine ----
    suspend fun runReconciliation(
        workspaceId: String,
        actorUserId: String,
        sourceLabel: String = "Internal Ledger vs Cached Balances",
        externalCsvRows: Map<String, Long>? = null
    ): ReconciliationRun = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canMutateFinancials() }, "run reconciliation")
        val now = System.currentTimeMillis()
        val wsAccounts = accounts.values.filter { it.workspaceId == workspaceId }
        val runId = CryptoUtils.generatePrefixedId("rec")
        val issues = mutableListOf<ReconciliationIssue>()

        var totalDebitBal = 0L
        var totalCreditBal = 0L
        var matched = 0

        for (acc in wsAccounts) {
            val derived = computeLedgerDerivedBalancePaise(acc.id)
            if (acc.normalBalance == NormalBalance.DEBIT) totalDebitBal += derived else totalCreditBal += derived

            val compared = externalCsvRows?.get(acc.accountNumber) ?: acc.cachedBalancePaise
            if (derived != compared) {
                issues += ReconciliationIssue(
                    id = CryptoUtils.generatePrefixedId("ris"),
                    runId = runId,
                    workspaceId = workspaceId,
                    accountId = acc.id,
                    accountNumber = acc.accountNumber,
                    accountName = acc.name,
                    ledgerBalancePaise = derived,
                    comparedBalancePaise = compared,
                    variancePaise = derived - compared,
                    source = if (externalCsvRows != null) "Imported Statement CSV" else "Materialized Cache"
                )
            } else {
                matched++
            }
        }

        // Also check global trial balance equality (Assets debit balance == Liabilities credit balance)
        val run = ReconciliationRun(
            id = runId,
            workspaceId = workspaceId,
            accountsChecked = wsAccounts.size,
            totalDebitBalancePaise = totalDebitBal,
            totalCreditBalancePaise = totalCreditBal,
            matchedAccounts = matched,
            mismatchedAccounts = issues.size,
            status = if (issues.isEmpty() && totalDebitBal == totalCreditBal) {
                ReconciliationStatus.MATCHED
            } else {
                ReconciliationStatus.MISMATCH_DETECTED
            },
            sourceLabel = sourceLabel,
            issues = issues,
            createdByUserId = actor.id,
            createdAt = now
        )
        reconciliationRuns.add(0, run)
        recordAuditInternal(
            workspaceId, actor, AuditEventType.RECONCILIATION_RUN, "reconciliation_run", run.id,
            metadata = mapOf("status" to run.status.name, "mismatches" to issues.size.toString())
        )
        publishSnapshot()
        run
    }

    suspend fun importDemoCsvStatement(
        workspaceId: String,
        actorUserId: String,
        csvContent: String
    ): ReconciliationRun {
        val lines = csvContent.trim().lines().filter { it.isNotBlank() }
        if (lines.size < 2) {
            throw LedgerLabException.badRequest("CSV must contain header 'account_number,balance_inr' and at least 1 row.")
        }
        val header = lines.first().trim().lowercase(Locale.US)
        if (header != "account_number,balance_inr") {
            throw LedgerLabException.badRequest("Invalid CSV header. Expected exact columns: account_number,balance_inr")
        }
        val parsed = mutableMapOf<String, Long>()
        for (i in 1 until lines.size) {
            val cols = lines[i].split(",").map { it.trim() }
            if (cols.size != 2) {
                throw LedgerLabException.badRequest("Row ${i + 1}: expected 2 columns (account_number,balance_inr).")
            }
            val accNum = cols[0]
            val paise = Money.parseInrToPaise(cols[1], requirePositive = false)
                .getOrElse { err -> throw LedgerLabException.badRequest("Row ${i + 1}: ${err.message}") }
            parsed[accNum] = paise
        }
        return runReconciliation(
            workspaceId = workspaceId,
            actorUserId = actorUserId,
            sourceLabel = "Demo CSV Statement (${parsed.size} rows)",
            externalCsvRows = parsed
        )
    }

    // ---- Webhooks & Transactional Outbox ----
    suspend fun createWebhookEndpoint(
        workspaceId: String,
        actorUserId: String,
        url: String,
        description: String,
        events: Set<WebhookEventType>
    ): Pair<WebhookEndpoint, String> = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canManageKeysAndWebhooks() }, "configure webhooks")
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) {
            throw LedgerLabException.badRequest("Webhook URL must start with https:// or http://")
        }
        if (events.isEmpty()) throw LedgerLabException.badRequest("Select at least one webhook event type.")
        val rawSecret = "whsec_${CryptoUtils.generateSecureToken(16)}"
        val now = System.currentTimeMillis()
        val endpoint = WebhookEndpoint(
            id = CryptoUtils.generatePrefixedId("whe"),
            workspaceId = workspaceId,
            url = cleanUrl,
            description = description.trim().ifBlank { "Simulated Event Consumer" },
            subscribedEvents = events,
            signingSecretHash = CryptoUtils.sha256Hex(rawSecret),
            signingSecretPreview = "${rawSecret.take(10)}••••${rawSecret.takeLast(4)}",
            enabled = true,
            createdAt = now
        )
        webhookEndpoints[endpoint.id] = endpoint
        webhookEndpointSecrets[endpoint.id] = rawSecret
        recordAuditInternal(
            workspaceId, actor, AuditEventType.WEBHOOK_CREATED, "webhook_endpoint", endpoint.id,
            metadata = mapOf("url" to cleanUrl, "eventsCount" to events.size.toString())
        )
        publishSnapshot()
        endpoint to rawSecret
    }

    suspend fun retryWebhookDelivery(workspaceId: String, actorUserId: String, deliveryId: String): WebhookDelivery =
        globalLock.withLock {
            requireRole(workspaceId, actorUserId, { it.canManageKeysAndWebhooks() }, "retry webhook deliveries")
            val idx = webhookDeliveries.indexOfFirst { it.id == deliveryId && it.workspaceId == workspaceId }
            if (idx < 0) throw LedgerLabException.notFound("Delivery record not found.")
            val existing = webhookDeliveries[idx]
            val retried = existing.copy(
                attemptCount = existing.attemptCount + 1,
                responseStatus = 200,
                responseBody = """{"received":true,"simulated":true,"attempt":${existing.attemptCount + 1}}""",
                succeeded = true,
                deliveredAt = System.currentTimeMillis()
            )
            webhookDeliveries[idx] = retried
            publishSnapshot()
            retried
        }

    fun getWebhookSigningSecretForVerification(workspaceId: String, endpointId: String): String? {
        val ep = webhookEndpoints[endpointId] ?: return null
        if (ep.workspaceId != workspaceId) return null
        return webhookEndpointSecrets[endpointId]
    }

    // ---- API Keys ----
    suspend fun createApiKey(
        workspaceId: String,
        actorUserId: String,
        name: String,
        scopes: List<String>
    ): ApiKeyCreationResult = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canManageKeysAndWebhooks() }, "create API keys")
        val cleanName = name.trim()
        if (cleanName.length < 2) throw LedgerLabException.badRequest("API key name must be at least 2 characters.")
        val rawKey = "ll_test_${CryptoUtils.generateSecureToken(18)}"
        val record = ApiKeyRecord(
            id = CryptoUtils.generatePrefixedId("key"),
            workspaceId = workspaceId,
            name = cleanName,
            keyPrefix = rawKey.take(14),
            keyHashSha256 = CryptoUtils.sha256Hex(rawKey),
            scopes = scopes.ifEmpty { listOf("read:ledger", "write:transfers") },
            createdByUserId = actor.id,
            createdAt = System.currentTimeMillis()
        )
        apiKeys[record.id] = record
        recordAuditInternal(
            workspaceId, actor, AuditEventType.API_KEY_CREATED, "api_key", record.id,
            metadata = mapOf("name" to cleanName, "prefix" to record.keyPrefix)
        )
        publishSnapshot()
        ApiKeyCreationResult(record, rawKey)
    }

    suspend fun revokeApiKey(workspaceId: String, actorUserId: String, keyId: String) = globalLock.withLock {
        val (actor, _) = requireRole(workspaceId, actorUserId, { it.canManageKeysAndWebhooks() }, "revoke API keys")
        val record = apiKeys[keyId]?.takeIf { it.workspaceId == workspaceId }
            ?: throw LedgerLabException.notFound("API key not found in workspace.")
        if (record.revokedAt != null) throw LedgerLabException.conflict("API key is already revoked.")
        apiKeys[keyId] = record.copy(revokedAt = System.currentTimeMillis())
        recordAuditInternal(
            workspaceId, actor, AuditEventType.API_KEY_REVOKED, "api_key", keyId,
            metadata = mapOf("prefix" to record.keyPrefix)
        )
        publishSnapshot()
    }

    fun authenticateApiKey(rawKey: String, workspaceId: String): ApiKeyRecord {
        val hash = CryptoUtils.sha256Hex(rawKey.trim())
        val record = apiKeys.values.firstOrNull { it.keyHashSha256 == hash && it.workspaceId == workspaceId }
            ?: throw LedgerLabException.unauthorized("Invalid API key.")
        if (record.revokedAt != null) {
            throw LedgerLabException.unauthorized("API key '${record.keyPrefix}...' has been revoked.")
        }
        apiKeys[record.id] = record.copy(lastUsedAt = System.currentTimeMillis())
        publishSnapshot()
        return record
    }

    suspend fun toggleRiskRule(workspaceId: String, actorUserId: String, ruleCode: String, enabled: Boolean) =
        globalLock.withLock {
            requireRole(workspaceId, actorUserId, { it.canReviewRisk() }, "configure risk rules")
            if (enabled) enabledRiskRules += ruleCode else enabledRiskRules -= ruleCode
            publishSnapshot()
        }

    // ---- Queries & Analytics ----
    fun getOverviewMetrics(workspaceId: String, dateRange: DateRangeFilter): OverviewMetrics {
        val now = System.currentTimeMillis()
        val cutoff = dateRange.days?.let { now - it * 24L * 60L * 60L * 1000L } ?: 0L
        val wsTx = transactions.values.filter { it.workspaceId == workspaceId && it.createdAt >= cutoff }
        val succeeded = wsTx.count { it.status == TransactionStatus.SUCCEEDED }
        val failed = wsTx.count { it.status == TransactionStatus.FAILED }
        val pendingReview = wsTx.count { it.status == TransactionStatus.REQUIRES_REVIEW }
        val settledVol = wsTx.filter { it.status == TransactionStatus.SUCCEEDED || it.status == TransactionStatus.REVERSED }
            .sumOf { it.amountPaise }
        val finishedCount = succeeded + failed
        val successRate = if (finishedCount == 0) 100.0 else (succeeded.toDouble() / finishedCount.toDouble()) * 100.0

        val openAlerts = riskEvaluations.values.count {
            it.workspaceId == workspaceId && it.score >= 30 && it.resolvedAt == null
        }
        val criticalAlerts = riskEvaluations.values.count {
            it.workspaceId == workspaceId && it.severity == RiskSeverity.CRITICAL && it.resolvedAt == null
        }

        val dayFormat = SimpleDateFormat("dd MMM", Locale.US)
        val dayBuckets = linkedMapOf<String, MutableList<Transaction>>()
        for (i in 6 downTo 0) {
            val label = dayFormat.format(Date(now - i * 24L * 60L * 60L * 1000L))
            dayBuckets[label] = mutableListOf()
        }
        for (tx in wsTx) {
            val label = dayFormat.format(Date(tx.createdAt))
            dayBuckets[label]?.add(tx)
        }
        val series = dayBuckets.map { (label, list) ->
            DailyVolumePoint(
                label = label,
                volumePaise = list.filter { it.status == TransactionStatus.SUCCEEDED }.sumOf { it.amountPaise },
                succeededCount = list.count { it.status == TransactionStatus.SUCCEEDED },
                failedCount = list.count { it.status == TransactionStatus.FAILED }
            )
        }

        return OverviewMetrics(
            totalVolumePaise = settledVol,
            totalTransactions = wsTx.size,
            successfulTransactions = succeeded,
            failedTransactions = failed,
            pendingReviewCount = pendingReview,
            successRatePercent = successRate,
            openRiskAlerts = openAlerts,
            criticalRiskAlerts = criticalAlerts,
            depositCount = wsTx.count { it.type == TransactionType.DEPOSIT },
            transferCount = wsTx.count { it.type == TransactionType.TRANSFER },
            withdrawalCount = wsTx.count { it.type == TransactionType.WITHDRAWAL },
            reversalCount = wsTx.count { it.type == TransactionType.REVERSAL },
            dailySeries = series
        )
    }

    fun getTrialBalance(workspaceId: String): List<TrialBalanceRow> {
        return accounts.values
            .filter { it.workspaceId == workspaceId }
            .sortedBy { if (it.isSystemAccount) 0 else 1 }
            .map { acc ->
                val (debits, credits) = computeAccountTotals(acc.id)
                val net = if (acc.normalBalance == NormalBalance.CREDIT) credits - debits else debits - credits
                TrialBalanceRow(
                    accountId = acc.id,
                    accountNumber = acc.accountNumber,
                    accountName = acc.name,
                    category = acc.category,
                    normalBalance = acc.normalBalance,
                    totalDebitsPaise = debits,
                    totalCreditsPaise = credits,
                    netBalancePaise = net
                )
            }
    }

    // ---- Internal Helpers ----
    private fun createSessionInternal(userId: String, now: Long): UserSession {
        val rawToken = CryptoUtils.generateSecureToken(24)
        val session = UserSession(
            id = CryptoUtils.generatePrefixedId("ses"),
            userId = userId,
            tokenPreview = "${rawToken.take(8)}...",
            createdAt = now,
            expiresAt = now + 24 * 60 * 60 * 1000L
        )
        sessions[session.id] = session
        return session
    }

    private fun provisionClearingAccountInternal(workspaceId: String, now: Long): LedgerAccount {
        val existing = accounts.values.firstOrNull { it.workspaceId == workspaceId && it.isSystemAccount }
        if (existing != null) return existing
        val clearing = LedgerAccount(
            id = "acc_clearing_${workspaceId}",
            workspaceId = workspaceId,
            customerId = null,
            accountNumber = "SYS-CLR-${workspaceId.takeLast(4).uppercase(Locale.US)}",
            name = "Simulated External Clearing Account",
            category = AccountCategory.ASSET,
            normalBalance = NormalBalance.DEBIT,
            currency = Money.CURRENCY_INR,
            status = AccountStatus.ACTIVE,
            isSystemAccount = true,
            cachedBalancePaise = 0L,
            createdAt = now
        )
        accounts[clearing.id] = clearing
        return clearing
    }

    private fun getOrCreateClearingAccountInternal(workspaceId: String, now: Long): LedgerAccount {
        return accounts.values.firstOrNull { it.workspaceId == workspaceId && it.isSystemAccount }
            ?: provisionClearingAccountInternal(workspaceId, now)
    }

    private suspend fun <T> withDeterministicAccountLocks(accountIds: List<String>, block: () -> T): T {
        val sorted = accountIds.distinct().sorted()
        val acquired = mutableListOf<Mutex>()
        try {
            for (id in sorted) {
                val lock = accountLocks.getOrPut(id) { Mutex() }
                lock.lock()
                acquired += lock
            }
            return block()
        } finally {
            for (lock in acquired.asReversed()) {
                lock.unlock()
            }
        }
    }

    private fun validatePositiveAmount(amountPaise: Long) {
        if (amountPaise <= 0L) {
            throw LedgerLabException.financialValidation("Transaction amount must be greater than ₹0.00.")
        }
    }

    private fun validateIdempotencyKey(key: String): String {
        val clean = key.trim()
        if (clean.length < 4) {
            throw LedgerLabException.badRequest("Idempotency-Key header (min 4 chars) is required.")
        }
        return clean
    }

    private fun checkIdempotencyInternal(workspaceId: String, key: String, payloadHash: String): Transaction? {
        val compositeKey = "$workspaceId::$key"
        val existing = idempotencyRecords[compositeKey] ?: return null
        if (existing.payloadHash != payloadHash) {
            throw LedgerLabException.conflict(
                "Idempotency conflict (409): Idempotency-Key '$key' was previously used with a different request payload."
            )
        }
        return transactions[existing.transactionId]
    }

    private fun storeIdempotencyInternal(
        workspaceId: String,
        actorId: String,
        key: String,
        payloadHash: String,
        txId: String,
        status: Int,
        now: Long
    ) {
        idempotencyRecords["$workspaceId::$key"] = IdempotencyRecord(
            workspaceId = workspaceId,
            actorId = actorId,
            key = key,
            payloadHash = payloadHash,
            transactionId = txId,
            responseStatus = status,
            createdAt = now
        )
    }

    private fun validateStateTransition(from: TransactionStatus, to: TransactionStatus) {
        val allowed = when (from) {
            TransactionStatus.CREATED -> setOf(TransactionStatus.PROCESSING, TransactionStatus.FAILED)
            TransactionStatus.PROCESSING -> setOf(TransactionStatus.SUCCEEDED, TransactionStatus.FAILED, TransactionStatus.REQUIRES_REVIEW)
            TransactionStatus.REQUIRES_REVIEW -> setOf(TransactionStatus.SUCCEEDED, TransactionStatus.FAILED)
            TransactionStatus.SUCCEEDED -> setOf(TransactionStatus.REVERSED)
            TransactionStatus.FAILED, TransactionStatus.REVERSED -> emptySet()
        }
        if (to !in allowed) {
            throw LedgerLabException.financialValidation("Invalid transaction state transition: ${from.name} -> ${to.name}.")
        }
    }

    private fun evaluateAndStoreRiskInternal(
        workspaceId: String,
        txId: String,
        type: TransactionType,
        amountPaise: Long,
        sender: LedgerAccount?,
        receiver: LedgerAccount?,
        now: Long
    ): RiskEvaluation {
        val recentWsTx = transactions.values.filter { it.workspaceId == workspaceId }
        val eval = riskEngine.evaluate(
            workspaceId = workspaceId,
            transactionId = txId,
            type = type,
            amountPaise = amountPaise,
            senderAccount = sender,
            receiverAccount = receiver,
            recentTransactions = recentWsTx,
            enabledRuleCodes = enabledRiskRules,
            nowMs = now
        )
        riskEvaluations[eval.id] = eval
        return eval
    }

    private fun recordFailedTransactionInternal(
        workspaceId: String,
        actor: User,
        type: TransactionType,
        amountPaise: Long,
        senderId: String?,
        receiverId: String?,
        reference: String,
        description: String,
        idempotencyKey: String,
        payloadHash: String,
        failureReason: String,
        now: Long
    ): Transaction {
        val txId = CryptoUtils.generatePrefixedId("tx")
        val tx = Transaction(
            id = txId,
            workspaceId = workspaceId,
            type = type,
            status = TransactionStatus.FAILED,
            amountPaise = amountPaise,
            senderAccountId = senderId,
            receiverAccountId = receiverId,
            reference = reference.ifBlank { "FAIL-${txId.takeLast(6)}" },
            description = description.ifBlank { "Failed simulated operation" },
            failureReason = failureReason,
            idempotencyKey = idempotencyKey,
            riskScore = 0,
            journalEntryId = null, // Failed transactions NEVER create posted journal entries
            lifecycleTimeline = listOf(
                LifecycleEvent(TransactionStatus.CREATED, "Request validated", now),
                LifecycleEvent(TransactionStatus.FAILED, "$failureReason (0 ledger postings)", now)
            ),
            createdByUserId = actor.id,
            createdAt = now,
            completedAt = now
        )
        transactions[tx.id] = tx
        storeIdempotencyInternal(workspaceId, actor.id, idempotencyKey, payloadHash, tx.id, 422, now)
        emitWebhookEventInternal(
            workspaceId, WebhookEventType.TRANSACTION_FAILED, tx.id,
            buildTxWebhookPayload("transaction.failed", tx), now
        )
        recordAuditInternal(
            workspaceId, actor, AuditEventType.TRANSACTION_CREATED, "transaction", tx.id,
            metadata = mapOf("type" to type.name, "status" to "FAILED", "reason" to failureReason)
        )
        publishSnapshot()
        return tx
    }

    private fun emitWebhookEventInternal(
        workspaceId: String,
        eventType: WebhookEventType,
        resourceId: String,
        payloadJson: String,
        nowMs: Long
    ) {
        val matchingEndpoints = webhookEndpoints.values.filter {
            it.workspaceId == workspaceId && it.enabled && eventType in it.subscribedEvents
        }
        val primarySecret = matchingEndpoints.firstOrNull()?.let { webhookEndpointSecrets[it.id] }
            ?: "whsec_ledgerlab_sandbox_outbox"
        val sigHeader = CryptoUtils.signWebhookPayload(primarySecret, nowMs / 1000L, payloadJson)
        val event = WebhookEvent(
            id = CryptoUtils.generatePrefixedId("evt"),
            workspaceId = workspaceId,
            eventType = eventType,
            resourceId = resourceId,
            payloadJson = payloadJson,
            signatureHeader = sigHeader,
            createdAt = nowMs
        )
        webhookEvents.add(0, event)

        for (ep in matchingEndpoints) {
            val simulatedFail = ep.url.contains("fail", ignoreCase = true)
            webhookDeliveries.add(
                0,
                WebhookDelivery(
                    id = CryptoUtils.generatePrefixedId("dlv"),
                    workspaceId = workspaceId,
                    eventId = event.id,
                    endpointId = ep.id,
                    endpointUrl = ep.url,
                    responseStatus = if (simulatedFail) 503 else 200,
                    responseBody = if (simulatedFail) """{"error":"Simulated 503 downstream timeout"}""" else """{"received":true,"simulated":true}""",
                    attemptCount = 1,
                    succeeded = !simulatedFail,
                    deliveredAt = nowMs
                )
            )
        }
    }

    private fun buildTxWebhookPayload(eventName: String, tx: Transaction): String {
        return """{"event":"$eventName","transactionId":"${tx.id}","type":"${tx.type.name}","status":"${tx.status.name}","amountPaise":${tx.amountPaise},"currency":"${tx.currency}"}"""
    }

    private fun recordAuditInternal(
        workspaceId: String,
        actor: User?,
        eventType: AuditEventType,
        resourceType: String,
        resourceId: String,
        actorEmailOverride: String? = null,
        metadata: Map<String, String>
    ) {
        auditLogs.add(
            0,
            AuditLogEntry(
                id = CryptoUtils.generatePrefixedId("aud"),
                workspaceId = workspaceId,
                actorUserId = actor?.id,
                actorEmail = actorEmailOverride ?: actor?.email ?: "system@ledgerlab.dev",
                eventType = eventType,
                resourceType = resourceType,
                resourceId = resourceId,
                sourceIp = "10.0.2.15",
                metadata = metadata,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun publishSnapshot() {
        snapshotVersion++
        _snapshot.value = EngineSnapshot(
            version = snapshotVersion,
            workspaces = workspaces.values.sortedBy { it.createdAt },
            members = members.toList(),
            customers = customers.values.sortedByDescending { it.createdAt },
            accounts = accounts.values.sortedWith(compareBy<LedgerAccount> { !it.isSystemAccount }.thenByDescending { it.createdAt }),
            transactions = transactions.values.sortedByDescending { it.createdAt },
            journals = journals.values.sortedByDescending { it.postedAt },
            postings = postings.toList(),
            riskEvaluations = riskEvaluations.values.sortedByDescending { it.evaluatedAt },
            riskRules = riskEngine.defaultRules.map { it.copy(enabled = it.ruleCode in enabledRiskRules) },
            reconciliationRuns = reconciliationRuns.toList(),
            webhookEndpoints = webhookEndpoints.values.sortedByDescending { it.createdAt },
            webhookEvents = webhookEvents.toList(),
            webhookDeliveries = webhookDeliveries.toList(),
            apiKeys = apiKeys.values.sortedByDescending { it.createdAt },
            auditLogs = auditLogs.toList()
        )
    }
}
