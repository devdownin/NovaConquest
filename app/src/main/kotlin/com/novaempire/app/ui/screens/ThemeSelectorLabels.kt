package com.novaempire.app.ui.screens

import com.novaempire.core.domain.theme.ThemeType

// Partie non graphique du sélecteur de thème : étiquettes, `testTag` et texte d'état.
//
// Séparée du composable pour qu'elle ne dépende pas de Compose — c'est ce qui rend
// `ThemeSelectorLabelsTest` exécutable sur la JVM, donc à chaque poussée, sans émulateur.

/** Étiquette de l'option « suivre le calendrier » du sélecteur de thème. */
const val THEME_OPTION_AUTOMATIC = "AUTOMATIC"

/** `testTag` de la ligne d'état sous le sélecteur de thème. */
const val THEME_STATUS_TAG = "theme_status"

/** `testTag` des interrupteurs de la section VISUALS. */
const val HOLOGRAPHIC_SWITCH_TAG = "switch_holographic"
const val HIGH_CONTRAST_SWITCH_TAG = "switch_high_contrast"
const val REDUCED_MOTION_SWITCH_TAG = "switch_reduced_motion"

/**
 * `testTag` d'une option du sélecteur.
 *
 * Cibler par étiquette serait ambigu : la ligne d'état contient elle aussi le nom du thème actif,
 * donc un test lancé un 31 octobre trouverait deux nœuds pour « HALLOWEEN ».
 */
fun themeOptionTag(theme: ThemeType?): String = "theme_option_${theme?.name ?: "AUTO"}"

/**
 * Les quatre choix du sélecteur, dans l'ordre d'affichage.
 *
 * `null` en tête : c'est le comportement par défaut du jeu, et la seule option qui laisse les
 * thèmes saisonniers s'activer d'eux-mêmes.
 */
val THEME_OPTIONS: List<Pair<ThemeType?, String>> = listOf(
    null to THEME_OPTION_AUTOMATIC,
    ThemeType.DEFAULT to "NOIR FUTURISM",
    ThemeType.HALLOWEEN to "HALLOWEEN",
    ThemeType.WINTER to "WINTER"
)

/**
 * Texte d'état sous le sélecteur : ce que l'automatique donne *aujourd'hui*, ou le fait qu'un choix
 * manuel neutralise le calendrier.
 */
fun themeStatusLabel(preference: ThemeType?, activeTheme: ThemeType): String =
    if (preference == null) "Seasonal — currently ${activeTheme.name}"
    else "Manual override — seasonal themes disabled"

/**
 * `testTag` du plateau de la carte tactique.
 *
 * Vit ici, avec les autres `testTag`, pour la même raison qu'eux : ce fichier ne dépend pas de
 * Compose, donc les tests JVM peuvent s'y référer sans émulateur.
 */
const val TACTICAL_MAP_TAG = "tactical_map"
