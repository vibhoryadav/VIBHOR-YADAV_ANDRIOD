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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.Workspace
import com.example.domain.WorkspaceMember
import com.example.domain.WorkspaceRole
import com.example.ui.BackendConnectionState
import com.example.ui.components.ConfirmationAlertDialog
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    activeWorkspace: Workspace?,
    members: List<WorkspaceMember>,
    currentUserId: String,
    backendState: BackendConnectionState,
    onUpdateRole: (targetUserId: String, role: WorkspaceRole) -> Unit,
    onCheckBackend: (String) -> Unit,
    onResetSandbox: () -> Unit
) {
    var serverUrl by remember(backendState.baseUrl) { mutableStateOf(backendState.baseUrl) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Workspace & Backend Settings",
            subtitle = "RBAC access control, PostgreSQL backend connectivity, and sandbox management"
        )

        DemoEnvironmentBanner(backendState = backendState)

        // PostgreSQL Backend Connector
        SurfaceCard {
            Text("PostgreSQL Backend API Endpoint", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Connect to a running LedgerLab Node.js + PostgreSQL server (see db/migrations/001_initial_schema.sql). Until connected, all ledger activity runs in transparent non-persistent Preview Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Backend Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (backendState.isConnected) "CONNECTED" else "PREVIEW MODE",
                    textColor = if (backendState.isConnected) StatusSuccess else StatusWarning,
                    backgroundColor = if (backendState.isConnected) StatusSuccessTint else StatusWarningTint
                )
                Button(
                    onClick = { onCheckBackend(serverUrl) },
                    enabled = !backendState.isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text(if (backendState.isChecking) "Checking..." else "Test /api/health")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(backendState.statusMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        // Workspace Members & RBAC
        SurfaceCard {
            Text("Workspace Members & RBAC (${activeWorkspace?.name ?: ""})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Owner & Admin can manage roles and approve risk holds. Viewer is strictly read-only.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            members.forEachIndexed { idx, member ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                var roleMenuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.userName + if (member.userId == currentUserId) " (You)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        MonoCodeText(member.userEmail)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSecondary,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable { roleMenuOpen = true }
                    ) {
                        Text(
                            text = "${member.role.label} ▾",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentIndigo,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        DropdownMenu(
                            expanded = roleMenuOpen,
                            onDismissRequest = { roleMenuOpen = false },
                            containerColor = BgPrimary
                        ) {
                            WorkspaceRole.entries.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.label) },
                                    onClick = {
                                        roleMenuOpen = false
                                        onUpdateRole(member.userId, role)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Security Architecture Summary & Reset
        SurfaceCard {
            Text("Financial & Security Invariants", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("• Double-Entry: Every posted journal enforces SUM(Debits) == SUM(Credits).", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("• Integer Paise: Long 64-bit paise arithmetic; zero floating-point rounding.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("• Concurrency: Deterministic sorted per-account locking prevents overdraft races.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("• Idempotency: SHA-256 request payload hashing returns 409 Conflict on key mismatch.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("• Immutability: Posted ledger lines cannot be mutated; only opposing reversals are allowed.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                border = BorderStroke(1.dp, StatusError)
            ) {
                Text("Reset Preview Sandbox to Seed State", color = StatusError)
            }
        }
    }

    if (confirmReset) {
        ConfirmationAlertDialog(
            title = "Reset Preview Sandbox?",
            message = "This will restore the initial seeded INR accounts, journals, risk alerts, and webhook logs.",
            confirmLabel = "Reset Sandbox",
            isDestructive = true,
            onConfirm = { confirmReset = false; onResetSandbox() },
            onDismiss = { confirmReset = false }
        )
    }
}
