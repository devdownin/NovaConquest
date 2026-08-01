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
import com.novaempire.app.settings.AppSettings
import com.novaempire.app.settings.LocalDisplaySettings
import com.novaempire.core.domain.theme.ThemeResolver
import com.novaempire.core.domain.theme.ThemeType

/**
 * Thème effectivement appliqué (préférence du joueur résolue contre le calendrier saisonnier).
 * Tout le monde doit lire *ça* plutôt que résoudre le thème de son côté : trois appelants le
 * faisaient par trois chemins différents et pouvaient se retrouver en désaccord — la carte tactique
 * dessinait avec les réglages DEFAULT pendant qu'elle s'affichait aux couleurs d'Halloween.
 */
val LocalThemeType = staticCompositionLocalOf { ThemeType.DEFAULT }

/** Réglages graphiques du thème actif ([LocalThemeType]). */
val LocalGraphicsConfig = staticCompositionLocalOf { ThemeManager.DEFAULT_GRAPHICS }

/** Palette de la carte tactique du thème actif ([LocalThemeType]). */
val LocalMapPalette = staticCompositionLocalOf { MapPalette.DEFAULT }

/**
 * Applique le thème **et** les réglages d'affichage : les deux décrivent la même chose — comment le
 * jeu se présente — et les composants décoratifs ont besoin des deux au même endroit.
 */
@Composable
fun NovaEmpireTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit
) {
    // Le thème ne dépend que de la préférence et de la date : sans `remember`, chaque recomposition
    // de la racine (donc chaque changement de GameState) reconstruisait un ColorScheme neuf et
    // invalidait tous les lecteurs de MaterialTheme.colorScheme.
    val activeTheme = remember(settings.theme) { ThemeResolver.resolve(settings.theme) }
    val colorScheme = remember(activeTheme) { ThemeManager.getColorSchemeForTheme(activeTheme) }
    val graphicsConfig = remember(activeTheme) { ThemeManager.getGraphicsConfig(activeTheme) }
    val mapPalette = remember(activeTheme) { ThemeManager.getMapPalette(activeTheme) }
    val typography = remember(colorScheme, settings.highContrast) {
        novaTypography(colorScheme, settings.highContrast)
    }

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
        LocalGraphicsConfig provides graphicsConfig,
        LocalMapPalette provides mapPalette,
        LocalDisplaySettings provides settings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
