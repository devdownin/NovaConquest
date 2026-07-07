package com.novaempire.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


@Composable
fun NovaEmpireTheme(
    themeType: com.novaempire.core.domain.models.ThemeType = com.novaempire.core.domain.models.ThemeType.DEFAULT,
    darkTheme: Boolean = true, // Force dark theme for Noir Futurism
    content: @Composable () -> Unit
) {
    val colorScheme = ThemeManager.getColorSchemeForTheme(ThemeManager.getActiveTheme(themeType))
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
