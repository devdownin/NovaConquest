package com.novaempire.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novaempire.app.settings.AppSettings
import com.novaempire.app.ui.theme.NovaEmpireTheme
import com.novaempire.core.domain.theme.ThemeType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test d'interface du sélecteur de thème et des interrupteurs d'affichage.
 *
 * Demande un appareil ou un émulateur : la CI ne lance le job `android-test` que sur `main`. La
 * partie testable sans appareil (liste d'options, texte d'état) est couverte à part par
 * `ThemeSelectorLabelsTest`, exécuté à chaque poussée.
 */
@RunWith(AndroidJUnit4::class)
class ThemeSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    /** Monte l'écran comme le fait `MainActivity` : état remonté, réglages appliqués en direct. */
    private fun setContentWithSettings(
        initial: AppSettings = AppSettings(),
        onChange: (AppSettings) -> Unit = {}
    ) {
        compose.setContent {
            var settings by remember { mutableStateOf(initial) }
            NovaEmpireTheme(settings = settings) {
                SettingsScreen(
                    onBackClick = {},
                    settings = settings,
                    onSettingsChange = {
                        settings = it
                        onChange(it)
                    }
                )
            }
        }
    }

    @Test
    fun toutes_les_options_de_theme_sont_affichees() {
        setContentWithSettings()

        THEME_OPTIONS.forEach { (value, _) ->
            compose.onNodeWithTag(themeOptionTag(value))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun choisir_un_theme_remonte_la_preference() {
        var lastSettings: AppSettings? = null
        setContentWithSettings(onChange = { lastSettings = it })

        compose.onNodeWithTag(themeOptionTag(ThemeType.HALLOWEEN)).performScrollTo().performClick()

        assertEquals(ThemeType.HALLOWEEN, lastSettings?.theme)
    }

    /** Le défaut d'origine : `DEFAULT` valait « pas de choix » et se faisait écraser par la saison. */
    @Test
    fun choisir_le_theme_par_defaut_est_un_choix_explicite() {
        var lastSettings: AppSettings? = null
        setContentWithSettings(onChange = { lastSettings = it })

        compose.onNodeWithTag(themeOptionTag(ThemeType.DEFAULT)).performScrollTo().performClick()

        assertEquals(ThemeType.DEFAULT, lastSettings?.theme)
    }

    @Test
    fun revenir_a_l_automatique_efface_la_preference() {
        var lastSettings: AppSettings? = null
        setContentWithSettings(
            initial = AppSettings(theme = ThemeType.WINTER),
            onChange = { lastSettings = it }
        )

        compose.onNodeWithTag(themeOptionTag(null)).performScrollTo().performClick()

        assertEquals(null, lastSettings?.theme)
    }

    @Test
    fun la_ligne_d_etat_suit_le_choix() {
        setContentWithSettings()

        compose.onNodeWithTag(THEME_STATUS_TAG).performScrollTo()
            .assertTextContains("Seasonal", substring = true)

        compose.onNodeWithTag(themeOptionTag(ThemeType.WINTER)).performScrollTo().performClick()

        compose.onNodeWithTag(THEME_STATUS_TAG).performScrollTo()
            .assertTextContains("Manual override", substring = true)
    }

    @Test
    fun les_interrupteurs_visuels_remontent_leur_etat() {
        var lastSettings: AppSettings? = null
        setContentWithSettings(onChange = { lastSettings = it })

        compose.onNodeWithTag(HIGH_CONTRAST_SWITCH_TAG).performScrollTo().performClick()
        assertEquals(true, lastSettings?.highContrast)

        compose.onNodeWithTag(HOLOGRAPHIC_SWITCH_TAG).performScrollTo().performClick()
        assertEquals(false, lastSettings?.holographicEffects)
    }
}
