package com.novaempire.core.domain.theme

/**
 * Les thèmes livrés avec le jeu.
 *
 * Chaque valeur doit avoir un fichier `themes/<nom en minuscules>.json` dans les assets — c'est la
 * seule déclaration à écrire pour ajouter un thème (cf. `THEME_GUIDE_FR.md`), et
 * `ShippedThemesTest` vérifie la correspondance.
 *
 * Volontairement **non `@Serializable`** : le thème est une préférence d'application, pas un état de
 * partie. Il vivait auparavant dans `GameState.themeConfig`, donc dans le format de sauvegarde, ce
 * qui liait un réglage d'affichage à la compatibilité des sauvegardes. Il est désormais persisté à
 * part (`ThemePreferenceStore` côté `:app`).
 */
enum class ThemeType {
    DEFAULT,
    HALLOWEEN,
    WINTER;

    /** Chemin de l'asset décrivant ce thème. */
    val assetPath: String get() = "themes/${name.lowercase()}.json"
}
