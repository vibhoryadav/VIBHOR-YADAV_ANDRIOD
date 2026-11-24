package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LedgerLabLightColorScheme = lightColorScheme(
    primary = AccentIndigo,
    onPrimary = BgPrimary,
    primaryContainer = AccentIndigoTint,
    onPrimaryContainer = AccentIndigoDark,
    secondary = TextSecondary,
    onSecondary = BgPrimary,
    secondaryContainer = BgSecondary,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusInfo,
    onTertiary = BgPrimary,
    tertiaryContainer = StatusInfoTint,
    onTertiaryContainer = TextPrimary,
    background = BgSecondary,
    onBackground = TextPrimary,
    surface = BgPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgSecondary,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderStrong,
    error = StatusError,
    onError = BgPrimary,
    errorContainer = StatusErrorTint,
    onErrorContainer = StatusError
)

private val LedgerLabShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun LedgerLabTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LedgerLabLightColorScheme,
        typography = LedgerLabTypography,
        shapes = LedgerLabShapes,
        content = content
    )
}
