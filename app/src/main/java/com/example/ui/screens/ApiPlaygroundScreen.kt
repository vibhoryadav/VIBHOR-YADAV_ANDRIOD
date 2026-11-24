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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.ApiRequestResult
import com.example.domain.Customer
import com.example.domain.LedgerAccount
import com.example.engine.CryptoUtils
import com.example.ui.components.FilterPillRow
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatShortTime
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CodeSurfaceBg
import com.example.ui.theme.CodeSurfaceText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class ApiPreset(
    val method: String,
    val path: String,
    val label: String,
    val bodyTemplate: String
)

@Composable
fun ApiPlaygroundScreen(
    accounts: List<LedgerAccount>,
    customers: List<Customer>,
    latestResult: ApiRequestResult?,
    history: List<ApiRequestResult>,
    onSendRequest: (method: String, path: String, headers: Map<String, String>, bodyJson: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val firstWallet = accounts.firstOrNull { !it.isSystemAccount }?.id ?: "acc_ananya_main"
    val secondWallet = accounts.filter { !it.isSystemAccount }.getOrNull(1)?.id ?: "acc_vikram_main"
    val firstCust = customers.firstOrNull()?.id ?: "cus_ananya"

    val presets = remember(firstWallet, secondWallet, firstCust) {
        listOf(
            ApiPreset("GET", "/api/health", "Health Check", ""),
            ApiPreset("GET", "/api/overview/metrics", "Overview Metrics", ""),
            ApiPreset("GET", "/api/accounts", "List Accounts", ""),
            ApiPreset("POST", "/api/customers", "Create Customer", "{\n  \"fullName\": \"Siddharth Nair\",\n  \"email\": \"siddharth@example.in\",\n  \"segment\": \"Fintech Sandbox\"\n}"),
            ApiPreset("POST", "/api/accounts", "Create Account", "{\n  \"customerId\": \"$firstCust\",\n  \"name\": \"API Created INR Wallet\"\n}"),
            ApiPreset("POST", "/api/deposits", "Deposit Funds", "{\n  \"accountId\": \"$firstWallet\",\n  \"amountInr\": \"1250.00\",\n  \"reference\": \"API-DEP-01\",\n  \"description\": \"Playground simulated deposit\"\n}"),
            ApiPreset("POST", "/api/transfers", "Create Transfer", "{\n  \"senderAccountId\": \"$firstWallet\",\n  \"receiverAccountId\": \"$secondWallet\",\n  \"amountInr\": \"350.00\",\n  \"reference\": \"API-TRF-01\",\n  \"description\": \"Playground atomic transfer\"\n}"),
            ApiPreset("POST", "/api/withdrawals", "Withdraw Funds", "{\n  \"accountId\": \"$firstWallet\",\n  \"amountInr\": \"100.00\",\n  \"reference\": \"API-WDR-01\",\n  \"description\": \"Playground withdrawal\"\n}"),
            ApiPreset("GET", "/api/transactions", "Fetch Transactions", ""),
            ApiPreset("GET", "/api/ledger/journals", "Fetch Ledger Journals", ""),
            ApiPreset("GET", "/api/ledger/trial-balance", "Trial Balance", ""),
            ApiPreset("POST", "/api/reconciliation/run", "Run Reconciliation", "{}")
        )
    }

    var method by remember { mutableStateOf("POST") }
    var path by remember { mutableStateOf("/api/transfers") }
    var idempotencyKey by remember { mutableStateOf(CryptoUtils.generatePrefixedId("idem_api")) }
    var bodyJson by remember { mutableStateOf(presets[6].bodyTemplate) }
    var presetDropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "API Playground",
            subtitle = "Interactive REST console calling the real LedgerLab engine & validation layer"
        )

        SurfaceCard {
            Text("Select Endpoint Preset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BgSecondary,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().clickable { presetDropdownOpen = true }
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$method $path", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Change ▾", style = MaterialTheme.typography.labelMedium, color = AccentIndigo)
                }
                DropdownMenu(expanded = presetDropdownOpen, onDismissRequest = { presetDropdownOpen = false }, containerColor = BgPrimary) {
                    presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.method} ${p.path} — ${p.label}", style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                method = p.method
                                path = p.path
                                bodyJson = p.bodyTemplate
                                presetDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            FilterPillRow(
                options = listOf("GET", "POST"),
                selectedIndex = if (method == "GET") 0 else 1,
                onSelect = { method = if (it == 0) "GET" else "POST" }
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Endpoint Path") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = idempotencyKey,
                    onValueChange = { idempotencyKey = it },
                    label = { Text("Header: Idempotency-Key") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { idempotencyKey = CryptoUtils.generatePrefixedId("idem_api") }) {
                    Text("Regenerate")
                }
            }

            if (method == "POST") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bodyJson,
                    onValueChange = { bodyJson = it },
                    label = { Text("JSON Request Body") },
                    minLines = 5,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val curl = "curl -X $method http://localhost:3000$path -H 'Idempotency-Key: $idempotencyKey' -H 'Content-Type: application/json' -d '$bodyJson'"
                        clipboard.setText(AnnotatedString(curl))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Request")
                }
                Button(
                    onClick = {
                        onSendRequest(
                            method,
                            path,
                            mapOf("Content-Type" to "application/json", "Idempotency-Key" to idempotencyKey),
                            bodyJson
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    modifier = Modifier.weight(1f).testTag("send_api_request_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Send Request")
                }
            }
        }

        // Response Viewer
        if (latestResult != null) {
            SurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ok = latestResult.statusCode in 200..299
                        StatusPill(
                            text = "HTTP ${latestResult.statusCode}",
                            textColor = if (ok) StatusSuccess else StatusError,
                            backgroundColor = if (ok) StatusSuccessTint else StatusErrorTint
                        )
                        MonoCodeText("${latestResult.durationMs} ms")
                    }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(latestResult.responseBody)) }
                    ) {
                        Text("Copy Response")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = CodeSurfaceBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = latestResult.responseBody,
                        color = CodeSurfaceText,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Request History
        if (history.isNotEmpty()) {
            SurfaceCard {
                Text("Request History (${history.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                history.take(8).forEachIndexed { idx, item ->
                    if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MonoCodeText("${item.method} ${item.path}", color = TextPrimary)
                            Text("${item.durationMs} ms • ${formatShortTime(item.timestamp)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        StatusPill(
                            text = "${item.statusCode}",
                            textColor = if (item.statusCode in 200..299) StatusSuccess else StatusError,
                            backgroundColor = if (item.statusCode in 200..299) StatusSuccessTint else StatusErrorTint
                        )
                    }
                }
            }
        }
    }
}
