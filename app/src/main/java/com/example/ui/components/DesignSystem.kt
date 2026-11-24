package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountStatus
import com.example.domain.DailyVolumePoint
import com.example.domain.Money
import com.example.domain.RiskSeverity
import com.example.domain.TransactionStatus
import com.example.domain.TransactionType
import com.example.ui.BackendConnectionState
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorTint
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoTint
import com.example.ui.theme.StatusNeutralTint
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessTint
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningTint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(8.dp),
        color = BgPrimary,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun DemoEnvironmentBanner(
    backendState: BackendConnectionState,
    modifier: Modifier = Modifier
) {
    val bgColor = if (backendState.isConnected) StatusSuccessTint else StatusWarningTint
    val fgColor = if (backendState.isConnected) StatusSuccess else Color(0xFF9A6008)
    val label = if (backendState.isConnected) {
        "CONNECTED TO POSTGRESQL API • ${backendState.baseUrl}"
    } else {
        "DEMO ENVIRONMENT • SIMULATED PREVIEW MODE (IN-MEMORY, NON-PERSISTENT VIRTUAL INR)"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (backendState.isConnected) Icons.Outlined.VerifiedUser else Icons.Outlined.Science,
                contentDescription = "Environment status",
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun TransactionStatusPill(status: TransactionStatus) {
    val (fg, bg) = when (status) {
        TransactionStatus.SUCCEEDED -> StatusSuccess to StatusSuccessTint
        TransactionStatus.FAILED -> StatusError to StatusErrorTint
        TransactionStatus.REQUIRES_REVIEW -> StatusWarning to StatusWarningTint
        TransactionStatus.PROCESSING -> StatusInfo to StatusInfoTint
        TransactionStatus.REVERSED -> AccentIndigo to AccentIndigoTint
        TransactionStatus.CREATED -> TextSecondary to StatusNeutralTint
    }
    StatusPill(text = status.label, textColor = fg, backgroundColor = bg)
}

@Composable
fun AccountStatusPill(status: AccountStatus) {
    val (fg, bg) = when (status) {
        AccountStatus.ACTIVE -> StatusSuccess to StatusSuccessTint
        AccountStatus.SUSPENDED -> StatusWarning to StatusWarningTint
        AccountStatus.CLOSED -> StatusError to StatusErrorTint
    }
    StatusPill(text = status.label, textColor = fg, backgroundColor = bg)
}

@Composable
fun SeverityPill(severity: RiskSeverity) {
    val (fg, bg) = when (severity) {
        RiskSeverity.LOW -> StatusSuccess to StatusSuccessTint
        RiskSeverity.MEDIUM -> StatusWarning to StatusWarningTint
        RiskSeverity.HIGH -> Color(0xFFC95B1C) to StatusWarningTint
        RiskSeverity.CRITICAL -> StatusError to StatusErrorTint
    }
    StatusPill(text = severity.label.uppercase(Locale.US), textColor = fg, backgroundColor = bg)
}

@Composable
fun TransactionTypePill(type: TransactionType) {
    val (fg, bg) = when (type) {
        TransactionType.DEPOSIT -> StatusSuccess to StatusSuccessTint
        TransactionType.TRANSFER -> AccentIndigo to AccentIndigoTint
        TransactionType.WITHDRAWAL -> StatusInfo to StatusInfoTint
        TransactionType.REVERSAL -> StatusWarning to StatusWarningTint
    }
    StatusPill(text = type.label, textColor = fg, backgroundColor = bg)
}

@Composable
fun MoneyText(
    paise: Long,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSizeSp: Int = 14,
    bold: Boolean = true,
    includeSign: Boolean = false
) {
    Text(
        text = Money.formatInr(paise, includeSign = includeSign),
        modifier = modifier,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun MonoCodeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    fontSizeSp: Int = 12
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = fontSizeSp.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun CopyableIdChip(
    id: String,
    label: String? = null,
    onCopied: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = BgSecondary,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .clickable {
                clipboard.setText(AnnotatedString(id))
                onCopied?.invoke()
            }
            .testTag("copy_id_$id")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(
                text = id,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy $id",
                tint = TextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color = AccentIndigo,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun FilterPillRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) AccentIndigoTint else BgPrimary,
                border = BorderStroke(1.dp, if (selected) AccentIndigo else BorderSubtle),
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .testTag("filter_pill_$label")
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) AccentIndigo else TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector = Icons.Outlined.Info,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BgSecondary)
                    .border(1.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun VolumeBarChart(
    series: List<DailyVolumePoint>,
    modifier: Modifier = Modifier
) {
    val maxVal = (series.maxOfOrNull { it.volumePaise } ?: 1L).coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (series.isEmpty()) return@Canvas
            val count = series.size
            val gap = 14.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(8f)
            // Subtle baseline grid lines
            for (i in 0..2) {
                val y = size.height * (i / 2f)
                drawLine(
                    color = BorderSubtle,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            series.forEachIndexed { idx, pt ->
                val ratio = pt.volumePaise.toFloat() / maxVal.toFloat()
                val barHeight = (size.height * 0.9f * ratio).coerceAtLeast(if (pt.volumePaise > 0) 6.dp.toPx() else 2.dp.toPx())
                val x = idx * (barWidth + gap)
                val y = size.height - barHeight
                drawRoundRect(
                    color = if (pt.volumePaise > 0) AccentIndigo else BorderSubtle,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            series.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SuccessVsFailureChart(
    series: List<DailyVolumePoint>,
    modifier: Modifier = Modifier
) {
    val maxCount = (series.maxOfOrNull { maxOf(it.succeededCount, it.failedCount) } ?: 1).coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (series.isEmpty()) return@Canvas
            val groupCount = series.size
            val groupGap = 12.dp.toPx()
            val groupWidth = ((size.width - groupGap * (groupCount - 1)) / groupCount).coerceAtLeast(10f)
            val barWidth = (groupWidth - 4.dp.toPx()) / 2f

            drawLine(
                color = BorderSubtle,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            series.forEachIndexed { idx, pt ->
                val xStart = idx * (groupWidth + groupGap)
                val sH = (size.height * 0.9f * (pt.succeededCount.toFloat() / maxCount.toFloat()))
                    .coerceAtLeast(if (pt.succeededCount > 0) 6.dp.toPx() else 2.dp.toPx())
                val fH = (size.height * 0.9f * (pt.failedCount.toFloat() / maxCount.toFloat()))
                    .coerceAtLeast(if (pt.failedCount > 0) 6.dp.toPx() else 2.dp.toPx())

                drawRoundRect(
                    color = StatusSuccess,
                    topLeft = Offset(xStart, size.height - sH),
                    size = Size(barWidth, sH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = StatusError,
                    topLeft = Offset(xStart + barWidth + 4.dp.toPx(), size.height - fH),
                    size = Size(barWidth, fH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            series.forEach { pt ->
                Text(pt.label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun ConfirmationAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPrimary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) StatusError else AccentIndigo
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

fun formatTimestamp(ms: Long): String {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
    return fmt.format(Date(ms))
}

fun formatShortTime(ms: Long): String {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
    return fmt.format(Date(ms))
}
