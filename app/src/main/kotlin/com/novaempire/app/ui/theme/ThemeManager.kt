package com.novaempire.app.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.novaempire.core.domain.theme.GraphicsConfig
import com.novaempire.core.domain.theme.HexColor
import com.novaempire.core.domain.theme.ThemeDefaults
import com.novaempire.core.domain.theme.ThemeDefinition
import com.novaempire.core.domain.theme.ThemeParser
import com.novaempire.core.domain.theme.ThemeType

/**
 * Traduit les définitions de thème (`:core:domain`, pur Kotlin et testé) en objets Compose.
 *
 * Tout ce qui peut être testé sur la JVM — modèle, parsing, validation des couleurs, résolution
 * saisonnière — vit dans `:core:domain`. Il ne reste ici que ce qui dépend d'Android : la lecture
 * des assets et la construction du [ColorScheme].
 */
object ThemeManager {
    private const val TAG = "ThemeManager"

    /** Réglages graphiques de secours, identiques au thème par défaut livré. */
    val DEFAULT_GRAPHICS: GraphicsConfig = ThemeDefaults.FALLBACK.graphics

    private data class ResolvedTheme(
        val colorScheme: ColorScheme,
        val graphics: GraphicsConfig,
        val mapPalette: MapPalette
    )

    private val loadedThemes = mutableMapOf<ThemeType, ResolvedTheme>()

    /** Thème de secours dérivé de la copie compilée du thème par défaut — plus de palette dupliquée. */
    private val fallbackTheme = resolveTheme(ThemeDefaults.FALLBACK)

    /**
     * Charge un fichier par valeur de [ThemeType], en `themes/<nom en minuscules>.json`.
     *
     * Chaque thème est chargé indépendamment : un fichier manquant ou malformé ne fait perdre que
     * ce thème-là. L'ancienne version ouvrait les trois fichiers dans un seul `try`, donc une
     * virgule en trop dans `winter.json` faisait basculer *toute* l'application sur la palette de
     * secours, y compris le thème par défaut.
     */
    fun init(context: Context) {
        loadedThemes.clear()
        for (themeType in ThemeType.entries) {
            val raw = try {
                context.assets.open(themeType.assetPath).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "Asset de thème illisible: ${themeType.assetPath} (${e.message})")
                continue
            }
            val definition = ThemeParser.parseOrNull(raw)
            if (definition == null) {
                Log.e(TAG, "JSON de thème invalide: ${themeType.assetPath}")
                continue
            }
            // Les défauts non bloquants (couleur illisible, réglage hors bornes) sont journalisés
            // puis absorbés par les valeurs de secours role par role. `ShippedThemesTest` en fait
            // un échec de build, donc ce chemin ne devrait jamais servir en production.
            definition.problems().forEach { Log.w(TAG, "${themeType.assetPath}: $it") }
            loadedThemes[themeType] = resolveTheme(definition)
        }
        Log.d(TAG, "Thèmes chargés: ${loadedThemes.keys}")
    }

    /**
     * Convertit les couleurs **au chargement**, pas au rendu : une chaîne hexadécimale invalide
     * faisait auparavant lever `android.graphics.Color.parseColor` en pleine composition, donc
     * planter l'application. Ici, chaque rôle a une valeur de secours.
     */
    private fun resolveTheme(def: ThemeDefinition): ResolvedTheme {
        val fallback = ThemeDefaults.FALLBACK.colors
        fun color(value: String, fallbackValue: String): Color =
            Color(HexColor.parse(value) ?: HexColor.parse(fallbackValue)!!)

        return ResolvedTheme(
            colorScheme = novaColorScheme(
                primary = color(def.colors.primary, fallback.primary),
                secondary = color(def.colors.secondary, fallback.secondary),
                tertiary = color(def.colors.tertiary, fallback.tertiary),
                background = color(def.colors.background, fallback.background),
                surface = color(def.colors.surface, fallback.surface),
                surfaceVariant = color(def.colors.surfaceVariant, fallback.surfaceVariant),
                onPrimary = color(def.colors.onPrimary, fallback.onPrimary),
                onSecondary = color(def.colors.onSecondary, fallback.onSecondary),
                onTertiary = color(def.colors.onTertiary, fallback.onTertiary),
                onBackground = color(def.colors.onBackground, fallback.onBackground),
                onSurface = color(def.colors.onSurface, fallback.onSurface),
                onSurfaceVariant = color(def.colors.onSurfaceVariant, fallback.onSurfaceVariant)
            ),
            graphics = def.graphics,
            mapPalette = MapPalette.from(def.terrain)
        )
    }

    /**
     * Construit un [ColorScheme] à partir des douze rôles exposés par les JSON, puis **dérive** les
     * rôles Material que les JSON ne décrivent pas (conteneurs, `outline`, surfaces inverses,
     * `scrim`). Sans ça, ces rôles gardaient la palette Material par défaut — mauve/violet — et
     * ressortaient dans les composants standard : bordure d'un `Switch` non coché, fond d'un
     * `Snackbar`, qui n'avaient donc rien à voir avec le thème appliqué.
     */
    private fun novaColorScheme(
        primary: Color,
        secondary: Color,
        tertiary: Color,
        background: Color,
        surface: Color,
        surfaceVariant: Color,
        onPrimary: Color,
        onSecondary: Color,
        onTertiary: Color,
        onBackground: Color,
        onSurface: Color,
        onSurfaceVariant: Color
    ): ColorScheme = darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onTertiary = onTertiary,
        onBackground = onBackground,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        primaryContainer = primary.copy(alpha = 0.25f).compositeOver(surface),
        onPrimaryContainer = onSurface,
        secondaryContainer = secondary.copy(alpha = 0.25f).compositeOver(surface),
        onSecondaryContainer = onSurface,
        tertiaryContainer = tertiary.copy(alpha = 0.25f).compositeOver(surface),
        onTertiaryContainer = onSurface,
        surfaceTint = primary,
        outline = onSurfaceVariant,
        outlineVariant = surfaceVariant,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        inversePrimary = primary,
        scrim = background
    )

    fun getColorSchemeForTheme(themeType: ThemeType): ColorScheme = resolve(themeType).colorScheme

    fun getGraphicsConfig(themeType: ThemeType): GraphicsConfig = resolve(themeType).graphics

    fun getMapPalette(themeType: ThemeType): MapPalette = resolve(themeType).mapPalette

    private fun resolve(themeType: ThemeType): ResolvedTheme =
        loadedThemes[themeType] ?: loadedThemes[ThemeType.DEFAULT] ?: fallbackTheme
}
