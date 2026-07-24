# Audit — Gestion des cartes (création, sélection, interaction)

> Portée : génération de carte (`MapFactory`), modèle de données (`GameMap`, `HexTile`,
> `TerrainType`, `MapSize`, `MapArchetype`), initialisation de partie
> (`GameEngine.createInitialState`), adaptateur de pathfinding (`GameGridMap`), et
> l'écran tactique / interaction utilisateur (`TacticalMapScreen`).
>
> ⚠️ **Tests non exécutés dans cet environnement** : le proxy réseau refuse
> `dl.google.com` (403), donc l'Android Gradle Plugin `9.2.1` et Google Maven sont
> inaccessibles — le build échoue avant compilation. Les constats reposent sur l'analyse
> statique. Le correctif B1 ci‑dessous est couvert par deux nouveaux tests JVM
> (`InitVerificationTest`) qui tourneront en CI.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **B1** | 🔴 **Majeur — ✅ corrigé** | Génération | Graine fixe : **toutes les parties génèrent la même carte** (`seed` par défaut = 42 jamais surchargé) |
| B2 | 🟠 Moyen | Cohérence UX | Trou noir : le tooltip promet des « dégâts aux vaisseaux » **jamais infligés** ; la case est traversable et sûre |
| B3 | 🟠 Moyen | Cohérence UX | Trois calculs divergents de portée de déplacement (moteur ≠ surbrillance ≠ glisser) → prévisualisations trompeuses |
| B4 | 🟡 Faible | Cohérence | Le tracé du glisser ignore la **navigation par trou de ver** (surbrillée mais non routée) |
| B5 | 🟡 Faible | Mort-code | `centerRequest` transporte un niveau de zoom **ignoré** |
| O1 | ⚪ Optim | Génération | `ensureConnectivity` relance un BFS complet après **chaque** corridor carvé |
| O2 | ⚪ Optim | Rendu | La couche terrain se **redessine intégralement à chaque sélection** de tuile |
| A1 | 💡 Amélio | Contenu | Une **seule paire** de trous de ver, quelle que soit la taille |
| A2 | 💡 Amélio | Contenu | `PLASMA_CLOUD`, `ION_STORM`, `ANOMALY` : terrains **jamais générés** (rendu + tooltip morts) |
| A3 | 💡 Amélio | Contenu | Seulement 2 archétypes ; le sélecteur UI est déjà en place |
| A4 | 💡 Amélio | Campagne | Taille de carte **forcée à MEDIUM** pour toute campagne |
| A5 | 💡 Amélio | Rejouabilité | Graine ni stockée ni affichée (pas de « rejouer/partager cette carte ») |

---

## 2. Bugs

### B1 — 🔴 Graine fixe : toutes les parties sont identiques  ✅ **corrigé**

`GameEngine.createInitialState` appelait la fabrique **sans passer de graine** :

```kotlin
// GameEngine.kt (avant)
val map = MapFactory.generateMap(radius = mapSize.radius, archetype = archetype)
```

Or `generateMap(radius, archetype, seed: Long = 42)` (`MapFactory.kt:23`) retombe alors sur
sa graine par défaut **42**. Conséquence : *chaque* nouvelle partie — STANDARD comme
ZODIAC, quelle que soit la taille — produit exactement la **même** galaxie (mêmes planètes,
astéroïdes, nébuleuses, wormholes, spécialités). La rejouabilité d'un 4X est nulle, et la
logique procédurale (`random.nextDouble()`, tirage des spécialités) ne sert à rien.

**Correctif appliqué** — tirer une graine depuis le RNG injecté :

```kotlin
// GameEngine.kt (après)
val map = MapFactory.generateMap(
    radius = mapSize.radius, archetype = archetype, seed = deps.rng.nextLong())
```

`deps.rng` est déjà l'injection de hasard testable du moteur (`GameEngineDependencies.rng`,
`Random.Default` en prod). Bénéfices :
- en production, chaque partie diffère ;
- en test, un `Random` déterministe rend la génération **reproductible** (mêmes graines →
  mêmes cartes).

**Tests ajoutés** (`InitVerificationTest`) :
- `newGamesProduceDifferentMaps` — deux moteurs seedés différemment ⇒ terrains différents ;
- `mapGenerationIsDeterministicForSameSeed` — deux moteurs seedés à l'identique ⇒ cartes
  identiques.

La connectivité reste garantie : `MapFactoryTest` valide déjà les graines `0..49` (STANDARD)
et `0..24` (ZODIAC) sur les rayons 3/5/8/12.

### B2 — 🟠 Trou noir : effet promis mais absent

Le tooltip (`TacticalMapScreen.kt:1611`) annonce *« Trou noir. Danger extrême — les vaisseaux
subissent des dommages »*, mais **aucun code n'inflige de dégâts** de trou noir. La seule
mécanique `BLACK_HOLE` réellement implémentée est un malus **-25 % d'attaque** en combat
(`CombatResolver.kt:29,55`). De plus `BLACK_HOLE` a `isPassable = true` (défaut de
`TerrainType`), donc une unité peut stationner indéfiniment sur le trou noir central `(0,0,0)`
sans le moindre risque — l'inverse de « danger extrême ».

**Recommandation** — choisir une direction et l'appliquer des deux côtés :
- *soit* implémenter des dégâts de fin de tour aux unités sur `BLACK_HOLE` (bloc de fin de
  tour de `GameEngine.reduce`, à la manière des soins de héros) ;
- *soit* rendre la case impassable et/ou corriger le texte pour ne décrire que le malus
  d'attaque. Attention si on la rend impassable : le centre `(0,0,0)` devient un obstacle et
  la logique de connectivité (`ensureConnectivity`, qui ne carve que les astéroïdes) doit en
  tenir compte.

### B3 — 🟠 Trois calculs divergents de portée de déplacement

| Source | Formule | Prend en compte |
|--------|---------|-----------------|
| **Moteur** (autoritaire, `IntentHandlers.kt:54‑55`) | `movement + BonusRegistry.sum(MOVEMENT_MODIFIER)` | faction **+ tech + héros + événement** (ex. ION_STORM −1) |
| Surbrillance `reachableHexes` (`TacticalMapScreen.kt:162`) | `movement + faction.bonusMovement − ionPenalty` | faction + ION_STORM, **pas** tech/héros |
| Tracé du glisser (`TacticalMapScreen.kt:438`) | `movement + faction.bonusMovement` | faction seule, **ni** tech/héros **ni** ION_STORM |

Conséquences visibles : pendant une tempête ionique, le glisser peut proposer une
destination que le moteur **refusera** ensuite (« Target position is unreachable or too
far »), et la surbrillance cyan ne coïncide pas avec le tracé du glisser.

**Recommandation** — exposer un helper unique de « mouvement effectif » (idéalement dans
`:core:engine`, réutilisé par l'UI) et l'appeler aux trois endroits, pour que
surbrillance = prévisualisation = résolution.

### B4 — 🟡 Le glisser ignore la navigation par trou de ver

`reachableHexes` construit `GameGridMap(gameState, gameState.activeFaction)` — qui active les
arêtes wormhole si `tech_wormhole_nav` est débloquée (`GameGridMap.kt:11‑27`). Mais le tracé
du glisser construit `GameGridMap(gs)` **sans faction** (`TacticalMapScreen.kt:433`). Un
joueur ayant la nav wormhole voit donc ses hex accessibles *via* wormhole surlignés, mais le
tracé du glisser ne route jamais par le trou de ver. Passer `gs.activeFaction` au
`GameGridMap` du glisser suffit.

### B5 — 🟡 `centerRequest` : niveau de zoom mort

`centerRequest: Pair<HexCoord, Int>` — le second champ (zoom) est ignoré via `(coord, _)`
(`TacticalMapScreen.kt:255`). Le « SMART FOCUS » recentre mais ne peut pas ajuster l'échelle :
soit exploiter la valeur (adapter `scale`), soit retirer le champ pour lever l'ambiguïté.

---

## 3. Optimisations

### O1 — BFS complet répété dans `ensureConnectivity`

`MapFactory.kt:151‑156` : pour chaque cible non atteinte, on carve un corridor **puis** on
relance `reachablePassable(hub)` (BFS complet, O(cellules)). Sur GIGANTIC (rayon 12 ≈ 469 hex,
nombreuses planètes), c'est O(cibles × cellules). Coût unique à la génération, donc non
bloquant, mais on peut :
- ne relancer le BFS **qu'en incrémental** depuis les cellules nouvellement carvées, ou
- carver toutes les cibles restantes puis faire **un seul** BFS de contrôle.

### O2 — La couche terrain se redessine à chaque sélection

Le `Canvas` de terrain (`TacticalMapScreen.kt:475‑600`) lit `selectedHex`, `reachableHexes`,
`attackRangeHexes`, `capturableCoords`, etc. Toute sélection invalide donc **l'intégralité**
du parcours de tuiles (fills + strokes + texte natif par hex). Le fichier sépare déjà une
seconde couche `Canvas` pour les animations ; y **déplacer les overlays de sélection**
(portée de déplacement/attaque, cibles, contour du sélectionné) éviterait de redessiner la
base terrain à chaque tap — gain net de fluidité sur les grandes cartes.

---

## 4. Améliorations de contenu / rejouabilité

- **A1 — Trous de ver** : une seule paire posée aux positions mi‑anneau (`MapFactory.kt:90‑102`).
  La nav wormhole n'offre donc jamais plus d'un saut. Générer un nombre de paires
  proportionnel au rayon.
- **A2 — Terrains morts** : `PLASMA_CLOUD`, `ION_STORM`, `ANOMALY` existent dans l'enum, ont
  un rendu dédié (`drawPlasmaCloud`/`drawIonStorm`/`drawAnomaly`) et un tooltip, mais **ne
  sont jamais générés** — le tirage procédural ne produit que EMPTY/PLANET/ASTEROIDS/NEBULA
  (+ BLACK_HOLE/WORMHOLE scriptés). Les brancher dans le tirage (idéalement pondéré par
  archétype) débloque du contenu déjà dessiné.
- **A3 — Archétypes** : seulement STANDARD et ZODIAC. Le sélecteur UI
  (`FactionSelectionScreen`) itère déjà `MapArchetype.values()` : ajouter des archétypes
  (anneau, spirale, amas) est peu coûteux.
- **A4 — Taille de campagne** : `GameViewModel.startCampaign` force `MapSize.MEDIUM` ; laisser
  chaque mission déclarer sa taille via `CampaignConfig`.
- **A5 — Graine reproductible** : une fois B1 en place, stocker la graine dans `GameState`
  (avec valeur par défaut pour ne pas casser les saves) et l'afficher permettrait de
  rejouer/partager une carte. Les tuiles étant déjà sérialisées, la sauvegarde fonctionne
  indépendamment.
- **A6 — Feedback** : un tap sur un hex non exploré est silencieusement ignoré
  (`TacticalMapScreen.kt:321`) — un léger retour (son/haptique) clarifierait l'action.

---

## 5. Ce qui fonctionne bien

- **Connectivité garantie** : `ensureConnectivity` + `carveLine` assurent qu'un vaisseau peut
  toujours atteindre chaque spawn et chaque planète, couvert par `MapFactoryTest` sur un large
  éventail de graines/tailles.
- **Spawns symétriques** : `spawnPointsFor(radius)` donne 6 systèmes de départ équilibrés,
  correctement consommés par `createInitialState` (un par faction jouable).
- **Séparation propre** : `GameGridMap` isole le pathfinder des modèles de domaine ; ajouter
  un terrain impassable ou une règle de blocage se fait au bon endroit.
- **Tirage procédural indépendant** : un seul `nextDouble()` par case pour choisir le terrain
  (buckets indépendants) — correction déjà notée en commentaire.
