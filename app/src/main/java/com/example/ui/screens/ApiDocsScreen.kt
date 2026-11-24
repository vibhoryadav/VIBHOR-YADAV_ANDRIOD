package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CodeSurfaceBg
import com.example.ui.theme.CodeSurfaceText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class EndpointDoc(
    val method: String,
    val path: String,
    val summary: String,
    val headers: String,
    val requestSchema: String,
    val responseSchema: String
)

@Composable
fun ApiDocsScreen() {
    val docs = remember {
        listOf(
            EndpointDoc(
                "GET", "/api/health",
                "Check API and PostgreSQL database connectivity.",
                "None", "N/A",
                """{ "status": "ok", "database": "postgres", "currency": "INR" }"""
            ),
            EndpointDoc(
                "POST", "/api/customers",
                "Create a synthetic customer profile scoped to the active workspace.",
                "Authorization: Bearer <key>",
                """{ "fullName": "string", "email": "string", "segment": "string" }""",
                """{ "id": "cus_...", "referenceCode": "CUST-1005", "status": "ACTIVE" }"""
            ),
            EndpointDoc(
                "POST", "/api/accounts",
                "Create a Customer Wallet Liability account (INR) with unique generated account number.",
                "Authorization: Bearer <key>",
                """{ "customerId": "cus_...", "name": "Operating Wallet" }""",
                """{ "id": "acc_...", "accountNumber": "LL...", "balancePaise": 0 }"""
            ),
            EndpointDoc(
                "POST", "/api/transfers",
                "Execute an atomic double-entry transfer between two active INR accounts.",
                "Authorization: Bearer <key>\nIdempotency-Key: <unique-key>",
                """{ "senderAccountId": "acc_...", "receiverAccountId": "acc_...", "amountInr": "250.00", "reference": "INV-1" }""",
                """{ "id": "tx_...", "status": "SUCCEEDED", "amountPaise": 25000, "journalEntryId": "jrn_..." }"""
            ),
            EndpointDoc(
                "GET", "/api/ledger/journals",
                "Retrieve immutable balanced journal entries and debit/credit posting counts.",
                "Authorization: Bearer <key>",
                "Query: ?limit=25&offset=0",
                """{ "data": [{ "id": "jrn_...", "balanced": true, "postingsCount": 2 }] }"""
            ),
            EndpointDoc(
                "POST", "/api/reconciliation/run",
                "Run automated reconciliation comparing ledger-derived balances against snapshots.",
                "Authorization: Bearer <key>",
                "{}",
                """{ "id": "rec_...", "status": "MATCHED", "mismatchedAccounts": 0 }"""
            )
        )
    }

    var expandedPath by remember { mutableStateOf<String?>(docs.first().path) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "API Documentation (OpenAPI 3.1)",
            subtitle = "Authentication, idempotency semantics, pagination, rate limits, and error schemas"
        )

        SurfaceCard {
            Text("Core Platform Conventions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            ConventionItem("Authentication", "Cookie session with CSRF token for dashboard calls, or Authorization: Bearer ll_test_... for API keys.")
            ConventionItem("Idempotency", "All POST /transfers, /deposits, and /withdrawals require Idempotency-Key. Same key + same payload replays original response; altered payload returns HTTP 409 Conflict.")
            ConventionItem("Money Representation", "Inputs accept INR decimal strings (max 2 decimal places). Internal storage and JSON responses expose integer paise (BIGINT).")
            ConventionItem("Rate Limits", "120 requests/min per API key; 5 login attempts/min per email (HTTP 429 on breach).")
            ConventionItem("Standard Errors", "400 Invalid Request • 401 Unauthorized • 403 Forbidden • 404 Not Found • 409 Conflict • 422 Financial Validation • 429 Rate Limited.")
        }

        docs.forEach { doc ->
            val expanded = expandedPath == "${doc.method}_${doc.path}"
            SurfaceCard(
                onClick = { expandedPath = if (expanded) null else "${doc.method}_${doc.path}" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = doc.method,
                            textColor = if (doc.method == "GET") StatusSuccess else AccentIndigo,
                            backgroundColor = if (doc.method == "GET") StatusSuccessTint else AccentIndigoTint
                        )
                        MonoCodeText(doc.path, color = TextPrimary, fontSizeSp = 13)
                    }
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(doc.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(Modifier.height(8.dp))
                    Text("Headers", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    MonoCodeText(doc.headers)
                    Spacer(Modifier.height(6.dp))
                    Text("Request Schema", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Surface(color = CodeSurfaceBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = doc.requestSchema,
                            color = CodeSurfaceText,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Response (200/201)", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Surface(color = CodeSurfaceBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = doc.responseSchema,
                            color = CodeSurfaceText,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConventionItem(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AccentIndigo)
        Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
