package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.domain.Customer
import com.example.domain.CustomerStatus
import com.example.domain.LedgerAccount
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
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CustomersScreen(
    customers: List<Customer>,
    accounts: List<LedgerAccount>,
    transactions: List<Transaction>,
    selectedCustomerId: String?,
    computeBalance: (String) -> Long,
    onSelectCustomer: (String?) -> Unit,
    onCreateCustomer: (name: String, email: String, segment: String) -> Unit,
    onUpdateCustomer: (id: String, name: String, segment: String, status: CustomerStatus) -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    if (selectedCustomerId != null) {
        val cust = customers.firstOrNull { it.id == selectedCustomerId }
        if (cust != null) {
            BackHandler { onSelectCustomer(null) }
            CustomerDetailView(
                customer = cust,
                accounts = accounts.filter { it.customerId == cust.id },
                transactions = transactions.filter { tx ->
                    val accIds = accounts.filter { it.customerId == cust.id }.map { it.id }.toSet()
                    tx.senderAccountId in accIds || tx.receiverAccountId in accIds
                },
                computeBalance = computeBalance,
                onBack = { onSelectCustomer(null) },
                onUpdateCustomer = { n, seg, st -> onUpdateCustomer(cust.id, n, seg, st) },
                onOpenAccount = onOpenAccount,
                onOpenTransaction = onOpenTransaction
            )
            return
        }
    }

    var search by remember { mutableStateOf("") }
    var filterIdx by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }

    val filtered = remember(customers, search, filterIdx) {
        customers.filter { c ->
            val matchStatus = when (filterIdx) {
                1 -> c.status == CustomerStatus.ACTIVE
                2 -> c.status == CustomerStatus.UNDER_REVIEW
                3 -> c.status == CustomerStatus.SUSPENDED
                else -> true
            }
            val q = search.trim().lowercase()
            val matchQuery = q.isEmpty() ||
                c.fullName.lowercase().contains(q) ||
                c.email.lowercase().contains(q) ||
                c.referenceCode.lowercase().contains(q)
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
            title = "Customers",
            subtitle = "Synthetic customer profiles linked to virtual INR wallets",
            trailing = {
                Button(
                    onClick = { showCreate = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Customer")
                }
            }
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search name, email, or reference code...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        FilterPillRow(
            options = listOf("All", "Active", "Under Review", "Suspended"),
            selectedIndex = filterIdx,
            onSelect = { filterIdx = it }
        )

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.Group,
                title = "No customers found",
                description = "Add a synthetic customer profile to get started."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { cust ->
                    val custAccounts = accounts.filter { it.customerId == cust.id }
                    val totalBal = custAccounts.sumOf { computeBalance(it.id) }
                    SurfaceCard(
                        onClick = { onSelectCustomer(cust.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cust.fullName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(cust.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                MonoCodeText("${cust.referenceCode} • ${cust.segment} • ${custAccounts.size} wallet(s)")
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(paise = totalBal, fontSizeSp = 15)
                                Spacer(Modifier.height(4.dp))
                                CustomerStatusPill(cust.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CustomerFormDialog(
            title = "Create Synthetic Customer",
            initialName = "",
            initialEmail = "",
            initialSegment = "Retail Wallet",
            emailEditable = true,
            onDismiss = { showCreate = false },
            onSave = { n, e, seg ->
                onCreateCustomer(n, e, seg)
                showCreate = false
            }
        )
    }
}

@Composable
private fun CustomerDetailView(
    customer: Customer,
    accounts: List<LedgerAccount>,
    transactions: List<Transaction>,
    computeBalance: (String) -> Long,
    onBack: () -> Unit,
    onUpdateCustomer: (String, String, CustomerStatus) -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to customers")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.fullName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${customer.email} • ${customer.segment}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            OutlinedButton(onClick = { showEdit = true }) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
        }

        CopyableIdChip(id = customer.id, label = customer.referenceCode)

        SurfaceCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Account Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    CustomerStatusPill(customer.status)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Behavioural Risk Score", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("${customer.riskScore}/100", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Created ${formatTimestamp(customer.createdAt)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        SurfaceCard {
            Text("Virtual Accounts (${accounts.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                Text("No virtual accounts assigned.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                accounts.forEachIndexed { idx, acc ->
                    if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAccount(acc.id) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(acc.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            MonoCodeText(acc.accountNumber)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(paise = computeBalance(acc.id))
                            AccountStatusPill(acc.status)
                        }
                    }
                }
            }
        }

        SurfaceCard {
            Text("Transaction History (${transactions.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Text("No transactions recorded for this customer.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                transactions.take(10).forEachIndexed { idx, tx ->
                    if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
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
                            TransactionStatusPill(tx.status)
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        CustomerFormDialog(
            title = "Edit Customer Profile",
            initialName = customer.fullName,
            initialEmail = customer.email,
            initialSegment = customer.segment,
            emailEditable = false,
            onDismiss = { showEdit = false },
            onSave = { n, _, seg ->
                onUpdateCustomer(n, seg, customer.status)
                showEdit = false
            }
        )
    }
}

@Composable
private fun CustomerStatusPill(status: CustomerStatus) {
    val (fg, bg) = when (status) {
        CustomerStatus.ACTIVE -> StatusSuccess to StatusSuccessTint
        CustomerStatus.UNDER_REVIEW -> StatusWarning to StatusWarningTint
        CustomerStatus.SUSPENDED -> StatusError to StatusErrorTint
    }
    StatusPill(status.label, fg, bg)
}

@Composable
private fun CustomerFormDialog(
    title: String,
    initialName: String,
    initialEmail: String,
    initialSegment: String,
    emailEditable: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var segment by remember { mutableStateOf(initialSegment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPrimary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name (synthetic)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = emailEditable,
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = segment,
                    onValueChange = { segment = it },
                    label = { Text("Segment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, segment) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
