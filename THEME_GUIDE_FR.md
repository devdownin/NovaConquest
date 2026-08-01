# Guide de Création de Thème (Graphic Noir Futurism)

Ce guide explique pas à pas comment un graphiste (ou développeur UI) peut créer et intégrer un nouveau thème dans **Nova Empire**.

## Comprendre l'Architecture des Thèmes

Dans Nova Empire, un thème est défini par des fichiers JSON situés dans `app/src/main/assets/themes/`.
Le `ColorScheme` Material, la typographie et une partie des réglages de rendu s'adaptent à ce fichier
au démarrage du jeu. Une seule ligne de Kotlin reste à écrire pour déclarer un nouveau thème (une
entrée dans l'énumération `ThemeType`, cf. Étape 3).

Un thème pilote trois choses : les couleurs de l'interface (`colors`), quelques réglages de rendu
(`graphics`) et la palette de la carte tactique (`terrain`).

ℹ️ **Ce qu'un thème ne change pas :** les couleurs de faction. Elles restent identiques d'un thème à
l'autre, délibérément — le joueur doit reconnaître ses flottes du premier coup d'œil, et une couleur
d'appartenance qui change avec la saison serait un piège de lisibilité, pas une décoration.

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
- `particleCountMultiplier` : Le nombre d'éclats d'encre projetés par une explosion de combat. La
  base est de 10 éclats ; mettez `2.0` pour des explosions massives (20), `0.5` pour un effet
  minimaliste (5), `0` pour les supprimer.

### La Palette de la Carte (`terrain`)

C'est la section qui donne son caractère à l'écran principal. Elle est **facultative** : un thème
qui l'omet hérite de la palette d'origine, sans erreur.

```json
  "terrain": {
    "void": "#FF181210",
    "asteroids": "#FF241C14",
    "asteroidRock": "#FF2A1C10",
    "nebula": "#FF261530",
    "nebulaHaze": "#FF3D2848",
    "planet": "#FF162018",
    "blackHole": "#FF1A0A00",
    "wormhole": "#FF12152A",
    "plasmaCloud": "#FF2A1208",
    "ionStorm": "#FF20202E",
    "anomaly": "#FF142218",
    "ink": "#FF130F0A",
    "unexplored": "#FF0D0A07",
    "explosionCore": "#FF8B3A0A",
    "explosionMid": "#FF3D1A06",
    "explosionEdge": "#FF1A0D04",
    "healthBarBackground": "#FF2D2620"
  }
```

- Les neuf premières après `void` sont les **fonds d'hexagone** par type de terrain.
- `asteroidRock` et `nebulaHaze` sont les détails dessinés *par-dessus* leur hexagone : la silhouette
  des rochers, le nuage de brume.
- `ink` est le trait de contour de toute la carte — c'est la couleur qui porte la direction
  artistique BD. La toucher change l'écran entier.
- `unexplored` est le fond des hexagones jamais visités.
- Les trois `explosion*` forment le dégradé de l'explosion de combat, du cœur vers le bord.

Conseil : gardez les fonds d'hexagone très sombres et proches les uns des autres (le terrain doit se
distinguer sans crier), et réservez le contraste aux unités et aux surcouches de sélection.

## Étape 3 : Enregistrer le Thème dans le Modèle de Données

Pour que l'application puisse trouver votre JSON, ouvrez le fichier source :
`core/domain/src/main/kotlin/com/novaempire/core/domain/theme/ThemeType.kt`

Ajoutez le nom de votre thème dans l'énumération `ThemeType` (le nom doit correspondre EXACTEMENT au `name` du JSON) :

```kotlin
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

### Vérifier son thème sans lancer le jeu

```powershell
./gradlew :core:domain:test --tests "com.novaempire.core.domain.theme.ShippedThemesTest"
```

Ce test lit les fichiers réellement livrés et échoue si un `ThemeType` n'a pas son JSON, s'il manque
une clé, si une couleur n'est pas analysable, si un réglage graphique est hors bornes ou si le champ
`name` ne correspond pas à l'énumération. C'est le moyen le plus rapide de valider un nouveau thème.

## Étape 4 : Activer le Thème

Le thème se choisit dans le jeu : **Menu principal → SETTINGS → THEME**. Les options sont
`AUTOMATIC` (suit le calendrier saisonnier), puis un choix explicite par thème. Le changement est
appliqué immédiatement et conservé d'une session à l'autre.

Les thèmes saisonniers ne s'activent d'eux-mêmes que si la préférence est sur `AUTOMATIC` :

| Fenêtre | Thème |
|---|---|
| 25 octobre → 5 novembre | `HALLOWEEN` |
| 20 décembre → 5 janvier | `WINTER` |
| le reste de l'année | `DEFAULT` |

Ces fenêtres sont définies dans `core/domain/.../theme/ThemeResolver.kt`. Un thème que vous ajoutez
n'y est pas rattaché : il est accessible via le sélecteur, et il faut modifier `seasonalTheme` pour
lui donner sa propre période.

*Note : la bascule saisonnière est évaluée au démarrage de l'écran, pas en continu — une application
laissée ouverte toute la nuit ne changera de thème qu'au prochain lancement.*
