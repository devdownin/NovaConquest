# Guide de Création de Thème (Graphic Noir Futurism)

Ce guide explique pas à pas comment un graphiste (ou développeur UI) peut créer et intégrer un nouveau thème dans **Nova Empire**.

## Comprendre l'Architecture des Thèmes

Dans Nova Empire, un thème est défini par des fichiers JSON situés dans `app/src/main/assets/themes/`.
Les ressources graphiques (couleurs des planètes, unités, effets de particules, etc.) s'adaptent dynamiquement à ce fichier au démarrage du jeu, **sans avoir besoin de toucher au code Kotlin**.

## Étape 1 : Créer votre Palette de Couleurs (Via Material Theme Builder)

1. Allez sur l'outil officiel de Google : **[Material Theme Builder](https://m3.material.io/theme-builder)**
2. Utilisez l'outil pour générer votre palette Dark Theme visuellement (vous pouvez importer une image ou définir une couleur primaire).
3. Inspirez-vous des principes "Graphic Noir Futurism" :
    - Des fonds sombres (brun, métal froid, noir profond) pour `background` et `surface`.
    - Des accents "Néon" vifs (cyan acier, rouge vif, orange rouille) pour `primary`, `secondary` et `tertiary`.

## Étape 2 : Créer le Fichier JSON

1. Dans le projet, naviguez vers `app/src/main/assets/themes/`
2. Créez un nouveau fichier JSON, par exemple `cyberpunk.json`.
3. Remplissez-le avec la structure suivante :

```json
{
  "name": "CYBERPUNK",
  "colors": {
    "primary": "#FF00FF00",
    "secondary": "#FFFF0055",
    "tertiary": "#FF00FFFF",
    "background": "#FF05050A",
    "surface": "#FF151020",
    "surfaceVariant": "#FF2A2035",
    "onPrimary": "#FFE0E0E0",
    "onSecondary": "#FFE0E0E0",
    "onTertiary": "#FFE0E0E0",
    "onBackground": "#FFE0E0E0",
    "onSurface": "#FFD4C8B0",
    "onSurfaceVariant": "#FF7A6E60"
  },
  "graphics": {
    "outlineStrokeWidth": 3.0,
    "planetShadowAlpha": 0.6,
    "blurRadius": 12.0,
    "particleCountMultiplier": 1.0
  }
}
```

*Note : Les couleurs doivent toujours inclure la couche Alpha au début (Ex: `#FF...` pour 100% opaque).*

### Ajustement des Paramètres Graphiques (`graphics`)
- `outlineStrokeWidth` : L'épaisseur des traits d'encre BD autour des éléments de la carte.
- `planetShadowAlpha` : L'intensité de l'ombrage en hachures sur les planètes.
- `blurRadius` : La puissance du verre dépoli (Frosted Glass) de l'interface utilisateur.
- `particleCountMultiplier` : Le nombre d'étincelles/débris émis lors d'un combat (Mettez 2.0 pour des explosions massives, 0.5 pour un effet minimaliste).

## Étape 3 : Enregistrer le Thème dans le Modèle de Données

Pour que l'application puisse trouver votre JSON, ouvrez le fichier source :
`core/domain/src/main/kotlin/com/novaempire/core/domain/models/ThemeConfig.kt`

Ajoutez le nom de votre thème dans l'énumération `ThemeType` (le nom doit correspondre EXACTEMENT au `name` du JSON) :

```kotlin
@Serializable
enum class ThemeType {
    DEFAULT,
    HALLOWEEN,
    WINTER,
    CYBERPUNK // <--- Ajoutez votre thème ici
}
```

Puis dans `app/src/main/kotlin/com/novaempire/app/ui/theme/ThemeManager.kt`, ajoutez la ligne de chargement du JSON :
```kotlin
val cyberpunkJsonStr = context.assets.open("themes/cyberpunk.json").bufferedReader().use { it.readText() }
loadedThemes[ThemeType.CYBERPUNK] = parseThemeDefinition(cyberpunkJsonStr)
```

## Étape 4 : Activer le Thème (Tests ou Saisonnier)

Pour tester immédiatement votre thème, modifiez la fonction `getActiveTheme` dans `ThemeManager.kt` :

```kotlin
fun getActiveTheme(savedTheme: ThemeType? = null): ThemeType {
    return ThemeType.CYBERPUNK // Force votre thème
    // ...
```
