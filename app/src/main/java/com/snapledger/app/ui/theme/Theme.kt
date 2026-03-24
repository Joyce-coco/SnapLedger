package com.snapledger.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFEC4899),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE4EC),
    secondary = Color(0xFFF472B6),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFFFFF0F3),
    surface = Color(0xFFFFFBFC),
    surfaceVariant = Color(0xFFFDE2E8),
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B),
    outline = Color(0xFFE8B4C0),
    error = Color(0xFFEF4444),
)

@Composable
fun SnapLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
