package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.AuditEventType
import com.example.domain.AuditLogEntry
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FilterPillRow
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuditLogsScreen(
    auditLogs: List<AuditLogEntry>
) {
    var search by remember { mutableStateOf("") }
    var filterIdx by remember { mutableIntStateOf(0) }

    val filtered = remember(auditLogs, search, filterIdx) {
        auditLogs.filter { log ->
            val matchType = when (filterIdx) {
                1 -> log.eventType in setOf(AuditEventType.LOGIN, AuditEventType.LOGOUT, AuditEventType.AUTH_FAILED)
                2 -> log.eventType in setOf(AuditEventType.TRANSACTION_CREATED, AuditEventType.TRANSACTION_APPROVED, AuditEventType.TRANSACTION_REJECTED, AuditEventType.TRANSACTION_REVERSED)
                3 -> log.eventType in setOf(AuditEventType.API_KEY_CREATED, AuditEventType.API_KEY_REVOKED, AuditEventType.WEBHOOK_CREATED)
                4 -> log.eventType in setOf(AuditEventType.WORKSPACE_ROLE_CHANGED, AuditEventType.RECONCILIATION_RUN, AuditEventType.ACCOUNT_CREATED)
                else -> true
            }
            val q = search.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                log.actorEmail.lowercase().contains(q) ||
                log.eventType.label.lowercase().contains(q) ||
                log.resourceId.lowercase().contains(q)
            matchType && matchSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Immutable Audit Logs",
            subtitle = "Append-only security & financial event trail (credentials redacted)"
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search actor, event type, or resource ID...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        FilterPillRow(
            options = listOf("All", "Auth", "Transactions", "API & Webhooks", "Governance"),
            selectedIndex = filterIdx,
            onSelect = { filterIdx = it }
        )

        if (filtered.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.History,
                title = "No audit events",
                description = "Actions across auth, ledger, and API keys appear here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { log ->
                    val isAuthFail = log.eventType == AuditEventType.AUTH_FAILED
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = log.eventType.label,
                                textColor = if (isAuthFail) StatusError else AccentIndigo,
                                backgroundColor = if (isAuthFail) StatusErrorTint else AccentIndigoTint
                            )
                            Text(formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Actor: ${log.actorEmail} • IP: ${log.sourceIp}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        MonoCodeText("${log.resourceType}:${log.resourceId} (${log.id})")
                        if (log.metadata.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            MonoCodeText(log.metadata.entries.joinToString(" | ") { "${it.key}=${it.value}" })
                        }
                    }
                }
            }
        }
    }
}
