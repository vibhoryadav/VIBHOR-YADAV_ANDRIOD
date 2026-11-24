package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.ApiKeyCreationResult
import com.example.domain.ApiKeyRecord
import com.example.ui.components.ConfirmationAlertDialog
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ApiKeysScreen(
    apiKeys: List<ApiKeyRecord>,
    createdKeyOnce: ApiKeyCreationResult?,
    onCreateKey: (name: String, scopes: List<String>) -> Unit,
    onRevokeKey: (String) -> Unit,
    onDismissKeyModal: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<ApiKeyRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "API Keys",
            subtitle = "SHA-256 hashed credentials • Full key is displayed only once upon creation",
            trailing = {
                Button(
                    onClick = { showCreate = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Generate Key")
                }
            }
        )

        if (apiKeys.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.Key,
                title = "No API keys created",
                description = "Generate a scoped test API key for programmatic access."
            )
        } else {
            apiKeys.forEach { key ->
                val revoked = key.revokedAt != null
                SurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(key.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            MonoCodeText("${key.keyPrefix}••••••••••••••••")
                            Spacer(Modifier.height(4.dp))
                            Text("Scopes: ${key.scopes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("Created: ${formatTimestamp(key.createdAt)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = "Last used: ${key.lastUsedAt?.let { formatTimestamp(it) } ?: "Never"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                text = if (revoked) "REVOKED" else "ACTIVE",
                                textColor = if (revoked) StatusError else StatusSuccess,
                                backgroundColor = if (revoked) StatusErrorTint else StatusSuccessTint
                            )
                            if (!revoked) {
                                OutlinedButton(onClick = { revokeTarget = key }) {
                                    Text("Revoke", color = StatusError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        val availableScopes = listOf("read:ledger", "write:transfers", "read:risk", "write:reconciliation")
        var name by remember { mutableStateOf("") }
        var selectedScopes by remember { mutableStateOf(setOf("read:ledger", "write:transfers")) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = BgPrimary,
            title = { Text("Generate Test API Key", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Key Name") },
                        placeholder = { Text("e.g. CI Integration Runner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Permissions & Scopes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    availableScopes.forEach { scope ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = scope in selectedScopes,
                                onCheckedChange = { checked ->
                                    selectedScopes = if (checked) selectedScopes + scope else selectedScopes - scope
                                }
                            )
                            Text(scope, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateKey(name, selectedScopes.toList()); showCreate = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (createdKeyOnce != null) {
        AlertDialog(
            onDismissRequest = onDismissKeyModal,
            containerColor = BgPrimary,
            title = { Text("Save Your API Key (Shown Once)", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "LedgerLab stores only the SHA-256 hash of this key. Copy it now — you cannot view it again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    CopyableIdChip(id = createdKeyOnce.plaintextKeyOnce, label = "Key")
                }
            },
            confirmButton = {
                Button(onClick = onDismissKeyModal, colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)) {
                    Text("I've saved it")
                }
            }
        )
    }

    if (revokeTarget != null) {
        val target = revokeTarget!!
        ConfirmationAlertDialog(
            title = "Revoke API Key?",
            message = "Requests using '${target.name}' (${target.keyPrefix}...) will immediately fail with HTTP 401.",
            confirmLabel = "Revoke Key",
            isDestructive = true,
            onConfirm = { onRevokeKey(target.id); revokeTarget = null },
            onDismiss = { revokeTarget = null }
        )
    }
}
