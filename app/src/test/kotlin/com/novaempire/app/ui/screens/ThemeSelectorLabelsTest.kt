package com.novaempire.app.ui.screens

import com.novaempire.core.domain.theme.ThemeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de la partie non graphique du sélecteur de thème : la liste d'options et le texte
 * d'état. Ils tournent sans émulateur, donc la CI les exécute à chaque poussée — contrairement au
 * test d'interface (`ThemeSelectorTest`) qui demande un appareil.
 */
class ThemeSelectorLabelsTest {

    @Test
    fun `le selecteur propose l'automatique puis chaque theme livre`() {
        assertEquals(ThemeType.entries.size + 1, THEME_OPTIONS.size)
        assertEquals(null, THEME_OPTIONS.first().first)
        assertEquals(THEME_OPTION_AUTOMATIC, THEME_OPTIONS.first().second)

        val offered = THEME_OPTIONS.mapNotNull { it.first }.toSet()
        assertEquals(ThemeType.entries.toSet(), offered)
    }

    @Test
    fun `aucune option n'est proposee deux fois`() {
        assertEquals(THEME_OPTIONS.size, THEME_OPTIONS.map { it.first }.distinct().size)
        assertEquals(THEME_OPTIONS.size, THEME_OPTIONS.map { it.second }.distinct().size)
    }

    @Test
    fun `sans preference l'etat annonce le theme saisonnier en cours`() {
        val label = themeStatusLabel(preference = null, activeTheme = ThemeType.HALLOWEEN)
        assertTrue(label, label.contains("HALLOWEEN"))
    }

    @Test
    fun `avec une preference l'etat signale que la saison est neutralisee`() {
        val label = themeStatusLabel(preference = ThemeType.WINTER, activeTheme = ThemeType.WINTER)
        assertTrue(label, label.contains("Manual override"))
    }
}
