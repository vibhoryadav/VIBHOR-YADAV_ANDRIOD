package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.RiskEvaluation
import com.example.domain.RiskRuleConfig
import com.example.domain.RiskSeverity
import com.example.domain.Transaction
import com.example.domain.TransactionStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FilterPillRow
import com.example.ui.components.KpiCard
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.SeverityPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.TransactionStatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RiskFraudScreen(
    evaluations: List<RiskEvaluation>,
    rules: List<RiskRuleConfig>,
    transactions: List<Transaction>,
    onToggleRule: (String, Boolean) -> Unit,
    onApproveTx: (String) -> Unit,
    onRejectTx: (String) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val txMap = remember(transactions) { transactions.associateBy { it.id } }
    var filterIdx by remember { mutableIntStateOf(0) }

    val alertsOnly = remember(evaluations) { evaluations.filter { it.score > 0 } }
    val criticalCount = remember(evaluations) { evaluations.count { it.severity == RiskSeverity.CRITICAL } }
    val pendingInvestigations = remember(evaluations, txMap) {
        evaluations.count { txMap[it.transactionId]?.status == TransactionStatus.REQUIRES_REVIEW }
    }

    val displayed = remember(evaluations, filterIdx, txMap) {
        when (filterIdx) {
            1 -> evaluations.filter { txMap[it.transactionId]?.status == TransactionStatus.REQUIRES_REVIEW }
            2 -> evaluations.filter { it.severity == RiskSeverity.HIGH || it.severity == RiskSeverity.CRITICAL }
            3 -> evaluations.filter { it.resolvedAt != null }
            else -> evaluations
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Risk & Fraud Detection Engine",
            subtitle = "Deterministic, explainable behavioural scoring (0–100) — no opaque black-box decisions"
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "Total Risk Alerts",
                value = alertsOnly.size.toString(),
                subtitle = "Score > 0 evaluations",
                accentColor = StatusWarning,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Critical Alerts",
                value = criticalCount.toString(),
                subtitle = "Score 80–100",
                accentColor = StatusError,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Pending Review",
                value = pendingInvestigations.toString(),
                subtitle = "Held transactions",
                accentColor = AccentIndigo,
                modifier = Modifier.weight(1f)
            )
        }

        // Configurable Rules Panel
        SurfaceCard {
            Text("Configurable Behavioural Rules (${rules.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Rules evaluate transaction velocity, failures, and account age.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            rules.forEachIndexed { idx, rule ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("${rule.title} (+${rule.weight} pts)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleRule(rule.ruleCode, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentIndigo)
                    )
                }
            }
        }

        FilterPillRow(
            options = listOf("All Evaluations", "Pending Review", "High & Critical", "Resolved"),
            selectedIndex = filterIdx,
            onSelect = { filterIdx = it }
        )

        if (displayed.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.Security,
                title = "No risk evaluations match",
                description = "Execute transactions or simulations to generate explainable risk scores."
            )
        } else {
            displayed.forEach { eval ->
                val tx = txMap[eval.transactionId]
                SurfaceCard(
                    onClick = { tx?.let { onOpenTransaction(it.id) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Score: ${eval.score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                SeverityPill(eval.severity)
                            }
                            MonoCodeText("TX: ${eval.transactionId} • ${eval.recommendedAction.label}")
                        }
                        if (tx != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(paise = tx.amountPaise)
                                TransactionStatusPill(tx.status)
                            }
                        }
                    }

                    if (eval.rulesTriggered.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        eval.rulesTriggered.forEach { match ->
                            Text(
                                text = "• ${match.title} (+${match.scoreContribution}): ${match.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("No rules triggered — baseline low-risk activity.", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Evaluated: ${formatTimestamp(eval.evaluatedAt)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (eval.resolutionNote != null) {
                        Text("Resolution: ${eval.resolutionNote}", style = MaterialTheme.typography.labelSmall, color = AccentIndigo)
                    }

                    if (tx?.status == TransactionStatus.REQUIRES_REVIEW) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onApproveTx(tx.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                modifier = Modifier.testTag("risk_approve_${tx.id}")
                            ) {
                                Text("Approve")
                            }
                            OutlinedButton(
                                onClick = { onRejectTx(tx.id) },
                                modifier = Modifier.testTag("risk_reject_${tx.id}")
                            ) {
                                Text("Reject", color = StatusError)
                            }
                        }
                    }
                }
            }
        }
    }
}
