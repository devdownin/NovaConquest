package com.novaempire.core.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ThemeResolverTest {

    private fun seasonal(year: Int, month: Int, day: Int): ThemeType =
        ThemeResolver.seasonalTheme(LocalDate.of(year, month, day))

    @Test
    fun `hors saison le theme est DEFAULT`() {
        assertEquals(ThemeType.DEFAULT, seasonal(2026, 7, 14))
        assertEquals(ThemeType.DEFAULT, seasonal(2026, 3, 1))
    }

    @Test
    fun `la fenetre halloween couvre le 25 octobre au 5 novembre inclus`() {
        assertEquals(ThemeType.DEFAULT, seasonal(2026, 10, 24))
        assertEquals(ThemeType.HALLOWEEN, seasonal(2026, 10, 25))
        assertEquals(ThemeType.HALLOWEEN, seasonal(2026, 10, 31))
        assertEquals(ThemeType.HALLOWEEN, seasonal(2026, 11, 1))
        assertEquals(ThemeType.HALLOWEEN, seasonal(2026, 11, 5))
        assertEquals(ThemeType.DEFAULT, seasonal(2026, 11, 6))
    }

    @Test
    fun `la fenetre hivernale enjambe le nouvel an`() {
        assertEquals(ThemeType.DEFAULT, seasonal(2026, 12, 19))
        assertEquals(ThemeType.WINTER, seasonal(2026, 12, 20))
        assertEquals(ThemeType.WINTER, seasonal(2026, 12, 31))
        assertEquals(ThemeType.WINTER, seasonal(2027, 1, 1))
        assertEquals(ThemeType.WINTER, seasonal(2027, 1, 5))
        assertEquals(ThemeType.DEFAULT, seasonal(2027, 1, 6))
    }

    @Test
    fun `sans preference on suit la saison`() {
        assertEquals(
            ThemeType.HALLOWEEN,
            ThemeResolver.resolve(null, LocalDate.of(2026, 10, 31))
        )
        assertEquals(
            ThemeType.DEFAULT,
            ThemeResolver.resolve(null, LocalDate.of(2026, 7, 14))
        )
    }

    @Test
    fun `une preference explicite gagne sur la saison`() {
        assertEquals(
            ThemeType.WINTER,
            ThemeResolver.resolve(ThemeType.WINTER, LocalDate.of(2026, 10, 31))
        )
    }

    /** Le défaut historique : DEFAULT était traité comme « pas de choix », donc toujours écrasé. */
    @Test
    fun `choisir DEFAULT pendant une fenetre saisonniere est respecte`() {
        assertEquals(
            ThemeType.DEFAULT,
            ThemeResolver.resolve(ThemeType.DEFAULT, LocalDate.of(2026, 12, 25))
        )
        assertEquals(
            ThemeType.DEFAULT,
            ThemeResolver.resolve(ThemeType.DEFAULT, LocalDate.of(2026, 10, 31))
        )
    }
}
