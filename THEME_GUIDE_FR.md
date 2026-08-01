# Guide de Création de Thème (Graphic Noir Futurism)

Ce guide explique pas à pas comment un graphiste (ou développeur UI) peut créer et intégrer un nouveau thème dans **Nova Empire**.

## Comprendre l'Architecture des Thèmes

Dans Nova Empire, un thème est défini par des fichiers JSON situés dans `app/src/main/assets/themes/`.
Le `ColorScheme` Material, la typographie et une partie des réglages de rendu s'adaptent à ce fichier
au démarrage du jeu. Une seule ligne de Kotlin reste à écrire pour déclarer un nouveau thème (une
entrée dans l'énumération `ThemeType`, cf. Étape 3).

⚠️ **Portée réelle du thème.** La palette de la carte tactique (couleurs des terrains, encre des
contours, couleurs de faction) est encore codée en dur dans `TacticalMapScreen.kt` et
`FactionSelectionScreen.kt` : changer de thème modifie l'interface, les panneaux et le texte, mais
laisse la carte quasiment inchangée. Voir `AUDIT_THEMES.md` (TH8) et `AUDIT_GRAPHISMES.md` (GR3).

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
- `particleCountMultiplier` : ⚠️ **Non branché à ce jour** — le champ est lu depuis le JSON mais
  aucun système de particules ne l'utilise encore. Le renseigner n'a aucun effet visible.

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

C'est la **seule** modification de code nécessaire : `ThemeManager` parcourt `ThemeType.entries` et
charge, pour chaque valeur, le fichier `assets/themes/<nom en minuscules>.json`. L'entrée
`CYBERPUNK` ci-dessus fait donc charger `themes/cyberpunk.json` sans autre intervention.

⚠️ Le nom du fichier doit être **exactement** le nom de l'énumération en minuscules. Si le fichier
est absent ou malformé, seul ce thème-là est perdu (les autres continuent de fonctionner) et l'erreur
est visible dans le Logcat sous le tag `ThemeManager`. Une couleur hexadécimale invalide est
remplacée par la couleur correspondante du thème par défaut, également avec un log.

## Étape 4 : Activer le Thème (Tests ou Saisonnier)

Pour tester immédiatement votre thème, modifiez la fonction `getActiveTheme` dans `ThemeManager.kt` :

```kotlin
fun getActiveTheme(savedTheme: ThemeType? = null): ThemeType {
    return ThemeType.CYBERPUNK // Force votre thème
    // ...
```
