package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.LedgerAccount
import com.example.domain.Money
import com.example.domain.SimulationScenario
import com.example.domain.Transaction
import com.example.domain.TransactionType
import com.example.engine.CryptoUtils
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.FilterPillRow
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.SurfaceCard
import com.example.ui.components.TransactionStatusPill
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TransfersSimulatorScreen(
    accounts: List<LedgerAccount>,
    transactions: List<Transaction>,
    computeBalance: (String) -> Long,
    onDeposit: (accountId: String, amountInr: String, ref: String, desc: String, (String) -> Unit) -> Unit,
    onTransfer: (senderId: String, receiverId: String, amountInr: String, ref: String, desc: String, idempotencyKey: String, (String) -> Unit) -> Unit,
    onWithdraw: (accountId: String, amountInr: String, ref: String, desc: String, (String) -> Unit) -> Unit,
    onRunSimulation: (SimulationScenario, String?, String?, String, (String?) -> Unit) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val walletAccounts = remember(accounts) { accounts.filter { !it.isSystemAccount } }
    var mainTab by remember { mutableIntStateOf(0) }
    var lastTxId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Transfers & Payment Simulator",
            subtitle = "Atomic double-entry money movement & failure lifecycle studio (Simulated INR)"
        )

        Surface(
            color = BgPrimary,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            TabRow(selectedTabIndex = mainTab, containerColor = BgPrimary, contentColor = AccentIndigo) {
                Tab(selected = mainTab == 0, onClick = { mainTab = 0 }, text = { Text("Money Movement") })
                Tab(selected = mainTab == 1, onClick = { mainTab = 1 }, text = { Text("Payment Simulator") })
            }
        }

        if (mainTab == 0) {
            MoneyMovementForm(
                walletAccounts = walletAccounts,
                computeBalance = computeBalance,
                onDeposit = onDeposit,
                onTransfer = onTransfer,
                onWithdraw = onWithdraw,
                onResultTx = { lastTxId = it }
            )
        } else {
            PaymentSimulatorStudio(
                walletAccounts = walletAccounts,
                computeBalance = computeBalance,
                onRunSimulation = onRunSimulation,
                onResultTx = { lastTxId = it }
            )
        }

        // Latest execution result card
        val latestTx = transactions.firstOrNull { it.id == lastTxId }
        if (latestTx != null) {
            SurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Execution Result", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    TransactionStatusPill(latestTx.status)
                }
                Spacer(Modifier.height(8.dp))
                CopyableIdChip(id = latestTx.id, label = "TX ID")
                Spacer(Modifier.height(6.dp))
                Text(latestTx.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (latestTx.failureReason != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(latestTx.failureReason, style = MaterialTheme.typography.bodySmall, color = StatusError)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onOpenTransaction(latestTx.id) }) {
                    Text("Inspect Full Lifecycle & Journal →")
                }
            }
        }

        // Transfer History
        val recentTransfers = remember(transactions) {
            transactions.filter { it.type == TransactionType.TRANSFER || it.type == TransactionType.DEPOSIT || it.type == TransactionType.WITHDRAWAL }.take(8)
        }
        SurfaceCard {
            Text("Recent Transfer & Settlement History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            recentTransfers.forEachIndexed { idx, tx ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTransaction(tx.id) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MonoCodeText("${tx.type.label} • ${tx.id}")
                        Text(tx.reference, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(paise = tx.amountPaise)
                        TransactionStatusPill(tx.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyMovementForm(
    walletAccounts: List<LedgerAccount>,
    computeBalance: (String) -> Long,
    onDeposit: (String, String, String, String, (String) -> Unit) -> Unit,
    onTransfer: (String, String, String, String, String, String, (String) -> Unit) -> Unit,
    onWithdraw: (String, String, String, String, (String) -> Unit) -> Unit,
    onResultTx: (String) -> Unit
) {
    var modeIdx by remember { mutableIntStateOf(0) } // 0=Transfer, 1=Deposit, 2=Withdrawal
    var senderAcc by remember(walletAccounts) { mutableStateOf(walletAccounts.firstOrNull()) }
    var receiverAcc by remember(walletAccounts) { mutableStateOf(walletAccounts.getOrNull(1) ?: walletAccounts.firstOrNull()) }
    var amountInr by remember { mutableStateOf("500.00") }
    var reference by remember { mutableStateOf("TRF-2026-01") }
    var description by remember { mutableStateOf("Simulated INR transfer") }
    var idempotencyKey by remember { mutableStateOf(CryptoUtils.generatePrefixedId("idem")) }
    var showPreview by remember { mutableStateOf(false) }

    SurfaceCard {
        FilterPillRow(
            options = listOf("Transfer (P2P)", "Deposit (Mint)", "Withdrawal (Burn)"),
            selectedIndex = modeIdx,
            onSelect = { modeIdx = it; showPreview = false }
        )
        Spacer(Modifier.height(12.dp))

        if (modeIdx == 0 || modeIdx == 2) {
            AccountDropdownField(
                label = if (modeIdx == 0) "Sender Account (Debit)" else "Source Wallet (Debit)",
                accounts = walletAccounts,
                selected = senderAcc,
                computeBalance = computeBalance,
                onSelect = { senderAcc = it }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (modeIdx == 0 || modeIdx == 1) {
            AccountDropdownField(
                label = if (modeIdx == 0) "Recipient Account (Credit)" else "Target Wallet (Credit)",
                accounts = walletAccounts,
                selected = receiverAcc,
                computeBalance = computeBalance,
                onSelect = { receiverAcc = it }
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = amountInr,
            onValueChange = { amountInr = it; showPreview = false },
            label = { Text("Amount (INR)") },
            placeholder = { Text("e.g. 1500.50") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("transfer_amount_input")
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = reference,
            onValueChange = { reference = it },
            label = { Text("Reference") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (modeIdx == 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = idempotencyKey,
                    onValueChange = { idempotencyKey = it },
                    label = { Text("Idempotency-Key") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { idempotencyKey = CryptoUtils.generatePrefixedId("idem") }) {
                    Text("New")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val parsedPreview = Money.parseInrToPaise(amountInr)
        if (showPreview && parsedPreview.isSuccess) {
            val paise = parsedPreview.getOrThrow()
            Surface(
                color = BgSecondary,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Double-Entry Journal Preview", style = MaterialTheme.typography.titleSmall, color = AccentIndigo)
                    val debitLabel = when (modeIdx) {
                        1 -> "Simulated External Clearing Account (Asset)"
                        else -> senderAcc?.name ?: "Sender"
                    }
                    val creditLabel = when (modeIdx) {
                        2 -> "Simulated External Clearing Account (Asset)"
                        else -> receiverAcc?.name ?: "Recipient"
                    }
                    Text("DEBIT: $debitLabel -> ${Money.formatInr(paise)}", style = MaterialTheme.typography.bodySmall, color = StatusError)
                    Text("CREDIT: $creditLabel -> ${Money.formatInr(paise)}", style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                    Text("Invariant: Total Debits == Total Credits (${Money.formatInr(paise)})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Preview Posting")
            }
            Button(
                onClick = {
                    when (modeIdx) {
                        0 -> {
                            val s = senderAcc?.id ?: return@Button
                            val r = receiverAcc?.id ?: return@Button
                            onTransfer(s, r, amountInr, reference, description, idempotencyKey) { txId ->
                                onResultTx(txId)
                                idempotencyKey = CryptoUtils.generatePrefixedId("idem")
                            }
                        }
                        1 -> {
                            val r = receiverAcc?.id ?: return@Button
                            onDeposit(r, amountInr, reference, description, onResultTx)
                        }
                        2 -> {
                            val s = senderAcc?.id ?: return@Button
                            onWithdraw(s, amountInr, reference, description, onResultTx)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                modifier = Modifier.weight(1f).testTag("execute_money_movement_button")
            ) {
                Text("Execute Atomically")
            }
        }
    }
}

@Composable
private fun PaymentSimulatorStudio(
    walletAccounts: List<LedgerAccount>,
    computeBalance: (String) -> Long,
    onRunSimulation: (SimulationScenario, String?, String?, String, (String?) -> Unit) -> Unit,
    onResultTx: (String) -> Unit
) {
    var selectedScenario by remember { mutableStateOf(SimulationScenario.SUCCEEDED) }
    var sender by remember(walletAccounts) { mutableStateOf(walletAccounts.firstOrNull()) }
    var receiver by remember(walletAccounts) { mutableStateOf(walletAccounts.getOrNull(1) ?: walletAccounts.firstOrNull()) }
    var amount by remember { mutableStateOf("250.00") }

    SurfaceCard {
        Text("Predefined Simulation Scenarios", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            "All outcomes are simulated; failed or pending flows never modify ledger balances.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))

        SimulationScenario.entries.forEach { scenario ->
            val selected = scenario == selectedScenario
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) AccentIndigoTint else BgSecondary,
                border = BorderStroke(1.dp, if (selected) AccentIndigo else BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedScenario = scenario }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scenario.title, style = MaterialTheme.typography.titleSmall, color = if (selected) AccentIndigo else TextPrimary)
                        Text(scenario.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    MonoCodeText(scenario.expectedOutcome, color = if (selected) AccentIndigo else StatusInfo)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AccountDropdownField("Sender Account", walletAccounts, sender, computeBalance) { sender = it }
        Spacer(Modifier.height(8.dp))
        AccountDropdownField("Recipient Account", walletAccounts, receiver, computeBalance) { receiver = it }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Simulated Amount (INR)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onRunSimulation(selectedScenario, sender?.id, receiver?.id, amount) { txId ->
                    if (txId != null) onResultTx(txId)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_simulation_button")
        ) {
            Text("Run Simulated Scenario")
        }
    }
}

@Composable
private fun AccountDropdownField(
    label: String,
    accounts: List<LedgerAccount>,
    selected: LedgerAccount?,
    computeBalance: (String) -> Long,
    onSelect: (LedgerAccount) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = BgSecondary,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = selected?.let { "${it.name} (${it.accountNumber})" } ?: "Select account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (selected != null) {
                MoneyText(paise = computeBalance(selected.id), fontSizeSp = 13)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = BgPrimary) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${acc.name} (${acc.accountNumber})", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(12.dp))
                            MoneyText(paise = computeBalance(acc.id), fontSizeSp = 12)
                        }
                    },
                    onClick = { onSelect(acc); expanded = false }
                )
            }
        }
    }
}
