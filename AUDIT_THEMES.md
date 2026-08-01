# Audit — Gestion des thèmes

> Portée : le système de thèmes de bout en bout — modèle et résolution (`:core:domain/theme`),
> chargement et application Compose (`ThemeManager`, `Theme.kt`, `Type.kt`, `Color.kt`),
> persistance de la préférence, consommation (`TacticalMapScreen`, `GraphicNoirComponents`,
> `SettingsScreen`), assets (`app/src/main/assets/themes/*.json`) et documentation
> (`THEME_GUIDE_FR.md`).
>
> ## ⚠️ Limite de cet audit
>
> **Aucun rendu n'a pu être observé.** Le proxy réseau refuse `dl.google.com` (403 sur le tunnel
> CONNECT), l'Android Gradle Plugin est donc inaccessible : ni build Gradle du dépôt, ni émulateur,
> ni capture. Un défaut purement visuel échapperait à cette analyse.
>
> Ce qui **a** pu être vérifié, en reconstruisant les modules purs dans un projet Gradle autonome
> (Kotlin 1.9.23, JDK 21, dépendances Maven Central uniquement) :
>
> - `:core:hex` + `:core:domain` + `:core:engine` compilent, **204 tests passent, 0 échec** — dont
>   les 22 tests de thème ajoutés par ce lot ;
> - les sources de `:app` ont été type-checkées contre tout sauf le classpath Android/Compose : il
>   ne reste **aucune erreur** portant sur l'API de thème ou sur du code de ce dépôt, seulement les
>   symboles `android.*` / `androidx.*` absents de ce montage.
>
> Autrement dit : la logique est testée, la couche Compose est relue mais **pas compilée**.

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
| **TH8** | 🔴 **Majeur — ✅ corrigé** | Produit | **Aucun moyen de choisir un thème** : `themeConfig` n'était écrit nulle part, il n'existait ni intent ni écran de sélection |
| TH9 | 🟠 Moyen — ✅ corrigé | Architecture | Le thème, une **préférence utilisateur**, vivait dans le `GameState` sérialisé — donc dans le format de sauvegarde |
| TH10 | 🟠 Moyen — ✅ corrigé | Testabilité | Toute la logique de thème était dans `:app` : **zéro test**, la CI ne l'exerçait jamais |
| TH11 | 🟡 Faible — ✅ corrigé | Config | `particleCountMultiplier` mort ; palette dupliquée entre le code et `default.json` |
| TH12 | 🟡 Faible — ✅ corrigé | Produit | Le choix « DEFAULT » ne pouvait pas exister : il était **toujours écrasé** par le thème saisonnier |
| **TH13** | 🔴 **Majeur — ✅ corrigé** | Portée | La **palette de la carte tactique était codée en dur** : changer de thème ne changeait pas l'écran principal |
| **TH14** | 🟠 **Moyen — ✅ corrigé** | Produit | Les autres réglages (volumes, effets, contraste) n'étaient **ni lus ni persistés** — « APPLY SETTINGS » ne faisait rien |
| TH15 | 🟠 Moyen — ✅ corrigé | Testabilité | **Aucun test d'interface** sur le sélecteur, et aucune source `androidTest` dans le dépôt |

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
chaque rôle a une valeur de secours :

```kotlin
fun color(value: String, fallbackValue: String): Color =
    Color(HexColor.parse(value) ?: HexColor.parse(fallbackValue)!!)
```

`HexColor.parse` (ajouté par TH10, dans `:core:domain`) retourne `null` au lieu de lever — l'échec
devient une valeur que l'appelant traite, et l'analyse est testable sur la JVM. Un thème
partiellement invalide s'affiche donc en mode dégradé avec un log explicite, au lieu de faire tomber
l'application ; et `ShippedThemesTest` fait échouer le build avant d'en arriver là. Effet de bord
bienvenu : plus aucun parsing de chaîne dans le chemin de composition (cf. TH6).

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

## 3. Corrigés dans un second temps

Les cinq points ci-dessous étaient laissés ouverts par la première passe parce qu'ils demandaient
des décisions de conception plutôt que des corrections de défaut. Ils sont traités ici, dans leur
ordre de dépendance : la préférence doit exister (TH9) avant qu'on puisse la choisir (TH8), et
« automatique » doit être exprimable (TH12) avant que le choix ait un sens.

### TH9 — 🟠 La préférence sort du format de sauvegarde  ✅ **corrigé**

`ThemeConfig` était un champ `@Serializable` de `GameState`, donc écrit dans chaque autosave. Trois
conséquences, toutes réelles : le thème n'existait pas tant qu'aucune partie n'était chargée (menu
principal, sélection de faction), il aurait été *par sauvegarde* plutôt que par joueur, et renommer
une valeur de `ThemeType` aurait touché la compatibilité des sauvegardes.

**Correction.** `GameState.themeConfig` et la classe `ThemeConfig` sont supprimés. La préférence est
persistée par `SettingsStore` (`SharedPreferences`, cf. TH14) et exposée par
`GameViewModel.themePreference: StateFlow<ThemeType?>`.

Aucune migration de sauvegarde n'est nécessaire : `SavedGameSnapshotCodec` décode avec
`ignoreUnknownKeys = true`, la clé `themeConfig` des anciennes sauvegardes est simplement ignorée.
Vérifié — la suite `:core:engine` (dont `SaveMigrationsTest` et les tests de codec) passe sans
modification.

`ThemeType` perd son `@Serializable` : ce n'est plus un état de partie, et le retirer rend la
séparation structurelle plutôt que documentaire.

### TH12 — 🟡 « Automatique » devient une valeur à part entière  ✅ **corrigé**

L'ancienne signature ne pouvait pas distinguer les deux cas :

```kotlin
if (savedTheme != null && savedTheme != ThemeType.DEFAULT) return savedTheme  // DEFAULT = pas de choix
```

La préférence est désormais un `ThemeType?` où **`null` = automatique**. Choisir explicitement
`DEFAULT` en pleine période de fêtes est respecté ; ne rien choisir laisse le calendrier décider.
C'est le comportement par défaut à l'installation, donc la bascule saisonnière fonctionne toujours
sans réglage.

Le test qui verrouille exactement le défaut d'origine :

```kotlin
@Test fun `choisir DEFAULT pendant une fenetre saisonniere est respecte`() {
    assertEquals(ThemeType.DEFAULT, ThemeResolver.resolve(ThemeType.DEFAULT, LocalDate.of(2026, 12, 25)))
}
```

### TH8 — 🔴 Le joueur peut enfin choisir son thème  ✅ **corrigé**

`SettingsScreen` gagne une section `THEME` avec quatre options — `AUTOMATIC`, `NOIR FUTURISM`,
`HALLOWEEN`, `WINTER` — et une ligne d'état qui dit ce que l'automatique donne *aujourd'hui*
(« Seasonal — currently DEFAULT ») ou signale qu'un choix manuel désactive les thèmes saisonniers.

Le choix s'applique **immédiatement**, à l'inverse du schéma brouillon/`APPLY SETTINGS` des autres
réglages : le thème est le seul dont l'effet est visible sans rien valider, et un aperçu instantané
vaut mieux qu'un bouton qui n'annonce pas à quoi il engage. L'écart est assumé et commenté dans le
code — les autres réglages de cet écran restent, eux, ni lus ni persistés (cf. §5).

Le chemin complet : `SettingsScreen` → `GameViewModel.updateSettings` → `SettingsStore`
(écriture) + `StateFlow` (état) → `MainActivity` → `NovaEmpireTheme(themePreference)` →
`ThemeResolver`. HALLOWEEN et WINTER, jusque-là accessibles ~23 jours par an, le sont maintenant
toute l'année.

### TH10 — 🟠 La logique de thème descend dans `:core:domain`, et la CI l'exerce  ✅ **corrigé**

Conformément à la recommandation de la première passe, le partage est désormais :

| Où | Quoi | Testé par la CI |
|---|---|---|
| `:core:domain/theme` | `ThemeType`, `ThemeDefinition`/`ThemeColors`/`GraphicsConfig`, `ThemeParser`, `HexColor`, `ThemeResolver`, `ThemeDefaults` | ✅ |
| `:app` | lecture des assets, `Color`/`ColorScheme`, `SettingsStore` | ❌ (Android) |

`android.graphics.Color.parseColor` disparaît au profit de `HexColor.parse`, un analyseur pur qui
retourne `null` au lieu de lever — c'est ce qui rend la validation des couleurs testable **et** qui
supprime définitivement le crash de TH2 à sa racine.

Quatre classes de test, 22 cas, dans `:core:domain` (que la CI exécute déjà via
`gradle :core:domain:test`) :

- `ThemeResolverTest` — bornes des fenêtres saisonnières (24/25 octobre, 5/6 novembre, 19/20
  décembre, 5/6 janvier), passage du nouvel an, priorité choix/saison ;
- `HexColorTest` — `#AARRGGBB`, `#RRGGBB`, casse, espaces, et les entrées invalides qui faisaient
  planter l'application ;
- `ThemeParserTest` — clé manquante, clés inconnues tolérées, couleur invalide et réglage hors
  bornes signalés sans faire échouer le parsing ;
- `ShippedThemesTest` — valide les **fichiers réellement livrés**.

Ce dernier méritait un peu de plomberie : le `build.gradle.kts` de `:core:domain` expose
`app/src/main/assets` à son classpath de **test** uniquement.

```kotlin
sourceSets { named("test") { resources.srcDir("../../app/src/main/assets") } }
```

Les JSON restent donc là où l'application les lit — aucun changement de chemin à l'exécution — mais
la CI vérifie désormais que chaque `ThemeType` a son fichier, qu'il parse, que ses douze couleurs
sont valides, que ses réglages graphiques sont dans les bornes et que son champ `name` correspond à
l'énumération. Les défauts TH2 et TH4 seraient aujourd'hui des échecs de build.

### TH11 — 🟡 Le dernier réglage mort est branché, la palette n'est plus dupliquée  ✅ **corrigé**

**`particleCountMultiplier`** n'avait aucun système à piloter : l'explosion de combat n'était qu'un
dégradé radial. `drawExplosionShards` en ajoute un — des éclats d'encre projetés depuis l'impact,
dont le nombre est `10 × particleCountMultiplier`. Les angles suivent l'angle d'or et les longueurs
dérivent de l'indice de l'éclat : pas de `Random` dans la passe de dessin, sinon les éclats
scintilleraient à chaque frame de l'animation. Les thèmes livrés donnent 10 éclats (DEFAULT),
12 (HALLOWEEN) et 15 (WINTER) ; le guide promettait « 2.0 pour des explosions massives », c'est
maintenant vrai (20).

**La palette de secours** ne duplique plus rien : `ThemeManager` la construit à partir de
`ThemeDefaults.FALLBACK`, une copie compilée du thème par défaut, et `ShippedThemesTest` vérifie
qu'elle est **identique** à `themes/default.json`. C'est exactement le type de dérive qui existait
déjà (`planetShadowAlpha` valait 0.7 côté code et 0.6 côté JSON) ; elle est maintenant impossible
sans casser le build.

---

## 4. Corrigés dans un troisième temps

### TH13 — 🔴 Le thème n'atteignait pas l'écran principal  ✅ **corrigé**

C'est le constat GR3 de `AUDIT_GRAPHISMES.md`, resté ouvert depuis : toute la palette de la carte
vivait dans `TacticalMapScreen.kt`, en littéraux.

```kotlin
TerrainType.EMPTY -> Color(0xFF181210)   // béton froid vide
TerrainType.NEBULA -> Color(0xFF261530)  // violet brume épais
val inkBlack = Color(0xFF130F0A)         // répété dans 8 fonctions de dessin
```

Passer en HALLOWEEN ou WINTER transformait donc les panneaux, le texte et les explosions, mais
laissait les hexagones exactement identiques — sur l'écran que le joueur regarde le plus.

**Correction.** Une section `terrain` (17 couleurs) est ajoutée au modèle de thème et aux trois JSON
livrés : fonds d'hexagone par terrain, détails dessinés par-dessus (`asteroidRock`, `nebulaHaze`),
encre des contours, hexagone inexploré, dégradé d'explosion, fond de jauge de PV. Côté `:app`,
`MapPalette` les convertit une fois et `LocalMapPalette` les publie ; les fonctions de dessin la
reçoivent en paramètre, comme le faisait déjà `GraphicsConfig`. Il ne reste **aucune couleur
littérale** dans `TacticalMapScreen`.

Deux garde-fous de conception :

- la section est **facultative** — chaque champ a pour valeur par défaut la couleur historique, donc
  un thème tiers qui l'omet reste valide et rend comme avant ;
- `default.json` reprend exactement les anciennes valeurs, **le thème par défaut est donc inchangé
  au pixel près**. Seuls HALLOWEEN et WINTER gagnent une carte à leurs couleurs.

Les palettes de carte d'HALLOWEEN et WINTER sont des propositions, cohérentes avec les couleurs
d'interface déjà validées de chaque thème (mêmes familles de teintes, luminance comparable aux
valeurs d'origine) — mais **aucun rendu n'a pu être observé**, cf. la limite en tête de document.
C'est le seul endroit de ce lot où un œil de graphiste reste nécessaire.

**Ce qui n'est délibérément pas thémé : les couleurs de faction.** Elles restent identiques d'un
thème à l'autre. Le joueur doit reconnaître ses flottes du premier coup d'œil ; une couleur
d'appartenance qui change avec la saison serait un piège de lisibilité, pas une décoration. C'est un
choix, pas un oubli — et il est écrit dans `THEME_GUIDE_FR.md`.

### TH14 — 🟠 Les réglages ne servaient à rien  ✅ **corrigé**

`SettingsScreen` affichait quatre réglages branchés sur des `remember` locaux. Personne ne les
lisait, rien ne les écrivait, et les deux boutons du bas faisaient la même chose :

```kotlin
IndustrialButton(text = "CANCEL",         onClick = onBackClick, …)
IndustrialButton(text = "APPLY SETTINGS", onClick = onBackClick, …)   // strictement identique
```

**Correction.** `AppSettings` (thème, volume général, volume des effets, effets holographiques,
contraste élevé) est persisté par `SettingsStore` et exposé par `GameViewModel.settings`. Chaque
réglage a maintenant un consommateur réel :

| Réglage | Effet |
|---|---|
| Master Volume | `AudioManager` applique le volume à toutes les lectures (elles étaient toutes à `1f`) |
| SFX Volume | multiplie le volume des bruits de **combat** seulement — baisser les « SFX » ne doit pas étouffer les clics d'interface |
| Holographic Effects | coupe le flou « verre dépoli » des panneaux, les trames de fond (`HalftoneBackground`, `NoiseOverlay`) et le balayage de la carte |
| High Contrast Mode | contours de carte opaques et épaissis (4 px au lieu de 2,5), texte secondaire remonté à la couleur du texte principal |

Les effets décoratifs se désactivent **eux-mêmes** en lisant `LocalDisplaySettings`, plutôt
qu'en imposant une condition à chacun de leurs appelants — `HalftoneBackground` est utilisé par cinq
écrans.

Le schéma brouillon/`APPLY` disparaît : tout s'applique et se persiste immédiatement, et les deux
boutons sont remplacés par un seul `BACK`. Un « APPLY » n'a de sens que s'il existe un « CANCEL » qui
annule vraiment ; ici chaque réglage a un effet visible ou audible tout de suite, donc l'aperçu en
direct est à la fois plus simple et plus honnête. C'était aussi la seule façon de lever
l'incohérence signalée en TH8 (le thème s'appliquait déjà en direct, `CANCEL` ne l'annulait pas).

### TH15 — 🟠 Le sélecteur est couvert par des tests  ✅ **corrigé**

Le dépôt n'avait **aucune source `androidTest`** ; le job `android-test` de la CI ne lançait donc
rien, et sur `main` uniquement.

La couverture est découpée selon ce que chaque niveau peut réellement vérifier :

| Test | Où | Quand |
|---|---|---|
| `ThemeSelectorLabelsTest` (4 cas) | `app/src/test` — JVM pur | **à chaque poussée** |
| `ThemeSelectorTest` (6 cas) | `app/src/androidTest` — Compose | job `android-test`, sur `main` |

Pour que le premier existe, la partie non graphique du sélecteur (options, `testTag`, texte d'état)
est extraite dans `ThemeSelectorLabels.kt`, **sans aucun import Compose**. Le fichier se compile et
se teste sur la JVM ; le composable ne garde que le rendu. Le pipeline gagne au passage une étape
`gradle :app:testDebugUnitTest` — les tests JVM du module Android n'étaient jamais exécutés.

Le test d'interface couvre l'affichage des quatre options, la remontée de chaque choix (dont
`DEFAULT`, le défaut d'origine de TH12), le retour à l'automatique, la ligne d'état et les
interrupteurs. Il cible par `testTag` et non par texte : la ligne d'état contient elle aussi le nom
du thème actif, donc un test lancé un 31 octobre trouverait deux nœuds pour « HALLOWEEN ».

---

## 5. Fichiers touchés

### `:core:domain` — modèle et logique pure (nouveau)

| Fichier | Rôle |
|---|---|
| `theme/ThemeType.kt` | Énumération + convention `assetPath` (déplacé depuis `models/ThemeConfig.kt`) |
| `theme/ThemeDefinition.kt` | Modèle `@Serializable`, validation (`problems()`), `ThemeParser`, `ThemeDefaults.FALLBACK` |
| `theme/HexColor.kt` | Analyse hexadécimale non levante |
| `theme/ThemeResolver.kt` | Résolution préférence × calendrier |
| `state/GameState.kt` | Champ `themeConfig` retiré |
| `models/ThemeConfig.kt` | **Supprimé** |
| `build.gradle.kts` | JUnit + assets exposés au classpath de test |
| `src/test/…/theme/*` | 4 classes, 22 tests |

### `:app` — couche Compose

| Fichier | Rôle |
|---|---|
| `settings/AppSettings.kt` | Modèle des préférences + `LocalDisplaySettings` (nouveau) |
| `settings/SettingsStore.kt` | Persistance `SharedPreferences` (nouveau) |
| `ui/theme/ThemeManager.kt` | Assets → `ColorScheme` + `MapPalette` ; conversion au chargement, chargement par thème, rôles Material dérivés |
| `ui/theme/MapPalette.kt` | Palette de carte convertie en couleurs Compose (nouveau) |
| `ui/theme/Theme.kt` | Résolution unique, mémoïsation, `LocalThemeType` / `LocalGraphicsConfig` / `LocalMapPalette` / `LocalDisplaySettings` |
| `ui/theme/Type.kt` | `Typography` constante → `novaTypography(colorScheme, highContrast)` |
| `ui/theme/ThemeDefinition.kt` | **Supprimé** (déplacé dans `:core:domain`) |
| `audio/AudioManager.kt` | Volumes appliqués (tout se jouait à `1f`) |
| `ui/viewmodels/GameViewModel.kt` | `settings` + `updateSettings` |
| `MainActivity.kt` | Câblage réglages → thème → écran de réglages |
| `ui/screens/SettingsScreen.kt` | Sélecteur de thème, réglages branchés, application immédiate |
| `ui/screens/ThemeSelectorLabels.kt` | Options et libellés, sans Compose — donc testables sur la JVM (nouveau) |
| `ui/screens/TacticalMapScreen.kt` | `LocalMapPalette`, `planetShadowAlpha`, éclats d'explosion, contraste élevé |
| `ui/components/GraphicNoirComponents.kt` | `LocalGraphicsConfig`, flou coupé avec les effets |
| `ui/components/BackgroundEffects.kt` | Trames coupées avec les effets |
| `src/test/…/ThemeSelectorLabelsTest.kt` | 4 tests JVM (nouveau) |
| `src/androidTest/…/ThemeSelectorTest.kt` | 6 tests Compose (nouveau) |

### Racine

| Fichier | Rôle |
|---|---|
| `.github/workflows/ci.yml` | Étape `:app:testDebugUnitTest` — les tests JVM du module Android n'étaient jamais lancés |
| `THEME_GUIDE_FR.md`, `CLAUDE.md` | Documentation du système de thèmes |

---

## 6. Ce qui reste ouvert

Aucun point de cet audit n'est en attente. Ce qui suit relève de choix assumés ou de sujets voisins.

- **Les couleurs de faction ne sont pas thémées, et ne devraient pas l'être.** Ce sont des couleurs
  d'appartenance : le joueur doit reconnaître ses flottes sans réfléchir, et les faire varier avec la
  saison serait un piège de lisibilité. Elles restent dans `Color.kt`, via `getFactionColor`. Ce qui
  *serait* légitime, c'est un mode daltonien — mais c'est le constat GR7 de `AUDIT_GRAPHISMES.md`,
  pas un problème de thème.
- **`getFactionColor` vit toujours dans un fichier d'écran** (`FactionSelectionScreen.kt`) alors
  qu'elle est appelée depuis la carte. Déjà relevé en GR7 ; ce lot ne l'a pas déplacée pour ne pas
  élargir le périmètre.
- **Les palettes de carte d'HALLOWEEN et WINTER n'ont pas été vues.** Elles sont cohérentes avec les
  couleurs d'interface déjà validées de chaque thème, mais aucun rendu n'a pu être produit dans cet
  environnement (cf. la limite en tête de document). C'est le seul livrable de ce lot qui appelle une
  relecture visuelle.
- **« RESET TUTORIAL DATA » ne fait toujours rien** (`onClick = { }`). Il n'y a pas de données de
  tutoriel à effacer dans le dépôt : le bouton attend une fonctionnalité qui n'existe pas encore.
- **La bascule saisonnière n'est pas instantanée** : le thème actif est mémoïsé pour la durée de la
  composition (cf. TH6). Une application ouverte depuis la veille au soir ne passera à HALLOWEEN
  qu'à la prochaine recréation de l'activité. Contrepartie assumée d'avoir cessé de relire l'horloge
  à chaque recomposition.
- **Les tests Compose ne tournent que sur `main`.** C'est la configuration existante du job
  `android-test` (émulateur), pas un choix de ce lot. La partie testable sans appareil a été extraite
  précisément pour ne pas dépendre de cette contrainte.
