package com.novaempire.core.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valide les thèmes **réellement livrés** : le build de ce module expose
 * `app/src/main/assets` au classpath de test (cf. `core/domain/build.gradle.kts`).
 *
 * C'est le filet qui manquait : un fichier absent, une clé oubliée ou une couleur mal tapée ne se
 * voyaient qu'au lancement de l'application — au mieux par un thème silencieusement perdu, au pire
 * par un crash.
 */
class ShippedThemesTest {

    private fun readAsset(path: String): String? =
        javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    @Test
    fun `chaque ThemeType a son fichier de thème`() {
        for (type in ThemeType.entries) {
            assertNotNull(
                "Asset manquant pour $type : ${type.assetPath}",
                readAsset(type.assetPath)
            )
        }
    }

    @Test
    fun `chaque thème livré est complet et exploitable`() {
        for (type in ThemeType.entries) {
            val raw = readAsset(type.assetPath) ?: continue
            val definition = ThemeParser.parse(raw)   // lève si une clé manque
            val problems = definition.problems()
            assertTrue(
                "${type.assetPath} : ${problems.joinToString("; ")}",
                problems.isEmpty()
            )
        }
    }

    @Test
    fun `le champ name correspond au ThemeType`() {
        for (type in ThemeType.entries) {
            val raw = readAsset(type.assetPath) ?: continue
            assertEquals(type.name, ThemeParser.parse(raw).name)
        }
    }

    /**
     * Anti-dérive : la copie compilée servant de secours doit rester identique au JSON par défaut.
     * Ces deux-là divergeaient déjà (`planetShadowAlpha` 0.7 côté code, 0.6 côté JSON).
     */
    @Test
    fun `le thème de secours est identique au thème par défaut livré`() {
        val raw = readAsset(ThemeType.DEFAULT.assetPath)
        assertNotNull("Asset manquant : ${ThemeType.DEFAULT.assetPath}", raw)
        assertEquals(ThemeDefaults.FALLBACK, ThemeParser.parse(raw!!))
    }
}
