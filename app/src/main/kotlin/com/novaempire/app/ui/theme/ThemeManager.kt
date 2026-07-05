package com.novaempire.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.novaempire.core.domain.models.ThemeType
import java.util.Calendar

object ThemeManager {
    val DEFAULT = darkColorScheme(
        primary = NeonCyan,
        secondary = NeonRed,
        tertiary = NeonOrange,
        background = VoidBlack,
        surface = SurfaceDark,
        surfaceVariant = SurfaceLight,
        onPrimary = TextPrimary,
        onSecondary = TextPrimary,
        onTertiary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary
    )

    val HALLOWEEN = darkColorScheme(
        primary = Color(0xFFD35400),
        secondary = Color(0xFF8E44AD),
        tertiary = Color(0xFFF39C12),
        background = Color(0xFF1A0F0A),
        surface = Color(0xFF2E190E),
        surfaceVariant = Color(0xFF452410),
        onPrimary = Color(0xFFFFDAB9),
        onSecondary = Color(0xFFFFDAB9),
        onTertiary = Color(0xFFFFDAB9),
        onBackground = Color(0xFFFFDAB9),
        onSurface = Color(0xFFFFDAB9),
        onSurfaceVariant = Color(0xFFB09989)
    )

    val WINTER = darkColorScheme(
        primary = Color(0xFF85C1E9),
        secondary = Color(0xFF3498DB),
        tertiary = Color(0xFFD6EAF8),
        background = Color(0xFF0F1A24),
        surface = Color(0xFF1B2A38),
        surfaceVariant = Color(0xFF2C3E50),
        onPrimary = Color(0xFFEBF5FB),
        onSecondary = Color(0xFFEBF5FB),
        onTertiary = Color(0xFFEBF5FB),
        onBackground = Color(0xFFEBF5FB),
        onSurface = Color(0xFFEBF5FB),
        onSurfaceVariant = Color(0xFFA9C2D4)
    )

    fun getColorSchemeForTheme(themeType: ThemeType): androidx.compose.material3.ColorScheme {
        return when (themeType) {
            ThemeType.HALLOWEEN -> HALLOWEEN
            ThemeType.WINTER -> WINTER
            else -> DEFAULT
        }
    }

    fun getActiveTheme(savedTheme: ThemeType? = null): ThemeType {
        if (savedTheme != null && savedTheme != ThemeType.DEFAULT) return savedTheme

        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return when {
            (month == Calendar.OCTOBER && day >= 25) || (month == Calendar.NOVEMBER && day <= 5) -> ThemeType.HALLOWEEN
            (month == Calendar.DECEMBER && day >= 20) || (month == Calendar.JANUARY && day <= 5) -> ThemeType.WINTER
            else -> ThemeType.DEFAULT
        }
    }
}
