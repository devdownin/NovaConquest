package com.novaempire.app.ui.theme

import android.content.Context
import android.util.Log
import com.novaempire.core.domain.theme.ThemeType

/**
 * Persiste le thème choisi par le joueur.
 *
 * Séparé de la sauvegarde de partie à dessein : le thème est une préférence d'application. Rangé
 * dans `GameState`, il n'existait pas tant qu'aucune partie n'était chargée — donc pas au menu
 * principal — et liait un réglage d'affichage à la compatibilité du format de sauvegarde.
 *
 * `null` signifie « automatique » : suivre le calendrier saisonnier (cf.
 * `com.novaempire.core.domain.theme.ThemeResolver`).
 */
class ThemePreferenceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nova_empire_settings", Context.MODE_PRIVATE)

    fun read(): ThemeType? {
        val stored = prefs.getString(KEY_THEME, null) ?: return null
        if (stored == VALUE_AUTO) return null
        return try {
            ThemeType.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            // Thème retiré d'une version à l'autre : on retombe sur l'automatique plutôt que de
            // planter au démarrage sur une préférence devenue obsolète.
            Log.w(TAG, "Thème inconnu en préférence: '$stored', retour à l'automatique")
            null
        }
    }

    fun write(theme: ThemeType?) {
        prefs.edit().putString(KEY_THEME, theme?.name ?: VALUE_AUTO).apply()
    }

    private companion object {
        const val TAG = "ThemePreferenceStore"
        const val KEY_THEME = "theme"
        const val VALUE_AUTO = "AUTO"
    }
}
