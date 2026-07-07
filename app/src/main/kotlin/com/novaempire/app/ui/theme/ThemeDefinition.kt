package com.novaempire.app.ui.theme




data class ThemeColors(
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onPrimary: String,
    val onSecondary: String,
    val onTertiary: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String
)


data class GraphicsConfig(
    val outlineStrokeWidth: Float,
    val planetShadowAlpha: Float,
    val blurRadius: Float,
    val particleCountMultiplier: Float
)


data class ThemeDefinition(
    val name: String,
    val colors: ThemeColors,
    val graphics: GraphicsConfig
)
