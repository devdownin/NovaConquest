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
| B2 | 🟠 Moyen — ✅ corrigé | Cohérence UX | Trou noir : le tooltip promet des « dégâts aux vaisseaux » **jamais infligés** ; la case est traversable et sûre |
| B3 | 🟠 Moyen — ✅ corrigé | Cohérence UX | Trois calculs divergents de portée de déplacement (moteur ≠ surbrillance ≠ glisser) → prévisualisations trompeuses |
| B4 | 🟡 Faible — ✅ corrigé | Cohérence | Le tracé du glisser ignore la **navigation par trou de ver** (surbrillée mais non routée) |
| B5 | 🟡 Faible — ✅ clarifié | (faux positif) | `centerRequest` : le second champ n'est **pas** un zoom mais un nonce de re-déclenchement ; audit initial erroné |
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

### B2 — 🟠 Trou noir : effet promis mais absent  ✅ **corrigé**

Le tooltip (`TacticalMapScreen.kt:1611`) annonçait *« Trou noir. Danger extrême — les
vaisseaux subissent des dommages »*, mais **aucun code n'infligeait de dégâts**. La seule
mécanique `BLACK_HOLE` implémentée était un malus **-25 % d'attaque** en combat
(`CombatResolver.kt:29,55`). Une unité pouvait stationner indéfiniment sur le trou noir
central `(0,0,0)` sans le moindre risque — l'inverse de « danger extrême ».

**Correctif appliqué** — dégâts de fin de tour dans `TurnManager.advanceTurn` : une unité de
la faction qui **termine** son tour et qui stationne sur un `BLACK_HOLE` perd
`TurnManager.BLACK_HOLE_DAMAGE = 3` PV ; à 0 PV elle est retirée. Le calcul suit le même
motif que les soins du héros Nix (ne touche que les unités de la faction active), et la
vision est recalculée en aval par `reduce(EndTurn)`. Le tooltip est mis à jour pour décrire
l'effet réel (-3 PV/tour + -25 % attaque). La case reste traversable (choix volontaire :
raccourci risqué plutôt qu'obstacle, pour ne pas perturber `ensureConnectivity`).

**Tests** (`TurnManagerTest`) : `blackHoleDamagesUnitOfFactionEndingTurn`,
`blackHoleDestroysUnitAtLowHp`, `blackHoleSparesUnitsOfOtherFactions`.

### B3 — 🟠 Trois calculs divergents de portée de déplacement  ✅ **corrigé**

Trois formules coexistaient :

| Source | Formule (avant) | Prenait en compte |
|--------|-----------------|-------------------|
| **Moteur** (autoritaire, `IntentHandlers.kt`) | `movement + BonusRegistry.sum(MOVEMENT_MODIFIER)` | faction **+ tech + héros + événement** (ION_STORM −1) |
| Surbrillance `reachableHexes` | `movement + faction.bonusMovement − ionPenalty` | faction + ION_STORM, **pas** tech/héros |
| Tracé du glisser | `movement + faction.bonusMovement` | faction seule, **ni** tech/héros **ni** ION_STORM |

Conséquence : en tempête ionique, le glisser proposait une destination que le moteur
**refusait** ensuite, et la surbrillance cyan ne coïncidait pas avec le tracé du glisser.

**Correctif appliqué** — nouveau `MovementCalculator.effectiveMovement(state, unit)` dans
`:core:engine`, source unique de vérité (faction + tech + héros + événement, plancher à 1).
Il est appelé par le moteur (`handleMoveUnit`), la surbrillance (`reachableHexes`) et le tracé
du glisser. Surbrillance = prévisualisation = résolution.

**Tests** (`MovementCalculatorTest`) : base, bonus de faction, pénalité ION_STORM, plancher à 1.

### B4 — 🟡 Le glisser ignore la navigation par trou de ver  ✅ **corrigé**

`reachableHexes` construisait `GameGridMap(gameState, gameState.activeFaction)` — qui active
les arêtes wormhole si `tech_wormhole_nav` est débloquée (`GameGridMap.kt:11‑27`). Mais le
tracé du glisser construisait `GameGridMap(gs)` **sans faction**. Un joueur ayant la nav
wormhole voyait ses hex accessibles *via* wormhole surlignés, sans que le glisser n'y route.
**Correctif** : le glisser passe désormais `gs.activeFaction` au `GameGridMap`.

### B5 — 🟡 `centerRequest` : faux positif (pas un zoom)  ✅ **clarifié**

Réexamen : le second champ de `centerRequest: Pair<HexCoord, Int>` n'est **pas** un niveau de
zoom mais un **compteur monotone** (`centerRequestCounter`, `MainActivity.kt:285`) servant de
*nonce* : incrémenté à chaque « SMART FOCUS », il change la `Pair` pour que
`LaunchedEffect(centerRequest)` se re-déclenche même en recentrant sur la même coordonnée. Le
destructuring `(coord, _)` est donc **correct** — le champ n'est pas mort, il agit via la clé
du `LaunchedEffect`. L'audit initial était erroné. **Action** : commentaires ajoutés aux deux
sites pour éviter la confusion ; aucun changement de comportement.

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
