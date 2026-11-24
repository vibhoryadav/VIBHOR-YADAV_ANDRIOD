package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.LedgerAccount
import com.example.domain.Money
import com.example.domain.ReconciliationRun
import com.example.domain.ReconciliationStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ReconciliationScreen(
    runs: List<ReconciliationRun>,
    accounts: List<LedgerAccount>,
    computeBalance: (String) -> Long,
    onRunReconciliation: () -> Unit,
    onImportCsv: (String) -> Unit
) {
    val sampleMatchedCsv = remember(accounts) {
        buildString {
            appendLine("account_number,balance_inr")
            accounts.filter { !it.isSystemAccount }.take(3).forEach { acc ->
                val balPaise = computeBalance(acc.id)
                val rupees = balPaise / 100L
                val frac = (balPaise % 100L).toString().padStart(2, '0')
                appendLine("${acc.accountNumber},$rupees.$frac")
            }
        }.trim()
    }
    var csvText by remember(sampleMatchedCsv) { mutableStateOf(sampleMatchedCsv) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Reconciliation Engine",
            subtitle = "Verify immutable ledger postings against cached balances and imported statements (Demo)",
            trailing = {
                Button(
                    onClick = onRunReconciliation,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("run_reconciliation_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Run Check")
                }
            }
        )

        // Demo CSV Statement Importer
        SurfaceCard {
            Text("Demo CSV Statement Import (Strict Schema)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Educational simulator only — not connected to a real bank. Required columns: account_number,balance_inr",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = csvText,
                onValueChange = { csvText = it },
                minLines = 4,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val first = accounts.firstOrNull { !it.isSystemAccount }
                        if (first != null) {
                            csvText = "account_number,balance_inr\n${first.accountNumber},999.00"
                        }
                    }
                ) {
                    Text("Load Mismatch Sample")
                }
                Button(
                    onClick = { onImportCsv(csvText) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Reconcile CSV")
                }
            }
        }

        // Reconciliation Runs History
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reconciliation Runs (${runs.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (runs.isNotEmpty()) {
                OutlinedButton(onClick = { showExportDialog = true }) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Export Report")
                }
            }
        }

        if (runs.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.Balance,
                title = "No reconciliation runs yet",
                description = "Run a reconciliation check to verify ledger integrity."
            )
        } else {
            runs.forEach { run ->
                SurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MonoCodeText(run.id)
                            Text(run.sourceLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(formatTimestamp(run.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        StatusPill(
                            text = run.status.label,
                            textColor = if (run.status == ReconciliationStatus.MATCHED) StatusSuccess else StatusError,
                            backgroundColor = if (run.status == ReconciliationStatus.MATCHED) StatusSuccessTint else StatusErrorTint
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Checked: ${run.accountsChecked}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Matched: ${run.matchedAccounts}", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                        Text("Mismatched: ${run.mismatchedAccounts}", style = MaterialTheme.typography.bodySmall, color = if (run.mismatchedAccounts > 0) StatusError else TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total Debit Balances", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            MoneyText(paise = run.totalDebitBalancePaise, fontSizeSp = 13)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Credit Balances", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            MoneyText(paise = run.totalCreditBalancePaise, fontSizeSp = 13)
                        }
                    }

                    if (run.issues.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(Modifier.height(8.dp))
                        Text("Unresolved Variances", style = MaterialTheme.typography.labelLarge, color = StatusError)
                        run.issues.forEach { issue ->
                            Surface(
                                color = StatusErrorTint,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, StatusError.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("${issue.accountName} (${issue.accountNumber})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        "Ledger: ${Money.formatInr(issue.ledgerBalancePaise)} | ${issue.source}: ${Money.formatInr(issue.comparedBalancePaise)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        "Variance: ${Money.formatInr(issue.variancePaise, includeSign = true)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        val reportCsv = buildString {
            appendLine("run_id,status,accounts_checked,matched,mismatched,total_debit_paise,total_credit_paise,created_at")
            runs.forEach { r ->
                appendLine("${r.id},${r.status.name},${r.accountsChecked},${r.matchedAccounts},${r.mismatchedAccounts},${r.totalDebitBalancePaise},${r.totalCreditBalancePaise},${r.createdAt}")
            }
        }
        CsvPreviewDialog("Reconciliation Report CSV", reportCsv) { showExportDialog = false }
    }
}
