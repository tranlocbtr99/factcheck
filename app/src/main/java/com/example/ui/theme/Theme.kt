package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Dark =
    darkColorScheme(
        primary = Color(0xFF78ADFF),
        onPrimary = Color(0xFF092341),
        background = Color(0xFF101318),
        onBackground = Color(0xFFF3F5F8),
        surface = Color(0xFF1C2129),
        onSurface = Color(0xFFF3F5F8),
        surfaceVariant = Color(0xFF282F3A),
        onSurfaceVariant = Color(0xFFB9C3D0),
        outline = Color(0xFF637084),
    )
private val Light =
    lightColorScheme(
        primary = Color(0xFF0066D6),
        onPrimary = Color.White,
        background = Color(0xFFF2F4F8),
        onBackground = Color(0xFF172235),
        surface = Color.White,
        onSurface = Color(0xFF172235),
        surfaceVariant = Color(0xFFE7EDF5),
        onSurfaceVariant = Color(0xFF526176),
        outline = Color(0xFF8593A5),
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = Typography,
        content = content,
    )
}
