package com.novaempire.app.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.novaempire.core.domain.theme.ThemeType

/**
 * Les préférences d'application, hors état de partie.
 *
 * Elles vivaient toutes dans des `remember` locaux de `SettingsScreen` : personne ne les lisait,
 * rien ne les persistait, et « APPLY SETTINGS » ne faisait que fermer l'écran.
 *
 * @param theme choix du joueur, `null` = automatique (thème saisonnier).
 * @param masterVolume volume global, 0..1.
 * @param sfxVolume volume des effets de combat, multiplié par [masterVolume].
 * @param holographicEffects effets décoratifs coûteux : flou des panneaux, trames de fond,
 *   balayage de la carte. Les couper allège le rendu et le bruit visuel.
 * @param highContrast renforce la lisibilité : contours de carte pleins et épais, texte secondaire
 *   à la couleur du texte principal.
 */
data class AppSettings(
    val theme: ThemeType? = null,
    val masterVolume: Float = 0.8f,
    val sfxVolume: Float = 0.7f,
    val holographicEffects: Boolean = true,
    val highContrast: Boolean = false
)

/**
 * Réglages d'affichage courants, publiés par `NovaEmpireTheme`.
 *
 * Permet aux composants décoratifs (`HalftoneBackground`, `NoiseOverlay`, `IndustrialPanel`) de se
 * désactiver eux-mêmes plutôt que d'imposer une condition à chacun de leurs appelants.
 */
val LocalDisplaySettings = staticCompositionLocalOf { AppSettings() }
