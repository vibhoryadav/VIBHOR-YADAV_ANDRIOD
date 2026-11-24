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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AccountStatus
import com.example.domain.Customer
import com.example.domain.LedgerAccount
import com.example.domain.LedgerPosting
import com.example.domain.PostingSide
import com.example.domain.Transaction
import com.example.ui.components.AccountStatusPill
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FilterPillRow
import com.example.ui.components.MoneyText
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.TransactionStatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AccountsScreen(
    accounts: List<LedgerAccount>,
    customers: List<Customer>,
    transactions: List<Transaction>,
    postings: List<LedgerPosting>,
    selectedAccountId: String?,
    computeBalance: (String) -> Long,
    computeTotals: (String) -> Pair<Long, Long>,
    onSelectAccount: (String?) -> Unit,
    onCreateAccount: (customerId: String, name: String) -> Unit,
    onUpdateAccountStatus: (accountId: String, status: AccountStatus) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    if (selectedAccountId != null) {
        val acc = accounts.firstOrNull { it.id == selectedAccountId }
        if (acc != null) {
            BackHandler { onSelectAccount(null) }
            AccountDetailView(
                account = acc,
                customer = customers.firstOrNull { it.id == acc.customerId },
                balancePaise = computeBalance(acc.id),
                totals = computeTotals(acc.id),
                transactions = transactions.filter { it.senderAccountId == acc.id || it.receiverAccountId == acc.id },
                postings = postings.filter { it.accountId == acc.id }.sortedByDescending { it.createdAt },
                onBack = { onSelectAccount(null) },
                onUpdateStatus = { st -> onUpdateAccountStatus(acc.id, st) },
                onOpenTransaction = onOpenTransaction
            )
            return
        }
    }

    var search by remember { mutableStateOf("") }
    var filterIdx by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filtered = remember(accounts, search, filterIdx) {
        accounts.filter { acc ->
            val matchStatus = when (filterIdx) {
                1 -> acc.status == AccountStatus.ACTIVE
                2 -> acc.status == AccountStatus.SUSPENDED
                3 -> acc.status == AccountStatus.CLOSED
                else -> true
            }
            val q = search.trim().lowercase()
            val matchQuery = q.isEmpty() ||
                acc.name.lowercase().contains(q) ||
                acc.accountNumber.lowercase().contains(q) ||
                acc.id.lowercase().contains(q)
            matchStatus && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Virtual Accounts",
            subtitle = "Chart of accounts: System Clearing Asset & Customer Wallet Liabilities",
            trailing = {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("create_account_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New Account")
                }
            }
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search account number, name, or ID...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        FilterPillRow(
            options = listOf("All", "Active", "Suspended", "Closed"),
            selectedIndex = filterIdx,
            onSelect = { filterIdx = it }
        )

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "No matching accounts",
                description = "Create a virtual wallet account or adjust your filters."
            )
        } else {
            val customerMap = remember(customers) { customers.associateBy { it.id } }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { acc ->
                    val bal = computeBalance(acc.id)
                    val cust = customerMap[acc.customerId]
                    SurfaceCard(
                        onClick = { onSelectAccount(acc.id) },
                        modifier = Modifier.fillMaxWidth().testTag("account_card_${acc.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(acc.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    if (acc.isSystemAccount) {
                                        StatusPill("SYSTEM ASSET", AccentIndigo, AccentIndigoTint)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                MonoCodeText("${acc.accountNumber} • ${acc.category.label} (${acc.normalBalance.label})")
                                if (cust != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("Customer: ${cust.fullName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(paise = bal, fontSizeSp = 16)
                                Spacer(Modifier.height(4.dp))
                                AccountStatusPill(acc.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAccountDialog(
            customers = customers,
            onDismiss = { showCreateDialog = false },
            onCreate = { customerId, name ->
                onCreateAccount(customerId, name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun AccountDetailView(
    account: LedgerAccount,
    customer: Customer?,
    balancePaise: Long,
    totals: Pair<Long, Long>,
    transactions: List<Transaction>,
    postings: List<LedgerPosting>,
    onBack: () -> Unit,
    onUpdateStatus: (AccountStatus) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to accounts")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                MonoCodeText("${account.accountNumber} • ${account.category.label} (${account.normalBalance.label} normal)")
            }
            AccountStatusPill(account.status)
        }

        CopyableIdChip(id = account.id, label = "Account ID")

        SurfaceCard {
            Text("Ledger-Derived Balance Summary", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            MoneyText(paise = balancePaise, fontSizeSp = 24)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Credits", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    MoneyText(paise = totals.second, color = StatusSuccess, fontSizeSp = 15)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Debits", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    MoneyText(paise = totals.first, color = StatusError, fontSizeSp = 15)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Created: ${formatTimestamp(account.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (customer != null) {
                Text(
                    text = "Owner Customer: ${customer.fullName} (${customer.email})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (!account.isSystemAccount) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account.status != AccountStatus.SUSPENDED) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(AccountStatus.SUSPENDED) },
                            border = BorderStroke(1.dp, StatusWarning)
                        ) {
                            Text("Suspend Account", color = StatusWarning)
                        }
                    } else {
                        Button(
                            onClick = { onUpdateStatus(AccountStatus.ACTIVE) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                        ) {
                            Text("Reactivate Account")
                        }
                    }
                }
            }
        }

        SurfaceCard {
            Text("Ledger Postings (${postings.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            if (postings.isEmpty()) {
                Text("No ledger postings recorded yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                postings.take(15).forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            MonoCodeText("${p.journalEntryId} • ${p.side.label}")
                            Text(formatTimestamp(p.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        MoneyText(
                            paise = if (p.side == PostingSide.CREDIT) p.amountPaise else -p.amountPaise,
                            color = if (p.side == PostingSide.CREDIT) StatusSuccess else StatusError,
                            includeSign = true
                        )
                    }
                }
            }
        }

        SurfaceCard {
            Text("Recent Transactions (${transactions.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            if (transactions.isEmpty()) {
                Text("No transactions yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                transactions.take(10).forEachIndexed { i, tx ->
                    if (i > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTransaction(tx.id) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            MonoCodeText(tx.id)
                            Text(tx.reference, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(paise = tx.amountPaise)
                            Spacer(Modifier.height(2.dp))
                            TransactionStatusPill(tx.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateAccountDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf(customers.firstOrNull()) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPrimary,
        title = { Text("Create Virtual Account", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Provision a new INR Customer Wallet Liability account.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSecondary,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().clickable { dropdownOpen = true }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Associated Customer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(selectedCustomer?.fullName ?: "Select customer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }, containerColor = BgPrimary) {
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.fullName} (${c.referenceCode})") },
                                onClick = { selectedCustomer = c; dropdownOpen = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account label (optional)") },
                    placeholder = { Text("e.g. Operating Wallet INR") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedCustomer?.let { onCreate(it.id, accountName) } },
                enabled = selectedCustomer != null,
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
            ) {
                Text("Create Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
