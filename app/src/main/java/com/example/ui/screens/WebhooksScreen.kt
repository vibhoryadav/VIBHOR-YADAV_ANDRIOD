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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.domain.WebhookDelivery
import com.example.domain.WebhookEndpoint
import com.example.domain.WebhookEvent
import com.example.domain.WebhookEventType
import com.example.engine.CryptoUtils
import com.example.ui.components.CopyableIdChip
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.SurfaceCard
import com.example.ui.components.formatShortTime
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
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

@Composable
fun WebhooksScreen(
    endpoints: List<WebhookEndpoint>,
    events: List<WebhookEvent>,
    deliveries: List<WebhookDelivery>,
    createdSecretOnce: Pair<String, String>?,
    getVerificationSecret: (String) -> String?,
    onCreateEndpoint: (url: String, description: String, events: Set<WebhookEventType>) -> Unit,
    onRetryDelivery: (String) -> Unit,
    onDismissSecretModal: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var inspectEvent by remember { mutableStateOf<WebhookEvent?>(null) }

    // Interactive HMAC-SHA256 Signature Verifier state
    val sampleEvent = events.firstOrNull()
    val sampleDelivery = deliveries.firstOrNull { it.eventId == sampleEvent?.id }
    val sampleSecret = sampleDelivery?.let { getVerificationSecret(it.endpointId) } ?: "whsec_sandbox_demo_key_9182"
    var verifySecret by remember(sampleSecret) { mutableStateOf(sampleSecret) }
    var verifySig by remember(sampleEvent) { mutableStateOf(sampleEvent?.signatureHeader ?: "") }
    var verifyPayload by remember(sampleEvent) { mutableStateOf(sampleEvent?.payloadJson ?: "") }
    var verifyResult by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Webhooks & Transactional Outbox",
            subtitle = "HMAC-SHA256 signed event delivery (Internal Simulated Sandbox)",
            trailing = {
                Button(
                    onClick = { showCreate = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Endpoint")
                }
            }
        )

        // Endpoints
        SurfaceCard {
            Text("Configured Endpoints (${endpoints.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            endpoints.forEachIndexed { idx, ep ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ep.description, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        MonoCodeText(ep.url)
                        Text("Secret: ${ep.signingSecretPreview} • ${ep.subscribedEvents.size} events", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    StatusPill(
                        text = if (ep.enabled) "ACTIVE" else "DISABLED",
                        textColor = StatusSuccess,
                        backgroundColor = StatusSuccessTint
                    )
                }
            }
        }

        // Delivery Attempts
        SurfaceCard {
            Text("Simulated Delivery Attempts (${deliveries.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            deliveries.take(10).forEachIndexed { idx, dlv ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MonoCodeText("${dlv.eventId} -> ${dlv.endpointUrl}")
                        Text(
                            "Attempts: ${dlv.attemptCount} • ${formatShortTime(dlv.deliveredAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = "HTTP ${dlv.responseStatus}",
                            textColor = if (dlv.succeeded) StatusSuccess else StatusError,
                            backgroundColor = if (dlv.succeeded) StatusSuccessTint else StatusErrorTint
                        )
                        if (!dlv.succeeded) {
                            OutlinedButton(onClick = { onRetryDelivery(dlv.id) }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Retry delivery")
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        // Event History
        SurfaceCard {
            Text("Outbox Event History (${events.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Tap an event to inspect payload JSON & HMAC-SHA256 signature.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            events.take(10).forEachIndexed { idx, evt ->
                if (idx > 0) HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { inspectEvent = evt }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatusPill(evt.eventType.eventName, AccentIndigo, AccentIndigoTint)
                        Spacer(Modifier.height(3.dp))
                        MonoCodeText("${evt.id} • ${evt.resourceId}")
                    }
                    Text(formatShortTime(evt.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        // Webhook Signature Verification Utility
        SurfaceCard {
            Text("HMAC-SHA256 Signature Verification Utility", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Verify LedgerLab-Signature (t=...,v1=...) against raw webhook JSON payload.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = verifySecret,
                onValueChange = { verifySecret = it; verifyResult = null },
                label = { Text("Endpoint Signing Secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = verifySig,
                onValueChange = { verifySig = it; verifyResult = null },
                label = { Text("Signature Header (t=...,v1=...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = verifyPayload,
                onValueChange = { verifyPayload = it; verifyResult = null },
                label = { Text("Raw JSON Payload") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { verifyResult = CryptoUtils.verifyWebhookSignature(verifySecret, verifySig, verifyPayload) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Verify Signature")
                }
                if (verifyResult != null) {
                    StatusPill(
                        text = if (verifyResult == true) "SIGNATURE VALID" else "SIGNATURE INVALID",
                        textColor = if (verifyResult == true) StatusSuccess else StatusError,
                        backgroundColor = if (verifyResult == true) StatusSuccessTint else StatusErrorTint
                    )
                }
            }
        }
    }

    if (inspectEvent != null) {
        val evt = inspectEvent!!
        AlertDialog(
            onDismissRequest = { inspectEvent = null },
            containerColor = BgPrimary,
            title = { Text(evt.eventType.eventName, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Signature Header", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    MonoCodeText(evt.signatureHeader)
                    Text("Payload JSON", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Surface(color = CodeSurfaceBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = evt.payloadJson,
                            color = CodeSurfaceText,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectEvent = null }) { Text("Close") }
            }
        )
    }

    if (showCreate) {
        var url by remember { mutableStateOf("https://listener.sandbox.local/webhook") }
        var desc by remember { mutableStateOf("Custom Simulated Consumer") }
        var selectedEvents by remember { mutableStateOf(setOf(WebhookEventType.TRANSACTION_SUCCEEDED, WebhookEventType.TRANSACTION_FAILED)) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = BgPrimary,
            title = { Text("Create Webhook Endpoint", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Subscribed Events", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    WebhookEventType.entries.forEach { ev ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ev in selectedEvents,
                                onCheckedChange = { checked ->
                                    selectedEvents = if (checked) selectedEvents + ev else selectedEvents - ev
                                }
                            )
                            Text(ev.eventName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateEndpoint(url, desc, selectedEvents); showCreate = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (createdSecretOnce != null) {
        AlertDialog(
            onDismissRequest = onDismissSecretModal,
            containerColor = BgPrimary,
            title = { Text("Webhook Signing Secret (Shown Once)", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Store this HMAC-SHA256 signing secret securely. Only its hash is retained.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    CopyableIdChip(id = createdSecretOnce.second, label = "Secret")
                }
            },
            confirmButton = {
                Button(onClick = onDismissSecretModal, colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)) {
                    Text("Done")
                }
            }
        )
    }
}
