package com.novaempire.app.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.novaempire.core.domain.models.ThemeType
import org.json.JSONObject
import java.util.Calendar

object ThemeManager {
    private val loadedThemes = mutableMapOf<ThemeType, ThemeDefinition>()

    fun init(context: Context) {
        try {
            val defaultJsonStr = context.assets.open("themes/default.json").bufferedReader().use { it.readText() }
            val halloweenJsonStr = context.assets.open("themes/halloween.json").bufferedReader().use { it.readText() }
            val winterJsonStr = context.assets.open("themes/winter.json").bufferedReader().use { it.readText() }

            loadedThemes[ThemeType.DEFAULT] = parseThemeDefinition(defaultJsonStr)
            loadedThemes[ThemeType.HALLOWEEN] = parseThemeDefinition(halloweenJsonStr)
            loadedThemes[ThemeType.WINTER] = parseThemeDefinition(winterJsonStr)
            Log.d("ThemeManager", "Themes loaded successfully")
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error loading themes from JSON: ${e.message}")
        }
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

    private fun parseColor(colorString: String): Color {
        return Color(android.graphics.Color.parseColor(colorString))
    }

    private val fallbackColorScheme = darkColorScheme(
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

    fun getColorSchemeForTheme(themeType: ThemeType): androidx.compose.material3.ColorScheme {
        val def = loadedThemes[themeType] ?: loadedThemes[ThemeType.DEFAULT]

        return if (def != null) {
            darkColorScheme(
                primary = parseColor(def.colors.primary),
                secondary = parseColor(def.colors.secondary),
                tertiary = parseColor(def.colors.tertiary),
                background = parseColor(def.colors.background),
                surface = parseColor(def.colors.surface),
                surfaceVariant = parseColor(def.colors.surfaceVariant),
                onPrimary = parseColor(def.colors.onPrimary),
                onSecondary = parseColor(def.colors.onSecondary),
                onTertiary = parseColor(def.colors.onTertiary),
                onBackground = parseColor(def.colors.onBackground),
                onSurface = parseColor(def.colors.onSurface),
                onSurfaceVariant = parseColor(def.colors.onSurfaceVariant)
            )
        } else {
            fallbackColorScheme
        }
    }

    fun getGraphicsConfig(themeType: ThemeType): GraphicsConfig {
        return loadedThemes[themeType]?.graphics ?: GraphicsConfig(3f, 0.7f, 12f, 1f)
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
