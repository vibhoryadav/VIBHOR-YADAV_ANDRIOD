package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.BackendConnectionState
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.LedgerLabMark
import com.example.ui.components.SurfaceCard
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    authError: String?,
    isBusy: Boolean,
    backendState: BackendConnectionState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onEnterDemoSandbox: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("aarav@ledgerlab.dev") }
    var password by remember { mutableStateOf("LedgerLab@2026") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LedgerLabMark()
                Column {
                    Text(
                        text = "LedgerLab",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Financial infrastructure. Made understandable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            DemoEnvironmentBanner(backendState = backendState)

            SurfaceCard {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = BgPrimary,
                    contentColor = AccentIndigo
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("Sign in") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("Create account") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (tabIndex == 1) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("register_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Work email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                )

                if (!authError.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = StatusErrorTint,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (tabIndex == 0) onLogin(email, password)
                        else onRegister(name, email, password)
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_button")
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(if (tabIndex == 0) "Sign in to Workspace" else "Register & Provision Workspace")
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Quick Portfolio Walkthrough",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Launch pre-seeded INR sandbox with double-entry journals, risk alerts, reversals, and RBAC actor switching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onEnterDemoSandbox,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AccentIndigo),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("enter_demo_sandbox_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AccentIndigo)
                    Text(
                        text = "Enter Demo Sandbox (Owner)",
                        color = AccentIndigo,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
