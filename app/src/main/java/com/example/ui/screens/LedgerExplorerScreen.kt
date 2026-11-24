package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.JournalEntry
import com.example.domain.LedgerAccount
import com.example.domain.LedgerPosting
import com.example.domain.Money
import com.example.domain.PostingSide
import com.example.domain.TrialBalanceRow
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LedgerExplorerScreen(
    journals: List<JournalEntry>,
    postings: List<LedgerPosting>,
    accounts: List<LedgerAccount>,
    trialBalance: List<TrialBalanceRow>,
    selectedJournalId: String?,
    onSelectJournal: (String?) -> Unit,
    onReverseTransaction: (String) -> Unit
) {
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    if (selectedJournalId != null) {
        val journal = journals.firstOrNull { it.id == selectedJournalId }
        if (journal != null) {
            BackHandler { onSelectJournal(null) }
            val lines = postings.filter { it.journalEntryId == journal.id }
            JournalDetailView(
                journal = journal,
                lines = lines,
                accountMap = accountMap,
                onBack = { onSelectJournal(null) },
                onReverseTransaction = { onReverseTransaction(journal.transactionId) }
            )
            return
        }
    }

    var tabIdx by remember { mutableIntStateOf(0) }
    var showCsv by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Double-Entry Ledger Explorer",
            subtitle = "Immutable posted journals • Every debit equals credit",
            trailing = {
                OutlinedButton(onClick = { showCsv = true }) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV")
                }
            }
        )

        Surface(
            color = BgPrimary,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            TabRow(selectedTabIndex = tabIdx, containerColor = BgPrimary, contentColor = AccentIndigo) {
                Tab(selected = tabIdx == 0, onClick = { tabIdx = 0 }, text = { Text("Journals (${journals.size})") })
                Tab(selected = tabIdx == 1, onClick = { tabIdx = 1 }, text = { Text("Trial Balance") })
                Tab(selected = tabIdx == 2, onClick = { tabIdx = 2 }, text = { Text("General Ledger") })
            }
        }

        when (tabIdx) {
            0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(journals, key = { it.id }) { j ->
                        val lines = postings.filter { it.journalEntryId == j.id }
                        val debits = lines.filter { it.side == PostingSide.DEBIT }.sumOf { it.amountPaise }
                        val credits = lines.filter { it.side == PostingSide.CREDIT }.sumOf { it.amountPaise }
                        SurfaceCard(
                            onClick = { onSelectJournal(j.id) },
                            modifier = Modifier.fillMaxWidth().testTag("journal_card_${j.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MonoCodeText(j.id)
                                        StatusPill(
                                            text = if (j.isReversal) "REVERSAL" else "POSTED",
                                            textColor = if (j.isReversal) StatusWarning else StatusSuccess,
                                            backgroundColor = if (j.isReversal) StatusWarningTint else StatusSuccessTint
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("${j.reference} • ${j.description}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    Text("TX: ${j.transactionId} • ${formatTimestamp(j.postedAt)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    MoneyText(paise = debits)
                                    Text(
                                        text = if (debits == credits) "Balanced" else "UNBALANCED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (debits == credits) StatusSuccess else StatusError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                val sumDebits = trialBalance.sumOf { it.totalDebitsPaise }
                val sumCredits = trialBalance.sumOf { it.totalCreditsPaise }
                Surface(
                    color = StatusSuccessTint,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                            Text("Trial Balance Verified: Total Debits = Total Credits", style = MaterialTheme.typography.labelLarge, color = StatusSuccess)
                        }
                        MoneyText(paise = sumDebits, color = StatusSuccess)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(trialBalance, key = { it.accountId }) { row ->
                        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.accountName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    MonoCodeText("${row.accountNumber} • ${row.category.label} (${row.normalBalance.label})")
                                }
                                MoneyText(paise = row.netBalancePaise, fontSizeSp = 15)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Debits: ${Money.formatInr(row.totalDebitsPaise)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("Credits: ${Money.formatInr(row.totalCreditsPaise)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            2 -> {
                val sortedPostings = remember(postings) { postings.sortedByDescending { it.createdAt } }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(sortedPostings, key = { it.id }) { p ->
                        val acc = accountMap[p.accountId]
                        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc?.name ?: p.accountId, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    MonoCodeText("${p.journalEntryId} • ${p.side.label}")
                                }
                                MoneyText(
                                    paise = p.amountPaise,
                                    color = if (p.side == PostingSide.CREDIT) StatusSuccess else AccentIndigo
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCsv) {
        val csv = buildString {
            appendLine("posting_id,journal_id,account_id,side,amount_paise,currency,created_at")
            postings.forEach { p ->
                appendLine("${p.id},${p.journalEntryId},${p.accountId},${p.side.name},${p.amountPaise},${p.currency},${p.createdAt}")
            }
        }
        CsvPreviewDialog(title = "General Ledger CSV Export", csvText = csv, onDismiss = { showCsv = false })
    }
}

@Composable
private fun JournalDetailView(
    journal: JournalEntry,
    lines: List<LedgerPosting>,
    accountMap: Map<String, LedgerAccount>,
    onBack: () -> Unit,
    onReverseTransaction: () -> Unit
) {
    val totalDebits = lines.filter { it.side == PostingSide.DEBIT }.sumOf { it.amountPaise }
    val totalCredits = lines.filter { it.side == PostingSide.CREDIT }.sumOf { it.amountPaise }
    val balanced = totalDebits == totalCredits && lines.size >= 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to ledger")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Journal Entry Detail", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                MonoCodeText(journal.id)
            }
            StatusPill(
                text = if (journal.isReversal) "REVERSAL" else "IMMUTABLE",
                textColor = AccentIndigo,
                backgroundColor = AccentIndigoTint
            )
        }

        // Required balanced validation banner
        Surface(
            color = if (balanced) StatusSuccessTint else StatusError.copy(alpha = 0.1f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (balanced) StatusSuccess else StatusError),
            modifier = Modifier.fillMaxWidth().testTag("journal_balanced_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (balanced) StatusSuccess else StatusError)
                Text(
                    text = if (balanced) "Journal balanced: Debits = Credits (${Money.formatInr(totalDebits)})" else "Journal Unbalanced",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (balanced) StatusSuccess else StatusError
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyableIdChip(id = journal.id, label = "Journal")
            CopyableIdChip(id = journal.transactionId, label = "TX")
        }

        SurfaceCard {
            Text(journal.description, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Reference: ${journal.reference}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Posted: ${formatTimestamp(journal.postedAt)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text("Posting Lines", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            lines.forEachIndexed { idx, line ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                val acc = accountMap[line.accountId]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc?.name ?: "Account", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        MonoCodeText("${acc?.accountNumber ?: line.accountId} • ${line.accountId}")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (line.side == PostingSide.DEBIT) "DEBIT" else "CREDIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (line.side == PostingSide.DEBIT) AccentIndigo else StatusSuccess
                        )
                        MoneyText(paise = line.amountPaise)
                    }
                }
            }
            if (!journal.isReversal) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onReverseTransaction) {
                    Text("Correct via Reversal Journal")
                }
            }
        }
    }
}
