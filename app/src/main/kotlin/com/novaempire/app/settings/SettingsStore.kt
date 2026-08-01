package com.novaempire.app.settings

import android.content.Context
import android.util.Log
import com.novaempire.core.domain.theme.ThemeType

/**
 * Persiste [AppSettings] dans les `SharedPreferences`.
 *
 * Séparé de la sauvegarde de partie à dessein : ce sont des préférences d'application. Le thème
 * vivait dans `GameState`, donc dans le format de sauvegarde — il n'existait pas tant qu'aucune
 * partie n'était chargée, et un réglage d'affichage pesait sur la compatibilité des sauvegardes.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = readTheme(),
            masterVolume = prefs.getFloat(KEY_MASTER_VOLUME, defaults.masterVolume).coerceIn(0f, 1f),
            sfxVolume = prefs.getFloat(KEY_SFX_VOLUME, defaults.sfxVolume).coerceIn(0f, 1f),
            holographicEffects = prefs.getBoolean(KEY_HOLO, defaults.holographicEffects),
            highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, defaults.highContrast)
        )
    }

    fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.theme?.name ?: VALUE_AUTO)
            .putFloat(KEY_MASTER_VOLUME, settings.masterVolume)
            .putFloat(KEY_SFX_VOLUME, settings.sfxVolume)
            .putBoolean(KEY_HOLO, settings.holographicEffects)
            .putBoolean(KEY_HIGH_CONTRAST, settings.highContrast)
            .apply()
    }

    private fun readTheme(): ThemeType? {
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

    private companion object {
        const val TAG = "SettingsStore"
        const val FILE = "nova_empire_settings"
        const val KEY_THEME = "theme"
        const val KEY_MASTER_VOLUME = "master_volume"
        const val KEY_SFX_VOLUME = "sfx_volume"
        const val KEY_HOLO = "holographic_effects"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val VALUE_AUTO = "AUTO"
    }
}
