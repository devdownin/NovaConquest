package com.novaempire.app.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.novaempire.core.domain.models.ThemeType
import org.json.JSONObject
import java.util.Calendar

object ThemeManager {
    private const val TAG = "ThemeManager"

    /** Réglages graphiques de secours — alignés sur `assets/themes/default.json`. */
    val DEFAULT_GRAPHICS = GraphicsConfig(
        outlineStrokeWidth = 3f,
        planetShadowAlpha = 0.6f,
        blurRadius = 12f,
        particleCountMultiplier = 1f
    )

    /**
     * Un thème prêt à l'emploi : les couleurs sont converties **au chargement**, pas à chaque
     * recomposition. Une chaîne hexadécimale invalide dans un JSON écrit à la main devenait sinon
     * une `IllegalArgumentException` levée en pleine composition, donc un crash au lancement.
     */
    private data class ResolvedTheme(val colorScheme: ColorScheme, val graphics: GraphicsConfig)

    private val loadedThemes = mutableMapOf<ThemeType, ResolvedTheme>()

    private val fallbackColorScheme = novaColorScheme(
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

    private val fallbackTheme = ResolvedTheme(fallbackColorScheme, DEFAULT_GRAPHICS)

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
            val path = "themes/${themeType.name.lowercase()}.json"
            try {
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                loadedThemes[themeType] = resolveTheme(parseThemeDefinition(json))
            } catch (e: Exception) {
                Log.e(TAG, "Impossible de charger le thème $themeType depuis $path: ${e.message}")
            }
        }
        Log.d(TAG, "Thèmes chargés: ${loadedThemes.keys}")
    }

    private fun parseThemeDefinition(jsonStr: String): ThemeDefinition {
        val jsonObj = JSONObject(jsonStr)
        val colorsObj = jsonObj.getJSONObject("colors")
        val graphicsObj = jsonObj.getJSONObject("graphics")

        return ThemeDefinition(
            name = jsonObj.getString("name"),
            colors = ThemeColors(
                primary = colorsObj.getString("primary"),
                secondary = colorsObj.getString("secondary"),
                tertiary = colorsObj.getString("tertiary"),
                background = colorsObj.getString("background"),
                surface = colorsObj.getString("surface"),
                surfaceVariant = colorsObj.getString("surfaceVariant"),
                onPrimary = colorsObj.getString("onPrimary"),
                onSecondary = colorsObj.getString("onSecondary"),
                onTertiary = colorsObj.getString("onTertiary"),
                onBackground = colorsObj.getString("onBackground"),
                onSurface = colorsObj.getString("onSurface"),
                onSurfaceVariant = colorsObj.getString("onSurfaceVariant")
            ),
            graphics = GraphicsConfig(
                outlineStrokeWidth = graphicsObj.getDouble("outlineStrokeWidth").toFloat(),
                planetShadowAlpha = graphicsObj.getDouble("planetShadowAlpha").toFloat(),
                blurRadius = graphicsObj.getDouble("blurRadius").toFloat(),
                particleCountMultiplier = graphicsObj.getDouble("particleCountMultiplier").toFloat()
            )
        )
    }

    private fun resolveTheme(def: ThemeDefinition): ResolvedTheme = ResolvedTheme(
        colorScheme = novaColorScheme(
            primary = parseColor(def.colors.primary, NeonCyan),
            secondary = parseColor(def.colors.secondary, NeonRed),
            tertiary = parseColor(def.colors.tertiary, NeonOrange),
            background = parseColor(def.colors.background, VoidBlack),
            surface = parseColor(def.colors.surface, SurfaceDark),
            surfaceVariant = parseColor(def.colors.surfaceVariant, SurfaceLight),
            onPrimary = parseColor(def.colors.onPrimary, TextPrimary),
            onSecondary = parseColor(def.colors.onSecondary, TextPrimary),
            onTertiary = parseColor(def.colors.onTertiary, TextPrimary),
            onBackground = parseColor(def.colors.onBackground, TextPrimary),
            onSurface = parseColor(def.colors.onSurface, TextPrimary),
            onSurfaceVariant = parseColor(def.colors.onSurfaceVariant, TextSecondary)
        ),
        graphics = def.graphics
    )

    private fun parseColor(colorString: String, fallback: Color): Color =
        try {
            Color(android.graphics.Color.parseColor(colorString))
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Couleur invalide dans un thème: '$colorString', valeur de secours utilisée")
            fallback
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

    private fun resolve(themeType: ThemeType): ResolvedTheme =
        loadedThemes[themeType] ?: loadedThemes[ThemeType.DEFAULT] ?: fallbackTheme

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
