package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DateRangeFilter
import com.example.domain.LedgerAccount
import com.example.domain.Money
import com.example.domain.OverviewMetrics
import com.example.domain.Transaction
import com.example.domain.TransactionStatus
import com.example.ui.BackendConnectionState
import com.example.ui.NavDestination
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.FilterPillRow
import com.example.ui.components.KpiCard
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.SuccessVsFailureChart
import com.example.ui.components.SurfaceCard
import com.example.ui.components.TransactionStatusPill
import com.example.ui.components.TransactionTypePill
import com.example.ui.components.VolumeBarChart
import com.example.ui.components.formatShortTime
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OverviewScreen(
    userName: String,
    dateRange: DateRangeFilter,
    metrics: OverviewMetrics,
    transactions: List<Transaction>,
    accounts: List<LedgerAccount>,
    backendState: BackendConnectionState,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val accountLookup = remember(accounts) { accounts.associateBy { it.id } }
    var search by remember { mutableStateOf("") }
    var statusFilterIdx by remember { mutableIntStateOf(0) }
    val statusLabels = listOf("All", "Succeeded", "Failed", "Requires Review", "Reversed")

    val filteredTx = remember(transactions, search, statusFilterIdx) {
        transactions.filter { tx ->
            val matchesStatus = when (statusFilterIdx) {
                1 -> tx.status == TransactionStatus.SUCCEEDED
                2 -> tx.status == TransactionStatus.FAILED
                3 -> tx.status == TransactionStatus.REQUIRES_REVIEW
                4 -> tx.status == TransactionStatus.REVERSED
                else -> true
            }
            val q = search.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                tx.id.lowercase().contains(q) ||
                tx.reference.lowercase().contains(q) ||
                tx.description.lowercase().contains(q)
            matchesStatus && matchesQuery
        }.take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DemoEnvironmentBanner(backendState = backendState)

        // Greeting header
        Column {
            Text(
                text = "Good morning, $userName",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Here's what's happening across your financial infrastructure.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Date filters
        FilterPillRow(
            options = DateRangeFilter.entries.map { it.label },
            selectedIndex = DateRangeFilter.entries.indexOf(dateRange),
            onSelect = { idx -> onDateRangeChange(DateRangeFilter.entries[idx]) }
        )

        // Quick Actions Bar
        SurfaceCard {
            Text("Quick Actions", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNavigate(NavDestination.ACCOUNTS) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("qa_create_account")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Account")
                }
                OutlinedButton(
                    onClick = { onNavigate(NavDestination.TRANSFERS) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("qa_simulate_deposit")
                ) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusSuccess)
                    Spacer(Modifier.width(6.dp))
                    Text("Simulate Deposit", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onNavigate(NavDestination.TRANSFERS) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("qa_make_transfer")
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentIndigo)
                    Spacer(Modifier.width(6.dp))
                    Text("Make Transfer", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onNavigate(NavDestination.TRANSFERS) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("qa_create_withdrawal")
                ) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusInfo)
                    Spacer(Modifier.width(6.dp))
                    Text("Create Withdrawal", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onNavigate(NavDestination.API_PLAYGROUND) },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("qa_open_playground")
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Open API Playground", color = TextPrimary)
                }
            }
        }

        // 6 KPI Cards (2-column responsive rows)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "Total Transaction Volume",
                    value = Money.formatCompactInr(metrics.totalVolumePaise),
                    subtitle = Money.formatInr(metrics.totalVolumePaise),
                    accentColor = AccentIndigo,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Total Transactions",
                    value = metrics.totalTransactions.toString(),
                    subtitle = "Across all lifecycle states",
                    accentColor = StatusInfo,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "Successful Transactions",
                    value = metrics.successfulTransactions.toString(),
                    subtitle = "Balanced journals posted",
                    accentColor = StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Failed Transactions",
                    value = metrics.failedTransactions.toString(),
                    subtitle = "Zero ledger mutations",
                    accentColor = StatusError,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    title = "Success Rate",
                    value = String.format("%.1f%%", metrics.successRatePercent),
                    subtitle = "Settled / attempted ratio",
                    accentColor = StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Pending Review",
                    value = metrics.pendingReviewCount.toString(),
                    subtitle = "${metrics.openRiskAlerts} open risk alerts",
                    accentColor = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Analytics Charts
        SurfaceCard {
            SectionHeader(
                title = "Transaction Volume (INR)",
                subtitle = "Settled daily simulated volume over trailing 7 days"
            )
            Spacer(Modifier.height(14.dp))
            VolumeBarChart(series = metrics.dailySeries)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Success vs Failure", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot("Succeeded", StatusSuccess)
                    LegendDot("Failed", StatusError)
                }
                Spacer(Modifier.height(12.dp))
                SuccessVsFailureChart(series = metrics.dailySeries)
            }
        }

        // Type Breakdown & Risk Summary
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text("Transaction Type Breakdown", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                val total = metrics.totalTransactions.coerceAtLeast(1)
                BreakdownRow("Deposits", metrics.depositCount, total, StatusSuccess)
                BreakdownRow("Transfers", metrics.transferCount, total, AccentIndigo)
                BreakdownRow("Withdrawals", metrics.withdrawalCount, total, StatusInfo)
                BreakdownRow("Reversals", metrics.reversalCount, total, StatusWarning)
            }
            SurfaceCard(
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(NavDestination.RISK_FRAUD) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Risk Alert Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${metrics.openRiskAlerts}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (metrics.openRiskAlerts > 0) StatusWarning else StatusSuccess
                )
                Text("Open alerts requiring review", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${metrics.criticalRiskAlerts} critical alerts (score >= 80)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (metrics.criticalRiskAlerts > 0) StatusError else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text("Tap to inspect Risk Engine →", style = MaterialTheme.typography.labelMedium, color = AccentIndigo)
            }
        }

        // Recent Activity Table
        SurfaceCard {
            SectionHeader(
                title = "Recent Activity",
                subtitle = "Live records from the double-entry engine",
                trailing = {
                    OutlinedButton(onClick = { onNavigate(NavDestination.TRANSACTIONS) }) {
                        Text("View all")
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter recent activity by ID or reference...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FilterPillRow(
                options = statusLabels,
                selectedIndex = statusFilterIdx,
                onSelect = { statusFilterIdx = it }
            )
            Spacer(Modifier.height(12.dp))
            filteredTx.forEachIndexed { idx, tx ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                val actorName = accountLookup[tx.senderAccountId]?.name
                    ?: accountLookup[tx.receiverAccountId]?.name
                    ?: "Wallet"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTransaction(tx.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonoCodeText(tx.id)
                            TransactionTypePill(tx.type)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${tx.reference} • $actorName",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatShortTime(tx.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(paise = tx.amountPaise, fontSizeSp = 14)
                        Spacer(Modifier.height(4.dp))
                        TransactionStatusPill(tx.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, total: Int, color: Color) {
    val fraction = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("$count", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgSecondary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
