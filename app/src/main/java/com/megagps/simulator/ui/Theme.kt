package com.megagps.simulator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF00796B), onPrimary = Color.White,
    secondary = Color(0xFF1E88E5), tertiary = Color(0xFFE53935),
    primaryContainer = Color(0xFFB2DFDB), onPrimaryContainer = Color(0xFF00201C),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF80CBC4), onPrimary = Color(0xFF003731),
    secondary = Color(0xFF90CAF9), tertiary = Color(0xFFEF9A9A),
    primaryContainer = Color(0xFF004D40), onPrimaryContainer = Color(0xFFB2DFDB),
)

@Composable
fun MegaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
