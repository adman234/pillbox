package com.adman234.pillbox.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF1F6F78)
private val Coral = Color(0xFFFF7A59)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Coral,
    tertiary = Coral,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3DD),
    onPrimary = Color(0xFF00363C),
    secondary = Color(0xFFFFB4A1),
    tertiary = Color(0xFFFFB4A1),
)

@Composable
fun PillboxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
