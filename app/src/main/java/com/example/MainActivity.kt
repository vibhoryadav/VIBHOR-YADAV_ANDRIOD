package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LedgerLabViewModel
import com.example.ui.NavDestination
import com.example.ui.components.AppShell
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.ApiDocsScreen
import com.example.ui.screens.ApiKeysScreen
import com.example.ui.screens.ApiPlaygroundScreen
import com.example.ui.screens.AuditLogsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.LedgerExplorerScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ReconciliationScreen
import com.example.ui.screens.RiskFraudScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.TransfersSimulatorScreen
import com.example.ui.screens.WebhooksScreen
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.LedgerLabTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.White.toArgb(),
                Color.White.toArgb()
            )
        )
        setContent {
            LedgerLabTheme {
                LedgerLabRootApp()
            }
        }
    }
}

@Composable
fun LedgerLabRootApp(
    vm: LedgerLabViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.toastEvents.collect { (msg, _) ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = TextPrimary,
                    contentColor = BgPrimary,
                    actionColor = StatusError
                )
            }
        }
    ) { outerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(outerPadding)) {
            val user = uiState.currentUser
            if (user == null) {
                AuthScreen(
                    authError = uiState.authError,
                    isBusy = uiState.isBusy,
                    backendState = uiState.backendConnection,
                    onLogin = vm::loginWithEmail,
                    onRegister = vm::registerUser,
                    onEnterDemoSandbox = vm::enterDemoSandbox
                )
            } else {
                val wsId = uiState.activeWorkspaceId
                val userWorkspaces = remember(snap.workspaces, snap.members, user.id) {
                    val memberWsIds = snap.members.filter { it.userId == user.id }.map { it.workspaceId }.toSet()
                    snap.workspaces.filter { it.id in memberWsIds }
                }
                val currentRole = remember(snap.members, wsId, user.id) {
                    snap.members.firstOrNull { it.workspaceId == wsId && it.userId == user.id }?.role
                }

                // Strictly workspace-scoped slices
                val wsAccounts = remember(snap.accounts, wsId) { snap.accounts.filter { it.workspaceId == wsId } }
                val wsCustomers = remember(snap.customers, wsId) { snap.customers.filter { it.workspaceId == wsId } }
                val wsTransactions = remember(snap.transactions, wsId, uiState.globalSearch) {
                    val q = uiState.globalSearch.trim().lowercase()
                    snap.transactions.filter { it.workspaceId == wsId }.filter { tx ->
                        q.isEmpty() || tx.id.lowercase().contains(q) || tx.reference.lowercase().contains(q) || tx.description.lowercase().contains(q)
                    }
                }
                val wsJournals = remember(snap.journals, wsId) { snap.journals.filter { it.workspaceId == wsId } }
                val wsPostings = remember(snap.postings, wsId) { snap.postings.filter { it.workspaceId == wsId } }
                val wsRisks = remember(snap.riskEvaluations, wsId) { snap.riskEvaluations.filter { it.workspaceId == wsId } }
                val wsRecons = remember(snap.reconciliationRuns, wsId) { snap.reconciliationRuns.filter { it.workspaceId == wsId } }
                val wsEndpoints = remember(snap.webhookEndpoints, wsId) { snap.webhookEndpoints.filter { it.workspaceId == wsId } }
                val wsEvents = remember(snap.webhookEvents, wsId) { snap.webhookEvents.filter { it.workspaceId == wsId } }
                val wsDeliveries = remember(snap.webhookDeliveries, wsId) { snap.webhookDeliveries.filter { it.workspaceId == wsId } }
                val wsApiKeys = remember(snap.apiKeys, wsId) { snap.apiKeys.filter { it.workspaceId == wsId } }
                val wsAuditLogs = remember(snap.auditLogs, wsId) { snap.auditLogs.filter { it.workspaceId == wsId } }
                val wsMembers = remember(snap.members, wsId) { snap.members.filter { it.workspaceId == wsId } }

                val metrics = remember(snap.version, wsId, uiState.selectedDateRange) {
                    vm.engine.getOverviewMetrics(wsId, uiState.selectedDateRange)
                }

                AppShell(
                    uiState = uiState,
                    workspaces = userWorkspaces,
                    currentRole = currentRole,
                    pendingReviewCount = metrics.pendingReviewCount,
                    openAlertsCount = metrics.openRiskAlerts,
                    onNavigate = vm::navigateTo,
                    onSelectWorkspace = vm::selectWorkspace,
                    onToggleSidebar = vm::toggleSidebar,
                    onSearchChange = vm::setGlobalSearch,
                    onSwitchDemoActor = vm::switchDemoActor,
                    onLogout = vm::logout
                ) { contentModifier ->
                    Box(modifier = contentModifier) {
                        when (uiState.activeDestination) {
                            NavDestination.OVERVIEW -> OverviewScreen(
                                userName = user.name.substringBefore(" "),
                                dateRange = uiState.selectedDateRange,
                                metrics = metrics,
                                transactions = wsTransactions,
                                accounts = wsAccounts,
                                backendState = uiState.backendConnection,
                                onDateRangeChange = vm::setDateRange,
                                onNavigate = vm::navigateTo,
                                onOpenTransaction = { txId ->
                                    vm.navigateTo(NavDestination.TRANSACTIONS)
                                    vm.openTransactionDetail(txId)
                                }
                            )

                            NavDestination.ACCOUNTS -> AccountsScreen(
                                accounts = wsAccounts,
                                customers = wsCustomers,
                                transactions = wsTransactions,
                                postings = wsPostings,
                                selectedAccountId = uiState.selectedAccountId,
                                computeBalance = vm.engine::computeLedgerDerivedBalancePaise,
                                computeTotals = vm.engine::computeAccountTotals,
                                onSelectAccount = vm::openAccountDetail,
                                onCreateAccount = vm::createAccount,
                                onUpdateAccountStatus = vm::updateAccountStatus,
                                onOpenTransaction = { txId ->
                                    vm.navigateTo(NavDestination.TRANSACTIONS)
                                    vm.openTransactionDetail(txId)
                                }
                            )

                            NavDestination.CUSTOMERS -> CustomersScreen(
                                customers = wsCustomers,
                                accounts = wsAccounts,
                                transactions = wsTransactions,
                                selectedCustomerId = uiState.selectedCustomerId,
                                computeBalance = vm.engine::computeLedgerDerivedBalancePaise,
                                onSelectCustomer = vm::openCustomerDetail,
                                onCreateCustomer = vm::createCustomer,
                                onUpdateCustomer = vm::updateCustomer,
                                onOpenAccount = { accId ->
                                    vm.navigateTo(NavDestination.ACCOUNTS)
                                    vm.openAccountDetail(accId)
                                },
                                onOpenTransaction = { txId ->
                                    vm.navigateTo(NavDestination.TRANSACTIONS)
                                    vm.openTransactionDetail(txId)
                                }
                            )

                            NavDestination.TRANSACTIONS -> TransactionsScreen(
                                transactions = wsTransactions,
                                accounts = wsAccounts,
                                postings = wsPostings,
                                riskEvaluations = wsRisks,
                                auditLogs = wsAuditLogs,
                                selectedTransactionId = uiState.selectedTransactionId,
                                onSelectTransaction = vm::openTransactionDetail,
                                onApprove = vm::approveTransaction,
                                onReject = { txId -> vm.rejectTransaction(txId) },
                                onReverse = { txId -> vm.reverseTransaction(txId) },
                                onOpenJournal = { jId ->
                                    vm.navigateTo(NavDestination.LEDGER)
                                    vm.openJournalDetail(jId)
                                }
                            )

                            NavDestination.TRANSFERS -> TransfersSimulatorScreen(
                                accounts = wsAccounts,
                                transactions = wsTransactions,
                                computeBalance = vm.engine::computeLedgerDerivedBalancePaise,
                                onDeposit = vm::executeDeposit,
                                onTransfer = vm::executeTransfer,
                                onWithdraw = vm::executeWithdrawal,
                                onRunSimulation = vm::runSimulation,
                                onOpenTransaction = { txId ->
                                    vm.navigateTo(NavDestination.TRANSACTIONS)
                                    vm.openTransactionDetail(txId)
                                }
                            )

                            NavDestination.LEDGER -> LedgerExplorerScreen(
                                journals = wsJournals,
                                postings = wsPostings,
                                accounts = wsAccounts,
                                trialBalance = vm.engine.getTrialBalance(wsId),
                                selectedJournalId = uiState.selectedJournalId,
                                onSelectJournal = vm::openJournalDetail,
                                onReverseTransaction = { txId -> vm.reverseTransaction(txId) }
                            )

                            NavDestination.RECONCILIATION -> ReconciliationScreen(
                                runs = wsRecons,
                                accounts = wsAccounts,
                                computeBalance = vm.engine::computeLedgerDerivedBalancePaise,
                                onRunReconciliation = vm::runReconciliation,
                                onImportCsv = vm::importCsvReconciliation
                            )

                            NavDestination.RISK_FRAUD -> RiskFraudScreen(
                                evaluations = wsRisks,
                                rules = snap.riskRules,
                                transactions = wsTransactions,
                                onToggleRule = vm::toggleRiskRule,
                                onApproveTx = vm::approveTransaction,
                                onRejectTx = { txId -> vm.rejectTransaction(txId) },
                                onOpenTransaction = { txId ->
                                    vm.navigateTo(NavDestination.TRANSACTIONS)
                                    vm.openTransactionDetail(txId)
                                }
                            )

                            NavDestination.API_PLAYGROUND -> ApiPlaygroundScreen(
                                accounts = wsAccounts,
                                customers = wsCustomers,
                                latestResult = uiState.latestApiResult,
                                history = uiState.apiHistory,
                                onSendRequest = vm::executeApiPlaygroundRequest
                            )

                            NavDestination.API_DOCS -> ApiDocsScreen()

                            NavDestination.WEBHOOKS -> WebhooksScreen(
                                endpoints = wsEndpoints,
                                events = wsEvents,
                                deliveries = wsDeliveries,
                                createdSecretOnce = uiState.createdWebhookSecretOnce,
                                getVerificationSecret = { epId -> vm.engine.getWebhookSigningSecretForVerification(wsId, epId) },
                                onCreateEndpoint = vm::createWebhookEndpoint,
                                onRetryDelivery = vm::retryWebhookDelivery,
                                onDismissSecretModal = vm::dismissWebhookSecretModal
                            )

                            NavDestination.API_KEYS -> ApiKeysScreen(
                                apiKeys = wsApiKeys,
                                createdKeyOnce = uiState.createdApiKeyOnce,
                                onCreateKey = vm::createApiKey,
                                onRevokeKey = vm::revokeApiKey,
                                onDismissKeyModal = vm::dismissApiKeyModal
                            )

                            NavDestination.AUDIT_LOGS -> AuditLogsScreen(auditLogs = wsAuditLogs)

                            NavDestination.SETTINGS -> SettingsScreen(
                                activeWorkspace = snap.workspaces.firstOrNull { it.id == wsId },
                                members = wsMembers,
                                currentUserId = user.id,
                                backendState = uiState.backendConnection,
                                onUpdateRole = vm::updateMemberRole,
                                onCheckBackend = vm::checkBackendHealth,
                                onResetSandbox = vm::resetSandboxData
                            )
                        }
                    }
                }
            }
        }
    }
}
