package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AuditLogEntry
import com.example.domain.LedgerAccount
import com.example.domain.LedgerPosting
import com.example.domain.Money
import com.example.domain.PostingSide
import com.example.domain.RiskEvaluation
import com.example.domain.Transaction
import com.example.domain.TransactionStatus
import com.example.domain.TransactionType
import com.example.ui.components.ConfirmationAlertDialog
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FilterPillRow
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.SeverityPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.TransactionStatusPill
import com.example.ui.components.TransactionTypePill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    accounts: List<LedgerAccount>,
    postings: List<LedgerPosting>,
    riskEvaluations: List<RiskEvaluation>,
    auditLogs: List<AuditLogEntry>,
    selectedTransactionId: String?,
    onSelectTransaction: (String?) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onReverse: (String) -> Unit,
    onOpenJournal: (String) -> Unit
) {
    if (selectedTransactionId != null) {
        val tx = transactions.firstOrNull { it.id == selectedTransactionId }
        if (tx != null) {
            BackHandler { onSelectTransaction(null) }
            TransactionDetailView(
                tx = tx,
                accounts = accounts,
                postings = postings.filter { it.journalEntryId == tx.journalEntryId },
                riskEvaluation = riskEvaluations.firstOrNull { it.id == tx.riskEvaluationId },
                auditLogs = auditLogs.filter { it.resourceId == tx.id },
                onBack = { onSelectTransaction(null) },
                onApprove = { onApprove(tx.id) },
                onReject = { onReject(tx.id) },
                onReverse = { onReverse(tx.id) },
                onOpenJournal = onOpenJournal
            )
            return
        }
    }

    var search by remember { mutableStateOf("") }
    var typeFilterIdx by remember { mutableIntStateOf(0) }
    var statusFilterIdx by remember { mutableIntStateOf(0) }
    var sortByAmountDesc by remember { mutableStateOf(false) }
    var showCsvExport by remember { mutableStateOf(false) }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    val filtered = remember(transactions, search, typeFilterIdx, statusFilterIdx, sortByAmountDesc) {
        val base = transactions.filter { tx ->
            val matchType = when (typeFilterIdx) {
                1 -> tx.type == TransactionType.DEPOSIT
                2 -> tx.type == TransactionType.TRANSFER
                3 -> tx.type == TransactionType.WITHDRAWAL
                4 -> tx.type == TransactionType.REVERSAL
                else -> true
            }
            val matchStatus = when (statusFilterIdx) {
                1 -> tx.status == TransactionStatus.SUCCEEDED
                2 -> tx.status == TransactionStatus.FAILED
                3 -> tx.status == TransactionStatus.REQUIRES_REVIEW
                4 -> tx.status == TransactionStatus.PROCESSING
                5 -> tx.status == TransactionStatus.REVERSED
                else -> true
            }
            val q = search.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                tx.id.lowercase().contains(q) ||
                tx.reference.lowercase().contains(q) ||
                tx.description.lowercase().contains(q) ||
                tx.idempotencyKey.lowercase().contains(q)
            matchType && matchStatus && matchSearch
        }
        if (sortByAmountDesc) base.sortedByDescending { it.amountPaise }
        else base.sortedByDescending { it.createdAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "Transactions Explorer",
            subtitle = "${filtered.size} transactions • Click any row for lifecycle, risk & journal lines",
            trailing = {
                OutlinedButton(
                    onClick = { showCsvExport = true },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV")
                }
            }
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search TX ID, reference, description, or idempotency key...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        FilterPillRow(
            options = listOf("All Types", "Deposit", "Transfer", "Withdrawal", "Reversal"),
            selectedIndex = typeFilterIdx,
            onSelect = { typeFilterIdx = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPillRow(
                options = listOf("All Status", "Succeeded", "Failed", "Requires Review", "Processing", "Reversed"),
                selectedIndex = statusFilterIdx,
                onSelect = { statusFilterIdx = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { sortByAmountDesc = !sortByAmountDesc }) {
                Text(if (sortByAmountDesc) "Sort: Amount ↓" else "Sort: Recent ↓")
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = "No matching transactions",
                description = "Try clearing filters or execute a simulated transfer."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    val senderName = accountMap[tx.senderAccountId]?.name ?: tx.senderAccountId ?: "—"
                    val receiverName = accountMap[tx.receiverAccountId]?.name ?: tx.receiverAccountId ?: "—"
                    SurfaceCard(
                        onClick = { onSelectTransaction(tx.id) },
                        modifier = Modifier.fillMaxWidth().testTag("tx_row_${tx.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MonoCodeText(tx.id)
                                    TransactionTypePill(tx.type)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("$senderName → $receiverName", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text(
                                    "${tx.reference} • Risk ${tx.riskScore}/100 • ${formatTimestamp(tx.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(paise = tx.amountPaise)
                                Spacer(Modifier.height(4.dp))
                                TransactionStatusPill(tx.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCsvExport) {
        val csv = buildString {
            appendLine("transaction_id,type,status,amount_paise,amount_inr,currency,reference,risk_score,created_at")
            filtered.forEach { tx ->
                appendLine("${tx.id},${tx.type.name},${tx.status.name},${tx.amountPaise},\"${Money.formatInr(tx.amountPaise)}\",${tx.currency},\"${tx.reference}\",${tx.riskScore},${tx.createdAt}")
            }
        }
        CsvPreviewDialog(title = "Transactions CSV Export", csvText = csv, onDismiss = { showCsvExport = false })
    }
}

@Composable
private fun TransactionDetailView(
    tx: Transaction,
    accounts: List<LedgerAccount>,
    postings: List<LedgerPosting>,
    riskEvaluation: RiskEvaluation?,
    auditLogs: List<AuditLogEntry>,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onReverse: () -> Unit,
    onOpenJournal: (String) -> Unit
) {
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    var confirmReverse by remember { mutableStateOf(false) }
    var confirmReject by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to transactions")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Transaction Detail", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                MonoCodeText(tx.id)
            }
            TransactionStatusPill(tx.status)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyableIdChip(id = tx.id, label = "TX")
            CopyableIdChip(id = tx.idempotencyKey, label = "Idempotency")
        }

        SurfaceCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Amount", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    MoneyText(paise = tx.amountPaise, fontSizeSp = 22)
                }
                TransactionTypePill(tx.type)
            }
            Spacer(Modifier.height(10.dp))
            DetailPair("Reference", tx.reference)
            DetailPair("Description", tx.description)
            DetailPair("Sender", accountMap[tx.senderAccountId]?.let { "${it.name} (${it.accountNumber})" } ?: tx.senderAccountId ?: "—")
            DetailPair("Recipient", accountMap[tx.receiverAccountId]?.let { "${it.name} (${it.accountNumber})" } ?: tx.receiverAccountId ?: "—")
            DetailPair("Created At", formatTimestamp(tx.createdAt))
            DetailPair("Completed At", tx.completedAt?.let { formatTimestamp(it) } ?: "Pending")
            if (tx.failureReason != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = StatusErrorTint,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Failure Reason: ${tx.failureReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (tx.status == TransactionStatus.REQUIRES_REVIEW || tx.status == TransactionStatus.SUCCEEDED) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tx.status == TransactionStatus.REQUIRES_REVIEW) {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            modifier = Modifier.testTag("approve_tx_button")
                        ) {
                            Text("Approve & Post")
                        }
                        OutlinedButton(
                            onClick = { confirmReject = true },
                            border = BorderStroke(1.dp, StatusError),
                            modifier = Modifier.testTag("reject_tx_button")
                        ) {
                            Text("Reject", color = StatusError)
                        }
                    }
                    if (tx.status == TransactionStatus.SUCCEEDED && tx.type != TransactionType.REVERSAL) {
                        OutlinedButton(
                            onClick = { confirmReverse = true },
                            border = BorderStroke(1.dp, StatusWarning),
                            modifier = Modifier.testTag("reverse_tx_button")
                        ) {
                            Text("Reverse via Opposing Entry", color = StatusWarning)
                        }
                    }
                }
            }
        }

        // Lifecycle Timeline
        SurfaceCard {
            Text("Lifecycle Timeline", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            tx.lifecycleTimeline.forEachIndexed { idx, ev ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (idx == tx.lifecycleTimeline.lastIndex) AccentIndigo else BorderSubtle)
                    )
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TransactionStatusPill(ev.status)
                            Text(formatTimestamp(ev.timestamp), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(ev.note, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
            }
        }

        // Risk Evaluation
        if (riskEvaluation != null) {
            SurfaceCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Risk Evaluation", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    SeverityPill(riskEvaluation.severity)
                }
                Spacer(Modifier.height(6.dp))
                Text("Score: ${riskEvaluation.score}/100 • Action: ${riskEvaluation.recommendedAction.label}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (riskEvaluation.rulesTriggered.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("No anomalous risk rules triggered.", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                } else {
                    riskEvaluation.rulesTriggered.forEach { rule ->
                        Spacer(Modifier.height(6.dp))
                        Text("• ${rule.title} (+${rule.scoreContribution}): ${rule.reason}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                }
            }
        }

        // Associated Journal Postings
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Associated Double-Entry Journal", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (tx.journalEntryId != null) {
                    TextButton(onClick = { onOpenJournal(tx.journalEntryId) }) {
                        Text("Inspect Journal →")
                    }
                }
            }
            if (postings.isEmpty()) {
                Text("No posted ledger lines (transaction has not settled).", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                postings.forEachIndexed { idx, p ->
                    if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    val accName = accountMap[p.accountId]?.name ?: p.accountId
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(accName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            MonoCodeText("${p.side.label} • ${p.accountId}")
                        }
                        MoneyText(
                            paise = p.amountPaise,
                            color = if (p.side == PostingSide.CREDIT) StatusSuccess else AccentIndigo
                        )
                    }
                }
            }
        }

        // Audit History
        if (auditLogs.isNotEmpty()) {
            SurfaceCard {
                Text("Audit Trail", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                auditLogs.forEach { log ->
                    Text(
                        "${log.eventType.label} by ${log.actorEmail} • ${formatTimestamp(log.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    if (confirmReverse) {
        ConfirmationAlertDialog(
            title = "Reverse Transaction?",
            message = "This will create a new opposing reversal journal entry (never editing or deleting the original immutable journal).",
            confirmLabel = "Post Reversal",
            isDestructive = false,
            onConfirm = { confirmReverse = false; onReverse() },
            onDismiss = { confirmReverse = false }
        )
    }

    if (confirmReject) {
        ConfirmationAlertDialog(
            title = "Reject Flagged Transaction?",
            message = "The transaction will transition to FAILED with zero ledger postings.",
            confirmLabel = "Reject",
            isDestructive = true,
            onConfirm = { confirmReject = false; onReject() },
            onDismiss = { confirmReject = false }
        )
    }
}

@Composable
private fun DetailPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CsvPreviewDialog(title: String, csvText: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPrimary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Surface(
                color = BgSecondary,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().height(220.dp)
            ) {
                Text(
                    text = csvText,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(csvText))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
            ) {
                Text("Copy CSV")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
