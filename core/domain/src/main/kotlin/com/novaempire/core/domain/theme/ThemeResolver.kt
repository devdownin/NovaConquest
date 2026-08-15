package com.novaempire.core.domain.theme

import java.time.LocalDate
import java.time.Month

/**
 * Décide quel thème afficher.
 *
 * La préférence du joueur est un `ThemeType?` où **`null` signifie « automatique »** (suivre le
 * calendrier). C'est ce qui distingue « je n'ai rien choisi » de « je veux explicitement le thème
 * par défaut » — l'ancienne version traitait `DEFAULT` comme « pas de choix », si bien qu'un joueur
 * ne pouvait pas garder la palette d'origine pendant les fêtes.
 *
 * Logique pure et sans Android : c'est la partie du système de thèmes que la CI peut exercer.
 */
object ThemeResolver {

    /** Fenêtres saisonnières, bornes incluses. */
    private val HALLOWEEN_START = Month.OCTOBER to 25
    private val HALLOWEEN_END = Month.NOVEMBER to 5
    private val WINTER_START = Month.DECEMBER to 20
    private val WINTER_END = Month.JANUARY to 5

    /**
     * @param preference choix explicite du joueur, ou `null` pour suivre la saison.
     * @param date jour de référence — paramétrable pour les tests.
     */
    fun resolve(preference: ThemeType?, date: LocalDate = LocalDate.now()): ThemeType =
        preference ?: seasonalTheme(date)

    /** Le thème imposé par le calendrier à cette date, [ThemeType.DEFAULT] hors saison. */
    fun seasonalTheme(date: LocalDate): ThemeType = when {
        inWindow(date, HALLOWEEN_START, HALLOWEEN_END) -> ThemeType.HALLOWEEN
        inWindow(date, WINTER_START, WINTER_END) -> ThemeType.WINTER
        else -> ThemeType.DEFAULT
    }

    /**
     * Fenêtre (mois, jour) → (mois, jour), bornes incluses, qui peut enjamber le nouvel an :
     * la fenêtre hivernale va du 20 décembre au 5 janvier.
     */
    private fun inWindow(
        date: LocalDate,
        start: Pair<Month, Int>,
        end: Pair<Month, Int>
    ): Boolean {
        if (start.first == end.first) {
            return date.month == start.first && date.dayOfMonth in start.second..end.second
        }
        val afterStart = date.month == start.first && date.dayOfMonth >= start.second
        val beforeEnd = date.month == end.first && date.dayOfMonth <= end.second
        val strictlyInside = date.month > start.first && date.month < end.first
        return if (start.first < end.first) {
            afterStart || beforeEnd || strictlyInside
        } else {
            // Fenêtre à cheval sur l'année : décembre… puis janvier.
            afterStart || beforeEnd || date.month > start.first || date.month < end.first
        }
    }
}
