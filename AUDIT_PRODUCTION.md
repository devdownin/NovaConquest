# Audit — Production & file de construction

> Portée : `handleBuildUnit` / `handleCancelBuild`, la progression des ordres et l'apparition des
> unités dans `TurnManager`, `buildTurns`, la production IA (`UtilityEvaluator.evaluateProduction`)
> et l'écran `StarSystemManagementScreen` (chantier + infrastructure).
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Le correctif moteur est couvert par la CI ; les correctifs d'interface sont revus statiquement.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **P1** | 🟠 **Moyen — ✅ corrigé** | Validation | `handleBuildUnit` ne vérifiait **ni la propriété ni même que la cible soit une planète** : on pouvait produire sur un monde ennemi, et y faire apparaître le vaisseau |
| **P2** | 🔴 **Majeur — ✅ corrigé** | Contenu | Seuls **3 des 7 types d'unités** étaient proposés au joueur : porte-vaisseaux, cuirassé, dreadnought et plateforme de défense étaient **inconstructibles** |
| P3 | 🟡 Faible — ✅ corrigé | UX | Boutons `PRODUCE` et `UPGRADE` cliquables sans crédits → erreur moteur au lieu d'un état désactivé |
| P4 | 💡 Design — ✅ tranché (voir AUDIT_DECISIONS_GAMEPLAY.md) | Équilibrage | Durée de construction quasi **plate** : un éclaireur (3 C) prend autant de tours qu'un cuirassé (18 C) |
| P5 | 🟡 Faible | Production | Un ordre dont la zone d'apparition reste bloquée se **réarme indéfiniment** à 1 tour restant |
| P6 | 💡 Design — ✅ tranché (voir AUDIT_DECISIONS_GAMEPLAY.md) | IA | La production IA suit un ordre fixe « le plus cher d'abord », sans tenir compte de sa posture |

---

## 2. Bugs corrigés

### P1 — 🟠 Production possible sur une planète ennemie  ✅ **corrigé**

```kotlin
val planetCoord = intent.location ?: playerState.capitalCoord ?: return …
IntentValidator.canAfford(playerState, intent.unitType.cost)?.let { return … }
if (playerState.buildQueue.any { it.planetCoord == planetCoord }) return …
```

Aucune vérification que `planetCoord` désigne **une planète**, ni qu'elle **vous appartient**. Le
réducteur étant l'autorité, un `BuildUnit` portant n'importe quelle coordonnée était accepté — et
`TurnManager` fait ensuite apparaître l'unité terminée *sur cette case ou à côté* :

```kotlin
val candidates = listOf(order.planetCoord) + gridMap.getNeighbors(order.planetCoord)
val spawnHex = candidates.firstOrNull { … }
```

…c'est-à-dire **au cœur du territoire adverse**. L'incohérence était d'autant plus nette que
`handleUpgradeSystem` vérifie déjà la propriété (« You don't own this planet. »).

En pratique l'interface n'ouvre la gestion que pour une planète possédée, mais elle ne constitue
pas une protection : elle reste ouverte si la planète est capturée pendant la consultation, et le
moteur doit de toute façon défendre ses propres invariants.

**Correctif** — `handleBuildUnit` exige désormais une planète (`IntentValidator.isPlanet`) contrôlée
par la faction active.
**Tests** : `buildUnitRejectedOnAPlanetYouDoNotOwn`, `buildUnitRejectedOnEmptySpace`,
`buildUnitAcceptedOnAPlanetYouOwn`.

### P2 — 🔴 Quatre unités sur sept étaient inconstructibles  ✅ **corrigé**

`ShipyardPanel` listait **trois** plans en dur — Scout, Fighter, Cruiser. Les quatre autres types
existent pourtant, sont entièrement implémentés… et étaient **hors d'atteinte du joueur** :

| Unité | Implémentée | Constructible par le joueur (avant) |
|-------|-------------|-------------------------------------|
| `CARRIER` | transport de 2 unités, boutons LOAD/DEPLOY sur la carte | ❌ |
| `BATTLESHIP` | bonus de siège ×2, portée 2 | ❌ |
| `DREADNOUGHT` | bonus de siège ×2, portée 3, 60 PV | ❌ |
| `DEFENSE_PLATFORM` | tourelle statique, 40 PV, portée 2 | ❌ |

Conséquence la plus visible : toute la mécanique de **transport par porte-vaisseaux** — y compris
les commandes LOAD/DEPLOY présentes dans `TacticalMapScreen` et l'exploit de réparation corrigé en
PR #60 — était **inaccessible au joueur humain**, alors que l'IA, elle, produit ces unités
(`UtilityEvaluator.evaluateProduction` parcourt tous les types). Le joueur affrontait donc des
dreadnoughts qu'il ne pouvait pas construire lui-même.

**Correctif** — la liste est désormais **pilotée par `UnitType.values()`** (triée par coût) : tout
type ajouté à l'enum apparaît automatiquement, sans risque de re-divergence. Les étiquettes
décoratives « LVL 1 / 2 / 4 », qui ne correspondaient à aucune mécanique (aucun niveau de système
n'est requis pour produire), sont remplacées par les statistiques réelles — ATQ, PV, portée,
mouvement — et l'entretien par tour.

### P3 — 🟡 Boutons actifs pour une action impossible  ✅ **corrigé**

Comme pour la recherche (T4) et le recrutement (H3) : `PRODUCE` et `UPGRADE SYSTEM` restaient
cliquables faute de crédits (seule la couleur changeait), et le clic partait jusqu'au moteur pour
être rejeté. Les deux passent désormais `enabled` au bouton.

---

## 3. Signalés, non modifiés

- **P4 — Durée de construction plate.** `buildTurns` renvoie **2 tours pour six types sur sept**
  (seul le dreadnought en demande 3). Un éclaireur à 3 C sort donc aussi vite qu'un cuirassé à
  18 C ou qu'un porte-vaisseaux à 25 C : le coût seul différencie les unités, et le temps ne joue
  aucun rôle stratégique. Même forme que T3 (coût des technologies, corrigé par un barème par
  palier) — une durée indexée sur le coût ou la classe rendrait les grosses unités réellement
  engageantes. *Changement d'équilibrage : laissé à votre décision.*
- **P5 — Ordre bloqué en boucle.** Si toutes les cases candidates sont occupées à l'échéance,
  l'ordre est réinséré avec `turnsRemaining = 1` et retentera à chaque tour, indéfiniment. Les
  crédits sont déjà dépensés ; le joueur peut annuler (50 % remboursés), mais rien ne l'avertit
  que la production est bloquée. Une notification serait utile.
- **P6 — Production IA rigide.** `evaluateProduction` prend toujours l'unité **la plus chère
  abordable** (dreadnought → porte-vaisseaux → cuirassé → …) pour chaque planète, sans considérer
  sa posture ni la composition de sa flotte — contrairement à ce qui a été fait pour la recherche
  (T6) et le recrutement de héros (H5). Elle dépense ainsi tout son trésor en gros vaisseaux même
  en paix.

## 4. Ce qui fonctionne bien

- **Un ordre par planète** : `buildQueue.any { it.planetCoord == planetCoord }` empêche d'empiler
  plusieurs constructions sur le même monde, et l'écran affiche l'ordre en cours avec les tours
  restants.
- **Repli d'apparition** : l'unité terminée apparaît sur la planète ou, à défaut, sur une case
  voisine libre et franchissable (via `GameGridMap`) — jamais sur une case occupée ou impassable.
- **Annulation remboursée** : `handleCancelBuild` rend 50 % du coût, symétriquement à l'annulation
  de recherche (T5).
- **Accélérateurs cumulés** : mondes-forges (`FORGE_WORLD`, ×2 sur place) et bonus
  `PRODUCTION_SPEED` (XYLAR) s'additionnent proprement dans le décompte des tours.
- **Vision recalculée** : les unités nouvellement produites sont prises en compte, `reduce(EndTurn)`
  appelant `updateVision` après `advanceTurn`.
