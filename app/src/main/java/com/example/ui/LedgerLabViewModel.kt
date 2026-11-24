package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.AccountStatus
import com.example.domain.ApiKeyCreationResult
import com.example.domain.ApiRequestResult
import com.example.domain.CustomerStatus
import com.example.domain.DateRangeFilter
import com.example.domain.Money
import com.example.domain.SimulationScenario
import com.example.domain.User
import com.example.domain.UserSession
import com.example.domain.WebhookEventType
import com.example.domain.WorkspaceRole
import com.example.engine.ApiRouter
import com.example.engine.CryptoUtils
import com.example.engine.EngineSnapshot
import com.example.engine.LedgerLabEngine
import com.example.engine.LedgerLabException
import com.example.network.LedgerLabRemoteClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavDestination(
    val route: String,
    val label: String,
    val group: NavGroup
) {
    OVERVIEW("overview", "Overview", NavGroup.WORKSPACE),
    ACCOUNTS("accounts", "Accounts", NavGroup.WORKSPACE),
    CUSTOMERS("customers", "Customers", NavGroup.WORKSPACE),
    TRANSACTIONS("transactions", "Transactions", NavGroup.WORKSPACE),
    TRANSFERS("transfers", "Transfers & Sim", NavGroup.WORKSPACE),

    LEDGER("ledger", "Ledger", NavGroup.FINANCIAL_SYSTEMS),
    RECONCILIATION("reconciliation", "Reconciliation", NavGroup.FINANCIAL_SYSTEMS),
    RISK_FRAUD("risk_fraud", "Risk & Fraud", NavGroup.FINANCIAL_SYSTEMS),

    API_PLAYGROUND("api_playground", "API Playground", NavGroup.DEVELOPER_TOOLS),
    API_DOCS("api_docs", "API Documentation", NavGroup.DEVELOPER_TOOLS),
    WEBHOOKS("webhooks", "Webhooks", NavGroup.DEVELOPER_TOOLS),
    API_KEYS("api_keys", "API Keys", NavGroup.DEVELOPER_TOOLS),

    AUDIT_LOGS("audit_logs", "Audit Logs", NavGroup.MANAGEMENT),
    SETTINGS("settings", "Settings", NavGroup.MANAGEMENT)
}

enum class NavGroup(val title: String) {
    WORKSPACE("WORKSPACE"),
    FINANCIAL_SYSTEMS("FINANCIAL SYSTEMS"),
    DEVELOPER_TOOLS("DEVELOPER TOOLS"),
    MANAGEMENT("MANAGEMENT")
}

data class BackendConnectionState(
    val baseUrl: String = "http://10.0.2.2:3000/api",
    val isConnected: Boolean = false,
    val isChecking: Boolean = false,
    val statusMessage: String = "PostgreSQL backend not attached — running in non-persistent Preview Mode"
)

data class AppUiState(
    val currentUser: User? = null,
    val currentSession: UserSession? = null,
    val activeWorkspaceId: String = "ws_sandbox",
    val activeDestination: NavDestination = NavDestination.OVERVIEW,
    val selectedDateRange: DateRangeFilter = DateRangeFilter.LAST_30_DAYS,
    val globalSearch: String = "",
    val sidebarCollapsed: Boolean = false,
    val selectedAccountId: String? = null,
    val selectedCustomerId: String? = null,
    val selectedTransactionId: String? = null,
    val selectedJournalId: String? = null,
    val latestApiResult: ApiRequestResult? = null,
    val apiHistory: List<ApiRequestResult> = emptyList(),
    val createdApiKeyOnce: ApiKeyCreationResult? = null,
    val createdWebhookSecretOnce: Pair<String, String>? = null,
    val backendConnection: BackendConnectionState = BackendConnectionState(),
    val authError: String? = null,
    val isBusy: Boolean = false
)

class LedgerLabViewModel(
    val engine: LedgerLabEngine = LedgerLabEngine(seedOnInit = true)
) : ViewModel() {

    private val apiRouter = ApiRouter(engine)
    val snapshot: StateFlow<EngineSnapshot> = engine.snapshot

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<Pair<String, Boolean>>()
    val toastEvents: SharedFlow<Pair<String, Boolean>> = _toastEvents.asSharedFlow()

    private fun emitToast(message: String, isError: Boolean = false) {
        viewModelScope.launch { _toastEvents.emit(message to isError) }
    }

    // ---- Auth ----
    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, authError = null) }
            try {
                val (user, session) = engine.login(email, password)
                val firstWs = engine.snapshot.value.members.firstOrNull { it.userId == user.id }?.workspaceId ?: "ws_sandbox"
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        currentSession = session,
                        activeWorkspaceId = firstWs,
                        isBusy = false,
                        authError = null
                    )
                }
            } catch (ex: LedgerLabException) {
                _uiState.update { it.copy(isBusy = false, authError = ex.message) }
            }
        }
    }

    fun registerUser(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, authError = null) }
            try {
                val (user, session) = engine.registerUser(name, email, password)
                val firstWs = engine.snapshot.value.members.firstOrNull { it.userId == user.id }?.workspaceId ?: "ws_sandbox"
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        currentSession = session,
                        activeWorkspaceId = firstWs,
                        isBusy = false,
                        authError = null
                    )
                }
            } catch (ex: LedgerLabException) {
                _uiState.update { it.copy(isBusy = false, authError = ex.message) }
            }
        }
    }

    fun enterDemoSandbox() {
        viewModelScope.launch {
            val (user, session) = engine.loginDemoOwner()
            _uiState.update {
                it.copy(
                    currentUser = user,
                    currentSession = session,
                    activeWorkspaceId = "ws_sandbox",
                    activeDestination = NavDestination.OVERVIEW,
                    authError = null
                )
            }
        }
    }

    fun switchDemoActor(userId: String) {
        viewModelScope.launch {
            try {
                val (user, session) = engine.switchDemoUser(userId)
                val allowedWorkspaces = engine.snapshot.value.members
                    .filter { it.userId == user.id }
                    .map { it.workspaceId }
                val nextWs = if (_uiState.value.activeWorkspaceId in allowedWorkspaces) {
                    _uiState.value.activeWorkspaceId
                } else {
                    allowedWorkspaces.firstOrNull() ?: "ws_sandbox"
                }
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        currentSession = session,
                        activeWorkspaceId = nextWs
                    )
                }
                emitToast("Switched active actor to ${user.name}")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun logout() {
        val state = _uiState.value
        val sessionId = state.currentSession?.id
        viewModelScope.launch {
            if (sessionId != null) {
                engine.logout(sessionId, state.activeWorkspaceId)
            }
            _uiState.update {
                AppUiState(backendConnection = it.backendConnection)
            }
        }
    }

    // ---- Navigation & Filters ----
    fun navigateTo(destination: NavDestination) {
        _uiState.update {
            it.copy(
                activeDestination = destination,
                selectedAccountId = null,
                selectedCustomerId = null,
                selectedTransactionId = null,
                selectedJournalId = null
            )
        }
    }

    fun selectWorkspace(workspaceId: String) {
        val userId = _uiState.value.currentUser?.id ?: return
        val hasAccess = engine.snapshot.value.members.any { it.workspaceId == workspaceId && it.userId == userId }
        if (!hasAccess) {
            emitToast("403 Forbidden: Your account is not a member of that workspace.", isError = true)
            return
        }
        _uiState.update {
            it.copy(
                activeWorkspaceId = workspaceId,
                selectedAccountId = null,
                selectedCustomerId = null,
                selectedTransactionId = null,
                selectedJournalId = null
            )
        }
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
    }

    fun setDateRange(range: DateRangeFilter) {
        _uiState.update { it.copy(selectedDateRange = range) }
    }

    fun setGlobalSearch(query: String) {
        _uiState.update { it.copy(globalSearch = query) }
    }

    fun openAccountDetail(accountId: String?) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

    fun openCustomerDetail(customerId: String?) {
        _uiState.update { it.copy(selectedCustomerId = customerId) }
    }

    fun openTransactionDetail(transactionId: String?) {
        _uiState.update { it.copy(selectedTransactionId = transactionId) }
    }

    fun openJournalDetail(journalId: String?) {
        _uiState.update { it.copy(selectedJournalId = journalId) }
    }

    // ---- Domain Actions ----
    fun createCustomer(name: String, email: String, segment: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val c = engine.createCustomer(st.activeWorkspaceId, actorId, name, email, segment)
                emitToast("Created customer ${c.fullName} (${c.referenceCode})")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun updateCustomer(customerId: String, name: String, segment: String, status: CustomerStatus) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.updateCustomer(st.activeWorkspaceId, actorId, customerId, name, segment, status)
                emitToast("Updated customer profile")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun createAccount(customerId: String, accountName: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val acc = engine.createAccount(st.activeWorkspaceId, actorId, customerId, accountName)
                emitToast("Created virtual account ${acc.accountNumber}")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun updateAccountStatus(accountId: String, status: AccountStatus) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.updateAccountStatus(st.activeWorkspaceId, actorId, accountId, status)
                emitToast("Account status updated to ${status.label}")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun executeDeposit(accountId: String, amountInr: String, reference: String, description: String, onDone: (String) -> Unit = {}) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            val paise = Money.parseInrToPaise(amountInr).getOrElse {
                emitToast(it.message ?: "Invalid INR amount", isError = true)
                return@launch
            }
            try {
                val tx = engine.deposit(
                    st.activeWorkspaceId, actorId, accountId, paise, reference, description,
                    CryptoUtils.generatePrefixedId("idem")
                )
                emitToast("Deposit ${tx.status.label}: ${Money.formatInr(tx.amountPaise)}")
                onDone(tx.id)
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun executeTransfer(
        senderAccountId: String,
        receiverAccountId: String,
        amountInr: String,
        reference: String,
        description: String,
        idempotencyKey: String,
        onDone: (String) -> Unit = {}
    ) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            val paise = Money.parseInrToPaise(amountInr).getOrElse {
                emitToast(it.message ?: "Invalid INR amount", isError = true)
                return@launch
            }
            try {
                val tx = engine.transfer(
                    st.activeWorkspaceId, actorId, senderAccountId, receiverAccountId, paise,
                    reference, description, idempotencyKey
                )
                val prefix = if (tx.idempotencyReplayed) "[Idempotent Replay] " else ""
                emitToast("${prefix}Transfer ${tx.status.label}: ${tx.id}", isError = tx.status.name == "FAILED")
                onDone(tx.id)
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun executeWithdrawal(accountId: String, amountInr: String, reference: String, description: String, onDone: (String) -> Unit = {}) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            val paise = Money.parseInrToPaise(amountInr).getOrElse {
                emitToast(it.message ?: "Invalid INR amount", isError = true)
                return@launch
            }
            try {
                val tx = engine.withdraw(
                    st.activeWorkspaceId, actorId, accountId, paise, reference, description,
                    CryptoUtils.generatePrefixedId("idem")
                )
                emitToast("Withdrawal ${tx.status.label}: ${Money.formatInr(tx.amountPaise)}", isError = tx.status.name == "FAILED")
                onDone(tx.id)
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun runSimulation(scenario: SimulationScenario, senderId: String?, receiverId: String?, amountInr: String, onDone: (String?) -> Unit) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            val paise = Money.parseInrToPaise(amountInr).getOrElse {
                emitToast(it.message ?: "Invalid INR amount", isError = true)
                return@launch
            }
            try {
                val tx = engine.simulateScenario(st.activeWorkspaceId, actorId, scenario, senderId, receiverId, paise)
                emitToast("Simulation '${scenario.title}' -> ${tx.status.label}")
                onDone(tx.id)
            } catch (ex: LedgerLabException) {
                emitToast("Simulated Error (${ex.httpStatus}): ${ex.message}", isError = true)
                onDone(null)
            }
        }
    }

    fun approveTransaction(txId: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.approveTransaction(st.activeWorkspaceId, actorId, txId)
                emitToast("Transaction $txId approved & posted to ledger")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun rejectTransaction(txId: String, reason: String = "Rejected during manual risk review") {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.rejectTransaction(st.activeWorkspaceId, actorId, txId, reason)
                emitToast("Transaction $txId rejected (0 postings)")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun reverseTransaction(txId: String, reason: String = "Operator-initiated double-entry reversal") {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val rev = engine.reverseTransaction(st.activeWorkspaceId, actorId, txId, reason)
                emitToast("Reversed via opposing transaction ${rev.id}")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun toggleRiskRule(ruleCode: String, enabled: Boolean) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.toggleRiskRule(st.activeWorkspaceId, actorId, ruleCode, enabled)
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun runReconciliation() {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val run = engine.runReconciliation(st.activeWorkspaceId, actorId)
                emitToast("Reconciliation ${run.status.label}: ${run.matchedAccounts}/${run.accountsChecked} accounts matched")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun importCsvReconciliation(csvText: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val run = engine.importDemoCsvStatement(st.activeWorkspaceId, actorId, csvText)
                emitToast("CSV Reconciliation: ${run.matchedAccounts} matched, ${run.mismatchedAccounts} mismatched")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun createWebhookEndpoint(url: String, description: String, events: Set<WebhookEventType>) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val (ep, secret) = engine.createWebhookEndpoint(st.activeWorkspaceId, actorId, url, description, events)
                _uiState.update { it.copy(createdWebhookSecretOnce = ep.url to secret) }
                emitToast("Webhook endpoint created")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun dismissWebhookSecretModal() {
        _uiState.update { it.copy(createdWebhookSecretOnce = null) }
    }

    fun retryWebhookDelivery(deliveryId: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.retryWebhookDelivery(st.activeWorkspaceId, actorId, deliveryId)
                emitToast("Webhook delivery retried (HTTP 200 simulated)")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun createApiKey(name: String, scopes: List<String>) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                val res = engine.createApiKey(st.activeWorkspaceId, actorId, name, scopes)
                _uiState.update { it.copy(createdApiKeyOnce = res) }
                emitToast("API Key generated (copy it now — shown once)")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun dismissApiKeyModal() {
        _uiState.update { it.copy(createdApiKeyOnce = null) }
    }

    fun revokeApiKey(keyId: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.revokeApiKey(st.activeWorkspaceId, actorId, keyId)
                emitToast("API key revoked")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun updateMemberRole(targetUserId: String, role: WorkspaceRole) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                engine.updateMemberRole(st.activeWorkspaceId, actorId, targetUserId, role)
                emitToast("Updated role to ${role.label}")
            } catch (ex: LedgerLabException) {
                emitToast(ex.message, isError = true)
            }
        }
    }

    fun executeApiPlaygroundRequest(method: String, path: String, headers: Map<String, String>, bodyJson: String) {
        val st = _uiState.value
        val actorId = st.currentUser?.id ?: return
        viewModelScope.launch {
            val result = apiRouter.execute(method, path, headers, bodyJson, st.activeWorkspaceId, actorId)
            _uiState.update {
                it.copy(
                    latestApiResult = result,
                    apiHistory = (listOf(result) + it.apiHistory).take(20)
                )
            }
        }
    }

    fun checkBackendHealth(url: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(backendConnection = it.backendConnection.copy(baseUrl = url, isChecking = true))
            }
            val result = LedgerLabRemoteClient.checkHealth(url)
            _uiState.update {
                result.fold(
                    onSuccess = { body ->
                        it.copy(
                            backendConnection = BackendConnectionState(
                                baseUrl = url,
                                isConnected = true,
                                isChecking = false,
                                statusMessage = "Connected (${body.status}, db=${body.database ?: "postgres"})"
                            )
                        )
                    },
                    onFailure = { err ->
                        it.copy(
                            backendConnection = BackendConnectionState(
                                baseUrl = url,
                                isConnected = false,
                                isChecking = false,
                                statusMessage = "Unreachable (${err.message ?: "connection refused"}) — using Preview Mode"
                            )
                        )
                    }
                )
            }
        }
    }

    fun resetSandboxData() {
        viewModelScope.launch {
            engine.resetToDemoSeed()
            val (user, session) = engine.loginDemoOwner()
            _uiState.update {
                it.copy(
                    currentUser = user,
                    currentSession = session,
                    activeWorkspaceId = "ws_sandbox",
                    selectedAccountId = null,
                    selectedCustomerId = null,
                    selectedTransactionId = null,
                    selectedJournalId = null
                )
            }
            emitToast("Preview sandbox reset to seeded demo state")
        }
    }
}
