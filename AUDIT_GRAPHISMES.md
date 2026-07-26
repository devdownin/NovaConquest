# Audit — Graphismes & rendu

> Portée : rendu de la carte tactique (`TacticalMapScreen` — hexagones, sprites d'unités,
> terrains, animations), palette et système de thèmes (`Color.kt`, `ThemeManager`,
> `ThemeDefinition`, assets JSON), composants visuels partagés (`GraphicNoirComponents`,
> `BackgroundEffects`), et cohérence de la direction artistique.
>
> ## ⚠️ Limite majeure de cet audit
>
> **Je n'ai pas pu voir le rendu.** Le proxy réseau refuse `dl.google.com` (403), l'Android Gradle
> Plugin est donc inaccessible : aucun build, aucun émulateur, aucune capture d'écran dans cet
> environnement. Ce qui suit est un audit **du code qui produit les graphismes** — densité,
> performance, cohérence de palette, thématisation — et non un jugement esthétique. Un défaut
> purement visuel (composition, lisibilité réelle, impression d'ensemble) **échapperait
> entièrement** à cette analyse. C'est la limite la plus sérieuse de toute la série d'audits, et
> elle est particulièrement gênante sur ce sujet précis.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **GR1** | 🔴 **Majeur — ✅ corrigé** | Densité | La carte était dimensionnée en **pixels bruts** : elle rétrécit quand la densité d'écran monte, et les libellés devenaient illisibles |
| **GR2** | 🟠 **Moyen — ✅ corrigé** | Palette | Deux factions sur sept utilisaient `Color.Cyan` / `Color.Magenta` **bruts**, primaires saturées incompatibles avec la palette désaturée |
| **GR3** | 🟠 Moyen | Thèmes | Le système de thèmes **n'atteint quasiment pas la carte** : toute la palette de terrain est codée en dur |
| GR4 | 🟡 Faible — ✅ corrigé | Performance | `IndustrialPanel` allouait un `Calendar` **à chaque passe de dessin**, pour chaque panneau |
| GR5 | 🟠 Moyen | Performance | **Aucun culling** : les 469 tuiles d'une carte GIGANTIC sont dessinées même hors écran, avec une allocation de `Path` par hexagone |
| GR6 | 🧹 Propreté — ✅ corrigé | Mort-code | Abstraction de rendu (`UnitRenderer` / `EnvironmentRenderer`) **jamais référencée** |
| GR7 | 🟡 Faible | Accessibilité | Rouge sang et rouille sont proches ; aucun mode daltonien, et `getFactionColor` vit dans un fichier d'écran |

---

## 2. Corrigés

### GR1 — 🔴 La carte rétrécissait avec la densité d'écran  ✅ **corrigé**

`DrawScope` travaille en **pixels**, pas en `dp`. Or tout le rendu de la carte était écrit en
constantes de pixels brutes :

```kotlin
private const val HEX_RADIUS = 60f   // pixels
val size = 25f                       // sprite d'unité
textSize = 11f                       // libellé de secteur
val barWidth = 40f                   // barre de PV
```

Aucun `toPx()` dans tout le fichier. Conséquence, un hexagone de 60 px mesure :

| Densité | Écran typique | Rayon réel |
|---------|---------------|------------|
| 1x (160 dpi) | tablette d'entrée de gamme | 60 dp — énorme |
| 2x (320 dpi) | milieu de gamme | 30 dp |
| 3x (480 dpi) | téléphone courant | **20 dp** |
| 3,5x (560 dpi) | haut de gamme | **17 dp** |

La carte n'avait donc pas la même taille physique d'un appareil à l'autre — et le libellé de secteur
à 11 px tombait à **~3,7 dp** sur un écran 3x, c'est-à-dire illisible. Avec un zoom initial de 0,8×,
c'était encore pire. Le pincement (0,5–3×) permettait de compenser, mais la vue par défaut était
fausse sur la majorité des téléphones modernes.

**Correctif** — `HEX_RADIUS_DP = 30.dp`, converti une fois par `LocalDensity` en début de composable.
Cette **valeur unique** alimente à la fois le dessin **et** `pixelToHex` (à qui elle est passée en
paramètre) : la détection de touche ne peut donc pas dériver du rendu, par construction. Les
dimensions dérivées deviennent proportionnelles au rayon plutôt que fixes — sprite `× 0,42`, barre de
PV `× 0,67`, libellé `× 0,18` — soit exactement les ratios d'origine, si bien que **l'apparence sur
un écran 2x est inchangée** et que tous les autres appareils s'y alignent enfin.

### GR2 — 🟠 Deux factions hors palette  ✅ **corrigé**

La palette est délibérément désaturée — le code le répète partout (« pas de violet électrique »,
« métal mat, pas gris numérique », « fumée brune, pas néon ») :

| Faction | Couleur (avant) | |
|---------|-----------------|---|
| DOMINION | `#8B2A2A` sang séché | ✅ |
| TRADERS | `#B8960A` or fané | ✅ |
| SYNTH | `#4A7B9D` acier froid | ✅ |
| NOMADS | `#B85C2A` rouille | ✅ |
| KAELEN | `#5A7A4A` lichen | ✅ |
| **XYLAR** | **`Color.Cyan` = `#00FFFF`** | ❌ primaire pure |
| **ANCIENT_NPC** | **`Color.Magenta` = `#FF00FF`** | ❌ primaire pure |

Deux factions sur sept hurlaient donc littéralement au milieu d'une direction artistique d'encre
sourde — et ce sont des couleurs qui portent l'identité d'un camp sur toute la carte (contours de
planètes, sprites, barres de PV, pastilles d'interface).

**Correctif** — deux teintes ajoutées à la palette : `XylarPurple #7B4A9D` (améthyste sourde, pour
l'essaim) et `AncientBone #9D8A6A` (os ancien / parchemin, pour les Anciens). Elles restent
distinctes des cinq autres tout en respectant la saturation d'ensemble.

### GR4 — 🟡 Allocation d'horloge dans le chemin de dessin  ✅ **corrigé**

```kotlin
Modifier.graphicsLayer {
    val radius = ThemeManager.getGraphicsConfig(ThemeManager.getActiveTheme()).blurRadius
```

Le bloc `graphicsLayer` s'exécute à **chaque passe de dessin**, et `getActiveTheme()` instancie un
`Calendar` puis lit l'horloge système pour déterminer la période de l'année. `IndustrialPanel` étant
utilisé des dizaines de fois par écran, cela représentait autant d'allocations inutiles par frame.
Le calcul est désormais résolu **une fois par panneau** via `remember`.

> ℹ️ À noter : cet appel utilise `getActiveTheme()` **sans argument**, donc il ignore le thème
> choisi par le joueur (seul le calendrier compte) — contrairement à la carte, qui lit
> `gameState.themeConfig.currentTheme`. Incohérence signalée en GR3.

### GR6 — 🧹 Abstraction de rendu morte  ✅ **corrigé**

`RendererProvider.kt` et `VectorRenderer.kt` (46 lignes) définissaient `UnitRenderer` /
`EnvironmentRenderer` avec une signature propre (`size`, `factionColor` en paramètres) — **jamais
référencées** : la carte dessine directement via des extensions `DrawScope`. Fichiers supprimés.
L'intention était bonne (permettre des rendus interchangeables selon le thème) ; si elle est reprise,
il faudra brancher la carte dessus, pas seulement déclarer les interfaces.

---

## 3. Signalés, non modifiés

### GR3 — 🟠 Le système de thèmes n'atteint pas la carte

Le système est bien vivant : trois thèmes JSON (`default`, `halloween`, `winter`) sont chargés depuis
les assets, alimentent le `ColorScheme` Material et basculent automatiquement selon la saison. Mais
**la carte tactique — l'écran principal — ignore presque tout** :

- **toute la palette de terrain est codée en dur** dans `TacticalMapScreen` (`Color(0xFF181210)` pour
  le vide, `0xFF241C14` pour les astéroïdes, `0xFF261530` pour la nébuleuse…), de même que l'encre
  noire `0xFF130F0A` répétée dans chaque fonction de dessin ;
- sur les quatre réglages de `GraphicsConfig`, **`outlineStrokeWidth` sert à un seul trait** (le
  contour de planète), `blurRadius` au flou des panneaux, et **`planetShadowAlpha` comme
  `particleCountMultiplier` ne sont lus nulle part**.

Passer en thème Halloween ou Winter ne change donc quasiment rien à l'écran que le joueur regarde le
plus. Le chemin de correction est net : déplacer la palette de terrain dans `ThemeDefinition` (les
JSON existent déjà, il suffit d'y ajouter une section `terrain`) et faire lire la carte depuis là.
C'est un travail de contenu autant que de code — laissé ouvert.

### GR5 — 🟠 Aucun culling, et une allocation par hexagone

La boucle de terrain parcourt **toutes** les tuiles à chaque redessin :

```kotlin
gameState.map.tiles.values.forEach { tile -> … }
```

Sur une carte GIGANTIC (rayon 12 ≈ **469 tuiles**), la totalité est dessinée quel que soit le
cadrage — alors que le `graphicsLayer` (pan + zoom) n'en laisse voir qu'une fraction. Aucun test de
visibilité n'est fait.

S'y ajoute une allocation systématique : `drawHexagonPath` crée un `Path()` **à chaque appel**, et il
est appelé 2 à 6 fois par tuile — soit de l'ordre de **1 000 à 2 800 objets `Path` par redessin**.
`drawAsteroids` en crée 6 de plus par tuile d'astéroïdes.

Deux pistes, indépendantes : (a) ne dessiner que les tuiles dont le centre projeté tombe dans le
viewport élargi d'un rayon ; (b) réutiliser un `Path` unique remis à zéro (`path.reset()`) plutôt
qu'en allouer un par appel. Je ne les ai pas appliquées ici : sans pouvoir observer le rendu, une
erreur de calcul de viewport ferait disparaître des tuiles à l'écran — un risque disproportionné pour
un gain que je ne peux pas mesurer.

### GR7 — 🟡 Lisibilité et accessibilité

- **Rouge sang `#8B2A2A` (DOMINION) et rouille `#B85C2A` (NOMADS)** sont deux teintes chaudes
  voisines ; sur un sprite de quelques millimètres, la distinction peut être difficile — d'autant que
  l'orange sert *aussi* de couleur fonctionnelle (assiégeable, production, événements).
- **Aucun mode daltonien** : l'appartenance d'un camp repose uniquement sur la teinte, sans forme ni
  motif distinctif. Les sprites diffèrent par type d'unité, pas par faction.
- `getFactionColor` est définie dans `FactionSelectionScreen.kt` alors qu'elle est utilisée par la
  carte, l'académie et l'écran de fin : sa place est dans `ui/theme`.

## 4. Ce qui fonctionne bien

- **`BackgroundEffects` est exemplaire** : `HalftoneBackground` et `NoiseOverlay` pré-calculent un
  bitmap **une fois par taille de canvas** puis le blittent en un seul `drawImage`, avec un `Random`
  graine fixe pour un grain stable. Le commentaire documente même le coût de l'approche naïve qu'ils
  remplacent (~10-15 ms de blocage au démarrage). C'est exactement le bon réflexe.
- **Séparation des couches de dessin** : la carte sépare le canvas de terrain (redessiné sur
  changement d'état) du canvas d'animation (scanline, halos, trajectoire, combat) — les animations ne
  forcent donc pas le redessin du terrain.
- **Direction artistique cohérente et documentée** : palette désaturée « encre / béton / rouille »,
  contours épais, hachures, reflets blancs — l'intention est explicite dans les commentaires et
  suivie par la quasi-totalité du code de dessin.
- **Tout est vectoriel** : aucun asset bitmap, donc pas de problème de résolution ni de poids d'APK ;
  les sept types d'unités ont chacun une silhouette distincte dessinée à la main.
- **Thèmes pilotés par les données** : les couleurs vivent dans des JSON d'assets, avec repli sur un
  schéma codé en dur si le chargement échoue — le jeu ne peut pas se retrouver sans palette.
