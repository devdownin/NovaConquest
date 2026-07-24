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
| O1 | ⚪ Optim — ✅ fait | Génération | `ensureConnectivity` relançait un BFS complet après **chaque** corridor → désormais incrémental |
| O2 | ⚪ Optim — ✅ fait | Rendu | Overlays de sélection déplacés hors de la couche terrain (plus de redraw terrain au tap) |
| A1 | 💡 Amélio — ✅ fait | Contenu | Trous de ver : `radius/4` paires (1‑3) au lieu d'une seule |
| A2 | 💡 Amélio — ✅ fait | Contenu | `PLASMA_CLOUD` / `ION_STORM` / `ANOMALY` désormais générés ; tooltips honnêtes |
| A3 | 💡 Amélio — ✅ fait | Contenu | 2 nouveaux archétypes : `NEBULA_EXPANSE`, `ASTEROID_BELT` |
| A4 | 💡 Amélio — ✅ fait | Campagne | `CampaignMission.mapSize` (défaut MEDIUM), utilisé au lancement |
| A5 | 💡 Amélio — ✅ fait | Rejouabilité | Graine stockée dans `GameMap.seed` et affichée (coin bas‑droit) |
| A6 | 💡 Amélio — ✅ fait | UX | Retour haptique léger quand on tape une case dans le brouillard |

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

### O1 — BFS complet répété dans `ensureConnectivity`  ✅ **fait**

Avant : pour chaque cible non atteinte, on carvait un corridor **puis** on relançait
`reachablePassable(hub)` (BFS complet, O(cellules)) → O(cibles × cellules) sur GIGANTIC.

**Correctif** — le set `reachable` est désormais maintenu **incrémentalement** :
`carveLine` renvoie les coords du corridor, et `expandReachable` poursuit un BFS **depuis ces
seules cellules**. Comme chaque corridor rejoint le hub, il est contigu à la région déjà
atteignable ; on n'ajoute que les hex nouvellement connectés au lieu de re-scanner toute la
région. Résultat identique (validé par `MapFactoryTest.everyMapIsFullyConnected…`), coût
amorti O(cellules) au total.

### O2 — La couche terrain se redessinait à chaque sélection  ✅ **fait**

Avant : le `Canvas` de terrain lisait `selectedHex`, `reachableHexes`, `attackRangeHexes`,
`capturableCoords`, etc. → toute sélection invalidait **l'intégralité** du parcours de tuiles.

**Correctif** — tous les overlays de sélection (portée déplacement/attaque, cibles,
capture/siège, contour du sélectionné) sont déplacés dans la seconde couche `Canvas` (celle
des animations), en itérant directement les **sets de coords** (moins de travail qu'un parcours
de toutes les tuiles). La couche terrain de base ne dépend plus de la sélection → aucun redraw
terrain au tap. Le filtre brouillard (`coord in exploredHexes`) est conservé pour un rendu
identique.

---

## 4. Améliorations de contenu / rejouabilité

- **A1 — Trous de ver** ✅ : `placeWormholes` pose `(radius/4).coerceIn(1,3)` paires
  point‑symétriques (au lieu d'une seule) — plus d'options de saut sur grandes cartes. Ne fait
  qu'ajouter de la traversabilité, donc la connectivité reste garantie.
- **A2 — Terrains morts** ✅ : `PLASMA_CLOUD`, `ION_STORM`, `ANOMALY` sont désormais générés
  via `terrainWeights(archetype)` **et dotés d'effets réels** (voir §5). Tous passables →
  connectivité intacte.
- **A3 — Archétypes** ✅ : ajout de `NEBULA_EXPANSE` (vision‑lourd) et `ASTEROID_BELT`
  (astéroïdes denses). Le sélecteur `FactionSelectionScreen` les affiche automatiquement ;
  `VictoryChecker` ne réserve son cas spécial qu'à ZODIAC, les autres suivent la victoire
  standard. Couverts par `MapFactoryTest.newArchetypesAreFullyConnected`.
- **A4 — Taille de campagne** ✅ : `CampaignMission.mapSize` (défaut MEDIUM) ; `GameViewModel`
  la transmet au lieu de forcer MEDIUM.
- **A5 — Graine reproductible** ✅ : `GameMap.seed` (défaut `0L`, compat saves) stocke la
  graine ; affichée discrètement en bas‑droite de la carte tactique.
- **A6 — Feedback** ✅ : un tap sur une case du brouillard déclenche un retour haptique léger
  au lieu d'être silencieusement ignoré.

---

## 5. Effets de terrain implémentés

Les terrains « décoratifs » ont désormais des mécaniques réelles, appliquées au bon endroit et
alignées sur leurs tooltips :

| Terrain | Effet(s) | Où |
|---------|----------|----|
| `BLACK_HOLE` | -3 PV/fin de tour (peut détruire) + -25 % attaque | `TurnManager`, `CombatResolver` |
| `NEBULA` | Bloque la vision (traversée libre) | `VisionSystem` |
| `PLASMA_CLOUD` | Bloque la vision + **coût de déplacement x2** | `VisionSystem`, `GameGridMap.enterCost` |
| `ION_STORM` (terrain) | Bloque la vision + **coût de déplacement x2** | `VisionSystem`, `GameGridMap.enterCost` |
| `ANOMALY` | **Impulsion aléatoire** de fin de tour (±2 PV ou rien) | `TurnManager` |

**Coût de déplacement par terrain** : l'interface `GridMap` gagne `enterCost(coord): Int`
(défaut 1) ; `HexPathfinder.findPath`/`findReachable` la consomment ; `GameGridMap` renvoie 2
pour plasma/ion. Comme tout le pathfinding (moteur, surbrillance, glisser, IA) passe par
`GameGridMap`, le surcoût est cohérent partout. L'heuristique de destination de l'IA
(`stepToward`) dépense aussi les points par hex, donc elle ne propose plus de trajets que le
moteur refuserait. En terrain normal (coût 1) le comportement est strictement inchangé.

**Tests** : `HexPathfinderTest` (coût d'entrée sur `findPath` et `findReachable`),
`GameGridMapTest` (plasma/ion = 2, nébuleuse = 1), `TurnManagerTest.anomalyProducesVariableOutcomes`.

---

## 6. Ce qui fonctionne bien

- **Connectivité garantie** : `ensureConnectivity` + `carveLine` assurent qu'un vaisseau peut
  toujours atteindre chaque spawn et chaque planète, couvert par `MapFactoryTest` sur un large
  éventail de graines/tailles.
- **Spawns symétriques** : `spawnPointsFor(radius)` donne 6 systèmes de départ équilibrés,
  correctement consommés par `createInitialState` (un par faction jouable).
- **Séparation propre** : `GameGridMap` isole le pathfinder des modèles de domaine ; ajouter
  un terrain impassable ou une règle de blocage se fait au bon endroit.
- **Tirage procédural indépendant** : un seul `nextDouble()` par case pour choisir le terrain
  (buckets indépendants) — correction déjà notée en commentaire.
