package com.novaempire.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.novaempire.core.domain.models.ThemeType

/**
 * Thème effectivement appliqué (choix sauvegardé résolu contre le calendrier saisonnier).
 * Tout le monde doit lire *ça* plutôt que rappeler `ThemeManager.getActiveTheme()` : trois appelants
 * résolvaient le thème par trois chemins différents et pouvaient se retrouver en désaccord — la
 * carte tactique dessinait avec les réglages DEFAULT pendant qu'elle s'affichait aux couleurs
 * d'Halloween.
 */
val LocalThemeType = staticCompositionLocalOf { ThemeType.DEFAULT }

/** Réglages graphiques du thème actif ([LocalThemeType]). */
val LocalGraphicsConfig = staticCompositionLocalOf { ThemeManager.DEFAULT_GRAPHICS }

@Composable
fun NovaEmpireTheme(
    themeType: ThemeType = ThemeType.DEFAULT,
    content: @Composable () -> Unit
) {
    // Le thème ne dépend que de `themeType` et de la date : sans `remember`, chaque recomposition
    // de la racine (donc chaque changement de GameState) reconstruisait un ColorScheme neuf et
    // invalidait tous les lecteurs de MaterialTheme.colorScheme.
    val activeTheme = remember(themeType) { ThemeManager.getActiveTheme(themeType) }
    val colorScheme = remember(activeTheme) { ThemeManager.getColorSchemeForTheme(activeTheme) }
    val graphicsConfig = remember(activeTheme) { ThemeManager.getGraphicsConfig(activeTheme) }
    val typography = remember(colorScheme) { novaTypography(colorScheme) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(
        LocalThemeType provides activeTheme,
        LocalGraphicsConfig provides graphicsConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
