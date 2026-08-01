# Audit — Gestion des thèmes

> Portée : le système de thèmes de bout en bout — modèle (`ThemeConfig`/`ThemeType` dans
> `:core:domain`), chargement et résolution (`ThemeManager`), application Compose (`Theme.kt`,
> `Type.kt`, `Color.kt`), consommation (`TacticalMapScreen`, `GraphicNoirComponents`,
> `SettingsScreen`), assets (`app/src/main/assets/themes/*.json`) et documentation
> (`THEME_GUIDE_FR.md`).
>
> ## ⚠️ Limite de cet audit
>
> **Aucun rendu n'a pu être observé.** Le proxy réseau refuse `dl.google.com` (403 sur le tunnel
> CONNECT), l'Android Gradle Plugin est donc inaccessible : ni build, ni émulateur, ni capture.
> Les corrections ci-dessous ont été relues ligne à ligne mais **n'ont pas été compilées**. Les
> modules purs (`:core:*`) ne sont pas touchés par ce lot, la CI ne couvre donc rien de ce code —
> voir TH10.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **TH1** | 🔴 **Majeur — ✅ corrigé** | Application | La typographie **figeait les couleurs de texte** du thème DEFAULT : changer de thème changeait les fonds, jamais l'encre |
| **TH2** | 🔴 **Majeur — ✅ corrigé** | Robustesse | Une couleur hexadécimale invalide dans un JSON **plantait l'application en pleine composition**, pas au chargement |
| **TH3** | 🟠 **Moyen — ✅ corrigé** | Cohérence | **Trois chemins de résolution** du thème actif, désynchronisés : la carte pouvait dessiner en DEFAULT pendant qu'elle s'affichait en HALLOWEEN |
| **TH4** | 🟠 **Moyen — ✅ corrigé** | Robustesse | Chargement **tout-ou-rien** : un seul JSON cassé faisait perdre les trois thèmes, silencieusement |
| **TH5** | 🟠 **Moyen — ✅ corrigé** | Rendu | Seuls **12 rôles Material sur ~30** étaient définis ; `Snackbar`, `Switch`, bordures gardaient le mauve Material par défaut |
| TH6 | 🟡 Faible — ✅ corrigé | Performance | Le `ColorScheme` était **reconstruit à chaque recomposition** de la racine, invalidant tous ses lecteurs |
| TH7 | 🟡 Faible — ✅ corrigé | Config | `planetShadowAlpha` documenté et présent dans les JSON mais **jamais lu** ; valeur de secours désalignée du JSON par défaut |
| **TH8** | 🔴 **Majeur — ouvert** | Produit | **Aucun moyen de choisir un thème** : `themeConfig` n'est écrit nulle part, il n'existe ni intent ni écran de sélection |
| TH9 | 🟠 Moyen — ouvert | Architecture | Le thème, une **préférence utilisateur**, vit dans le `GameState` sérialisé — donc dans le format de sauvegarde |
| TH10 | 🟠 Moyen — ouvert | Testabilité | Toute la logique de thème est dans `:app` : **zéro test**, la CI ne l'exerce jamais |
| TH11 | 🟡 Faible — ouvert | Config | `particleCountMultiplier` toujours mort ; palette dupliquée entre `Color.kt` et `default.json` |
| TH12 | 🟡 Faible — ouvert | Produit | Le choix « DEFAULT » ne peut pas exister : il est **toujours écrasé** par le thème saisonnier |

---

## 2. Corrigés

### TH1 — 🔴 Le thème changeait les fonds mais jamais le texte  ✅ **corrigé**

`Type.kt` posait une couleur en dur dans chaque `TextStyle` :

```kotlin
displayLarge = TextStyle(..., color = TextPrimary)   // #FFD4C8B0, sépia
bodyMedium   = TextStyle(..., color = TextSecondary) // #FF7A6E60
```

Or dans Material 3, un `color` défini dans le style **gagne sur `LocalContentColor`**. Résultat :
en thème WINTER, `onBackground` valait `#FFEBF5FB` (blanc bleuté) mais tous les `Text` de
l'application continuaient de s'afficher en sépia `#FFD4C8B0` sur fond bleu nuit — la faute de goût
exacte que le thème était censé éviter, et un problème de contraste au passage.

C'est le défaut le plus visible du système : sur les trois thèmes livrés, deux affichaient un texte
qui n'était pas le leur.

**Correction.** `Type.kt` expose désormais `novaTypography(colorScheme)` au lieu d'un `val`
constant ; `displayLarge/headlineLarge/headlineMedium/bodyLarge/labelLarge` tirent
`colorScheme.onBackground`, `bodyMedium` tire `colorScheme.onSurfaceVariant`. Le mapping est choisi
pour être **iso-visuel sur DEFAULT** : dans `default.json`, `onBackground` vaut exactement
`#FFD4C8B0` (= `TextPrimary`) et `onSurfaceVariant` exactement `#FF7A6E60` (= `TextSecondary`).
Aucun changement d'apparence sur le thème par défaut, thème correct sur les deux autres.

### TH2 — 🔴 Un `#` mal tapé plantait l'application  ✅ **corrigé**

Les couleurs étaient stockées **sous forme de chaînes** dans `ThemeDefinition`, et converties
seulement au moment de construire le `ColorScheme` :

```kotlin
private fun parseColor(colorString: String): Color =
    Color(android.graphics.Color.parseColor(colorString))   // lève IllegalArgumentException
```

`getColorSchemeForTheme` est appelée depuis `NovaEmpireTheme`, donc **pendant la composition**. Le
`try/catch` de `init()` ne couvrait que la lecture et le parsing JSON, pas la conversion des
couleurs. Un `#FG4A7B9D` — exactement le genre de coquille qu'un graphiste produit en éditant les
JSON à la main, ce que `THEME_GUIDE_FR.md` l'invite explicitement à faire — passait le chargement
sans bruit puis faisait planter le premier rendu.

La validation se faisait donc à la mauvaise couche : le plus tard possible, au plus près de l'écran.

**Correction.** Les couleurs sont converties **une fois au chargement**, dans `resolveTheme`, et
chaque conversion a une valeur de secours prise sur la palette de référence :

```kotlin
private fun parseColor(colorString: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(colorString)) }
    catch (e: IllegalArgumentException) { Log.e(TAG, "Couleur invalide…"); fallback }
```

Un thème partiellement invalide s'affiche maintenant en mode dégradé avec un log explicite, au lieu
de faire tomber l'application. Effet de bord bienvenu : plus aucun parsing de chaîne dans le chemin
de composition (cf. TH6).

### TH3 — 🟠 Trois résolutions concurrentes du thème actif  ✅ **corrigé**

Le thème « effectif » se calcule en résolvant le choix sauvegardé contre le calendrier saisonnier
(`ThemeManager.getActiveTheme`). Trois appelants le faisaient **différemment** :

| Appelant | Code | Thème obtenu |
|---|---|---|
| `Theme.kt` (couleurs) | `getActiveTheme(themeType)` | résolu ✅ |
| `TacticalMapScreen` (rendu carte) | `gameState.themeConfig.currentTheme` **brut** | jamais résolu ❌ |
| `GraphicNoirComponents` (flou) | `remember { getActiveTheme() }` sans clé ni thème sauvegardé | résolu une seule fois, choix ignoré ❌ |

Concrètement, le 31 octobre : interface aux couleurs HALLOWEEN, mais carte tactique dessinée avec
`outlineStrokeWidth = 3.0` (DEFAULT) au lieu de `3.5`. Le `remember` sans clé de
`GraphicNoirComponents` figeait en plus le flou à sa valeur du premier rendu — un futur sélecteur de
thème n'aurait pas rafraîchi les panneaux.

**Correction.** `NovaEmpireTheme` résout le thème **une seule fois** et le publie via deux
`staticCompositionLocalOf` :

```kotlin
val LocalThemeType = staticCompositionLocalOf { ThemeType.DEFAULT }
val LocalGraphicsConfig = staticCompositionLocalOf { ThemeManager.DEFAULT_GRAPHICS }
```

Les deux consommateurs lisent `LocalGraphicsConfig.current`. Il n'y a plus qu'une source de vérité,
et c'est l'idiome Compose attendu plutôt qu'un appel de singleton depuis un composable.

### TH4 — 🟠 Chargement tout-ou-rien, et silencieux  ✅ **corrigé**

```kotlin
try {
    val defaultJsonStr   = context.assets.open("themes/default.json")…
    val halloweenJsonStr = context.assets.open("themes/halloween.json")…
    val winterJsonStr    = context.assets.open("themes/winter.json")…
    loadedThemes[DEFAULT] = parseThemeDefinition(defaultJsonStr)
    …
} catch (e: Exception) { Log.e(…) }
```

Les trois fichiers étaient ouverts avant qu'aucun ne soit enregistré : une virgule en trop dans
`winter.json` — ou son absence pure et simple — laissait `loadedThemes` **vide**, y compris pour
DEFAULT. L'application basculait alors sur `fallbackColorScheme`, une quatrième copie de la palette,
sans aucun signal côté utilisateur. La liste des thèmes était par ailleurs codée en dur, ce qui
contredisait la promesse du guide (« sans avoir besoin de toucher au code Kotlin »).

**Correction.** Boucle sur `ThemeType.entries` avec un `try/catch` **par thème** et une convention
de nommage `themes/<nom en minuscules>.json`. Un thème cassé ne coûte plus que lui-même, et ajouter
un thème ne demande plus de toucher à `ThemeManager` — seulement une entrée d'énumération. Le guide
a été mis à jour en conséquence.

### TH5 — 🟠 Les rôles Material non renseignés restaient mauves  ✅ **corrigé**

`darkColorScheme(...)` ne recevait que 12 rôles. Les ~18 autres — `outline`, `primaryContainer`,
`inverseSurface`, `scrim`, `surfaceTint`… — gardaient la **palette Material baseline**, c'est-à-dire
les violets de démonstration de Google. Tant que l'interface n'utilise que six rôles
(`surface`, `surfaceVariant`, `background`, `onSurface`, `onSurfaceVariant`, `primary`) le problème
reste latent, mais les composants Material standard, eux, piochent dans les rôles non définis :

- le `Snackbar` d'erreur de chargement (`MainActivity`) utilise `inverseSurface`/`inverseOnSurface` ;
- les `Switch` de `SettingsScreen` utilisent `outline` pour la bordure non cochée ;
- tout `Card`/`Chip`/`TextField` ajouté demain tirera `*Container` et `outlineVariant`.

**Correction.** `ThemeManager.novaColorScheme` **dérive** les rôles manquants des douze rôles
fournis par les JSON : conteneurs = couleur d'accent composée à 25 % sur `surface`, `outline` =
`onSurfaceVariant`, `outlineVariant` = `surfaceVariant`, surfaces inverses croisées, `scrim` =
`background`, `surfaceTint` = `primary`. Les rôles `error*` sont volontairement laissés à la
baseline : le rouge d'erreur reste sémantiquement correct dans les trois thèmes, et WINTER n'a
aucune couleur chaude à lui prêter.

### TH6 — 🟡 Le `ColorScheme` était reconstruit à chaque recomposition  ✅ **corrigé**

```kotlin
val colorScheme = ThemeManager.getColorSchemeForTheme(ThemeManager.getActiveTheme(themeType))
```

Aucun `remember`. `NovaEmpireTheme` enveloppe tout le contenu de `setContent`, qui recompose à
**chaque changement de `GameState`** — donc à chaque déplacement d'unité. Chaque passe allouait un
`Calendar` (lecture d'horloge), parsait 12 chaînes hexadécimales et construisait un `ColorScheme`
neuf. Comme `MaterialTheme` publie ce `ColorScheme` dans un `CompositionLocal`, une nouvelle
instance à chaque fois **invalide tous ses lecteurs** : l'interface entière se recomposait à cause
du thème, pour une valeur qui ne change jamais en cours de partie.

**Correction.** Thème actif, `ColorScheme`, `GraphicsConfig` et typographie sont mémoïsés
(`remember(themeType)` / `remember(activeTheme)` / `remember(colorScheme)`). Contrepartie assumée :
la bascule saisonnière ne se fait plus « à minuit pile » si l'application tourne depuis la veille,
elle attend la prochaine recréation de l'activité.

Nettoyage joint : `darkTheme`, un paramètre inutilisé de `NovaEmpireTheme` (le thème clair n'existe
pas), et deux imports morts (`isSystemInDarkTheme`, `darkColorScheme`) ont été supprimés.

### TH7 — 🟡 `planetShadowAlpha` : trois sources, aucune connexion  ✅ **corrigé**

Le champ existait dans les trois JSON, était parsé dans `GraphicsConfig`, était documenté dans
`THEME_GUIDE_FR.md` (« L'intensité de l'ombrage en hachures sur les planètes ») — et n'était lu
**nulle part**. `drawPlanet` codait la valeur en dur :

```kotlin
color = inkBlack.copy(alpha = 0.6f)
```

Pire, les deux valeurs de secours divergeaient déjà : `getGraphicsConfig` repliait sur
`GraphicsConfig(3f, 0.7f, 12f, 1f)` quand `default.json` déclare `0.6`. Trois vérités pour un
réglage.

**Correction.** `drawPlanet` lit `graphicsConfig.planetShadowAlpha`, et la valeur de secours
(`ThemeManager.DEFAULT_GRAPHICS`, désormais nommée et unique) est alignée sur `default.json`. Le
rendu du thème par défaut est inchangé (0.6 = 0.6) ; HALLOWEEN gagne l'ombrage plus dense (0.8) et
WINTER l'ombrage plus léger (0.5) que leurs auteurs avaient demandés.

---

## 3. Ouverts

### TH8 — 🔴 Le joueur ne peut pas choisir son thème

C'est le vrai trou du système, et il est purement produit :

- `GameState.themeConfig` **n'est écrit nulle part**. Aucun `.copy(themeConfig = …)` dans tout le
  dépôt, aucun `GameIntent` de changement de thème, aucun `when` correspondant dans
  `GameEngine.reduce`. La valeur est donc constante : `ThemeType.DEFAULT`.
- `SettingsScreen` propose « Holographic Effects » et « High Contrast Mode » — deux interrupteurs
  branchés sur un `remember` local, que « APPLY SETTINGS » ne persiste pas et que personne ne lit —
  mais **aucun sélecteur de thème**.

Conséquence : les thèmes HALLOWEEN et WINTER ne sont atteignables que par la fenêtre calendaire
(25 oct.–5 nov., 20 déc.–5 janv.), soit ~23 jours par an, et le joueur ne peut ni les activer hors
saison ni les refuser pendant.

Chemin de correction, dans l'ordre de dépendance : (1) trancher TH9 — où vit la préférence ; (2) un
sélecteur dans `SettingsScreen` (les trois thèmes + une option « Automatique/Saisonnier ») ; (3) la
persistance associée. Rien de tout ça n'est fait ici : c'est une décision de conception, pas une
correction de défaut.

### TH9 — 🟠 Une préférence d'affichage stockée dans le format de sauvegarde

`ThemeConfig` vit dans `:core:domain`, est `@Serializable`, et est un champ de `GameState` — donc
sérialisé dans chaque autosave par `SavedGameSnapshotCodec`. Trois conséquences :

1. **Portée fausse.** Un thème est une préférence d'application, pas un état de partie. Rangé là, il
   serait par construction *par sauvegarde* : charger une vieille partie changerait l'apparence du
   jeu.
2. **Avant la partie, rien.** Menu principal, sélection de faction, écran de chargement s'affichent
   sur un `GameState` initial — le thème sauvegardé serait ignoré sur tous ces écrans.
3. **Couplage au format de sauvegarde.** Comme le rappelle `CLAUDE.md`, il n'y a **pas de couche de
   migration**. Ajouter une valeur à `ThemeType` est sûr (les anciennes sauvegardes contiennent
   `"DEFAULT"`), mais en **renommer ou supprimer** une invaliderait toutes les sauvegardes
   existantes — un risque disproportionné pour un réglage d'affichage.

Recommandation : sortir la préférence du `GameState` vers un `DataStore`/`SharedPreferences` exposé
par `GameViewModel`, et ne garder `ThemeType` dans `:core:domain` que si un contenu de jeu en dépend
réellement (ce n'est pas le cas aujourd'hui). À faire **avant** TH8, dont c'est le préalable.

### TH10 — 🟠 Aucun test, et pas testable en l'état

`ThemeManager` est un `object` global à état mutable, initialisé par `init(Context)` depuis
`MainActivity.onCreate`, dans le module `:app`. Or la CI n'exécute que
`:core:hex:test :core:domain:test :core:engine:test` — **aucune ligne du système de thèmes n'est
couverte**, y compris les parties qui n'ont rien d'Android :

- la fenêtre saisonnière de `getActiveTheme` (bornes de dates, priorité choix/saison) : logique
  pure, testable telle quelle ;
- le parsing d'une définition de thème et la tolérance aux valeurs invalides : ne dépend d'`android`
  que par `Color.parseColor`, trivialement remplaçable ;
- la validité des JSON livrés (chaque `ThemeType` a-t-il un fichier ? toutes les clés ? toutes les
  couleurs parsables ?) — un test qui aurait rendu TH2 et TH4 impossibles à écrire.

Recommandation : déplacer la résolution saisonnière et le modèle de thème vers `:core:domain` (ou un
`:core:theme`), ne laisser dans `:app` que la conversion `String → Color` et la construction du
`ColorScheme`, puis injecter le manager plutôt que d'y accéder en singleton. Même remarque que pour
`UtilityEvaluator` dans `suggestions.md` : le singleton global est la cause racine.

### TH11 — 🟡 Un réglage encore mort, et une palette dupliquée

- **`particleCountMultiplier`** est parsé, exposé, documenté (« Mettez 2.0 pour des explosions
  massives ») — et lu par personne. Contrairement à `planetShadowAlpha`, il n'a pas de site d'appel
  évident : le rendu des combats de `TacticalMapScreen` n'a pas de système de particules
  paramétrable. Le guide a été corrigé pour ne plus promettre un effet inexistant ; brancher le
  réglage reste à faire (ou supprimer le champ).
- **Trois copies de la palette par défaut** coexistent : les constantes de `Color.kt`,
  `assets/themes/default.json`, et les valeurs de secours de `ThemeManager` (qui réutilisent
  `Color.kt`, ce qui limite la casse). Elles concordent aujourd'hui ; rien ne le garantit demain.
  Un test de non-régression comparant `default.json` aux constantes suffirait — voir TH10.

### TH12 — 🟡 « DEFAULT » n'est pas un choix exprimable

```kotlin
fun getActiveTheme(savedTheme: ThemeType? = null): ThemeType {
    if (savedTheme != null && savedTheme != ThemeType.DEFAULT) return savedTheme
    // … sinon, fenêtre calendaire
}
```

`DEFAULT` est traité comme « pas de choix », donc systématiquement écrasé par le thème saisonnier.
Le joueur qui, en décembre, veut explicitement la palette d'origine n'a aucun moyen de la demander.
Le modèle manque simplement d'une valeur « Automatique » distincte de « Thème par défaut » —
`ThemeConfig.currentTheme: ThemeType? = null` (null = automatique) ou une entrée `AUTO`.

Corriger ça isolément serait une régression (`MainActivity` passe toujours `DEFAULT` : le thème
saisonnier disparaîtrait purement et simplement). C'est donc à traiter **avec** TH8/TH9, pas avant.

---

## 4. Fichiers touchés

| Fichier | Nature |
|---|---|
| `app/…/ui/theme/Type.kt` | `Typography` constante → `novaTypography(colorScheme)` (TH1) |
| `app/…/ui/theme/ThemeManager.kt` | Conversion au chargement + valeurs de secours (TH2), chargement par thème (TH4), rôles Material dérivés (TH5), `DEFAULT_GRAPHICS` unifié (TH7) |
| `app/…/ui/theme/Theme.kt` | Mémoïsation (TH6), `LocalThemeType`/`LocalGraphicsConfig` (TH3), nettoyage |
| `app/…/ui/screens/TacticalMapScreen.kt` | Lecture via `LocalGraphicsConfig` (TH3), `planetShadowAlpha` branché (TH7) |
| `app/…/ui/components/GraphicNoirComponents.kt` | Lecture via `LocalGraphicsConfig` (TH3) |
| `THEME_GUIDE_FR.md` | Procédure d'ajout à jour, portée réelle et champs morts signalés |

**Non compilé** — cf. la limite en tête de document.
