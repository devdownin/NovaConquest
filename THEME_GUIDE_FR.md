# Guide de Création de Thème (Graphic Noir Futurism)

Ce guide explique pas à pas comment un graphiste (ou développeur UI) peut créer et intégrer un nouveau thème dans **Nova Empire**.

## Comprendre l'Architecture des Thèmes

Dans Nova Empire, un thème est défini par une palette de 12 couleurs (`ColorScheme`) utilisée par le framework Jetpack Compose. Les ressources graphiques (couleurs des planètes, unités, effets de particules) s'adaptent dynamiquement à ce thème sans changer le code de dessin.

Le gestionnaire central se trouve dans le fichier :
`app/src/main/kotlin/com/novaempire/app/ui/theme/ThemeManager.kt`

## Étape 1 : Créer votre Palette de Couleurs

Ouvrez `ThemeManager.kt`. Vous allez créer une nouvelle variable pour votre thème, par exemple pour un thème "CYBERPUNK".

Voici les 12 couleurs à fournir, avec leur rôle dans l'interface "Graphic Noir" :

```kotlin
val CYBERPUNK = darkColorScheme(
    primary = Color(0xFF00FF00),      // (NeonGreen) : Éléments positifs, capture de planète, confirmations, unités alliées.
    secondary = Color(0xFFFF0055),    // (NeonPink) : Dégâts, santé basse, cibles ennemies.
    tertiary = Color(0xFF00FFFF),     // (NeonCyan) : Couleur principale de l'interface, texte des secteurs, boutons, radar.

    background = Color(0xFF05050A),   // Fond de l'espace profond (très sombre).
    surface = Color(0xFF151020),      // Panneaux industriels (menus, boutons).
    surfaceVariant = Color(0xFF2A2035), // Éléments surélevés, fond des jauges de vie.

    onPrimary = Color(0xFFE0E0E0),    // Texte sur fond Primary.
    onSecondary = Color(0xFFE0E0E0),  // Texte sur fond Secondary.
    onTertiary = Color(0xFFE0E0E0),   // Texte sur fond Tertiary.

    onBackground = Color(0xFFE0E0E0), // Texte principal sur le fond (Titre principal).
    onSurface = Color(0xFFD4C8B0),    // (TextPrimary) : Texte de contenu dans les panneaux.
    onSurfaceVariant = Color(0xFF7A6E60) // (TextSecondary) : Texte secondaire/grisé.
)
```

## Étape 2 : Enregistrer le Thème dans le Modèle de Données

Ouvrez le fichier :
`core/domain/src/main/kotlin/com/novaempire/core/domain/models/ThemeConfig.kt`

Ajoutez le nom de votre thème dans l'énumération `ThemeType` :

```kotlin
@Serializable
enum class ThemeType {
    DEFAULT,
    HALLOWEEN,
    WINTER,
    CYBERPUNK // <--- Ajoutez votre thème ici
}
```

## Étape 3 : Relier le Thème au Moteur

Retournez dans `ThemeManager.kt`.

1. Trouvez la fonction `getColorSchemeForTheme` et ajoutez votre thème au `when` :

```kotlin
fun getColorSchemeForTheme(themeType: ThemeType): androidx.compose.material3.ColorScheme {
    return when (themeType) {
        ThemeType.HALLOWEEN -> HALLOWEEN
        ThemeType.WINTER -> WINTER
        ThemeType.CYBERPUNK -> CYBERPUNK // <--- Ajoutez cette ligne
        else -> DEFAULT
    }
}
```

2. (Optionnel) Si votre thème correspond à un événement saisonnier, ajoutez-le dans `getActiveTheme`. L'exemple suivant l'active du 1er au 15 Février :

```kotlin
// Cyberpunk: Feb 1 to Feb 15
(month == Calendar.FEBRUARY && day <= 15) -> ThemeType.CYBERPUNK
```

## Tester le Rendu

Le moteur de jeu lit la date système pour forcer un thème si la partie n'en a pas sauvegardé un. Pour tester immédiatement votre thème sans attendre la date :

Dans `ThemeManager.kt` :
Modifiez la toute première ligne de `getActiveTheme` pour forcer votre thème temporairement :
```kotlin
fun getActiveTheme(savedTheme: ThemeType? = null): ThemeType {
    return ThemeType.CYBERPUNK // Ligne de test ! N'oubliez pas de l'effacer ensuite.
    // ...
```

## Principes Graphiques à Respecter

Si vous ajustez les couleurs, gardez à l'esprit la direction artistique :
- **Graphic Noir Futurism** : Évitez les couleurs primaires pures (sauf pour les néons `primary/secondary/tertiary`). Privilégiez des couleurs désaturées, sales (bruns, gris chauds, violets foncés) pour `background` et `surface`.
- Les contours (`inkBlack`) de la carte (Dessin des vaisseaux et planètes) resteront noirs, quel que soit le thème, pour préserver l'effet "Bande Dessinée".
