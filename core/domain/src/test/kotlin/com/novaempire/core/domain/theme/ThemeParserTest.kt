package com.novaempire.core.domain.theme

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeParserTest {

    private fun themeJson(
        primary: String = "#FF4A7B9D",
        planetShadowAlpha: String = "0.6"
    ) = """
        {
          "name": "TEST",
          "colors": {
            "primary": "$primary",
            "secondary": "#FF8B2A2A",
            "tertiary": "#FFB85C2A",
            "background": "#FF130F0A",
            "surface": "#FF1C1810",
            "surfaceVariant": "#FF2D2620",
            "onPrimary": "#FFD4C8B0",
            "onSecondary": "#FFD4C8B0",
            "onTertiary": "#FFD4C8B0",
            "onBackground": "#FFD4C8B0",
            "onSurface": "#FFD4C8B0",
            "onSurfaceVariant": "#FF7A6E60"
          },
          "graphics": {
            "outlineStrokeWidth": 3.0,
            "planetShadowAlpha": $planetShadowAlpha,
            "blurRadius": 12.0,
            "particleCountMultiplier": 1.0
          }
        }
    """.trimIndent()

    @Test
    fun `parse un thème complet`() {
        val theme = ThemeParser.parse(themeJson())
        assertEquals("TEST", theme.name)
        assertEquals("#FF4A7B9D", theme.colors.primary)
        assertEquals(0.6f, theme.graphics.planetShadowAlpha, 0.0001f)
        assertTrue(theme.problems().isEmpty())
    }

    @Test
    fun `les clés inconnues sont ignorées`() {
        val withExtra = themeJson().replaceFirst("\"name\": \"TEST\"", "\"name\": \"TEST\", \"terrain\": {\"void\": \"#FF181210\"}")
        assertEquals("TEST", ThemeParser.parse(withExtra).name)
    }

    @Test(expected = SerializationException::class)
    fun `une clé de couleur manquante fait échouer le parsing`() {
        ThemeParser.parse(themeJson().replace("\"tertiary\": \"#FFB85C2A\",", ""))
    }

    @Test
    fun `parseOrNull absorbe un JSON malformé`() {
        assertNull(ThemeParser.parseOrNull("{ pas du json"))
        assertNull(ThemeParser.parseOrNull(themeJson().replace("\"tertiary\": \"#FFB85C2A\",", "")))
    }

    @Test
    fun `une couleur invalide est signalée sans faire échouer le parsing`() {
        val theme = ThemeParser.parse(themeJson(primary = "#FG4A7B9D"))
        val problems = theme.problems()
        assertEquals(1, problems.size)
        assertTrue(problems.first(), problems.first().contains("primary"))
    }

    @Test
    fun `un réglage graphique hors bornes est signalé`() {
        val problems = ThemeParser.parse(themeJson(planetShadowAlpha = "1.4")).problems()
        assertEquals(1, problems.size)
        assertTrue(problems.first(), problems.first().contains("planetShadowAlpha"))
    }
}
