# Audit — Combat / unités & conditions de victoire

> Portée : `CombatResolver` (attaque, riposte, siège, capture), `UnitType` / `GameUnit`,
> `MovementCalculator`, le transport par porte-vaisseaux (`handleLoadUnit` / `handleDeployUnit`),
> et `VictoryChecker` (six conditions de victoire + objectifs de campagne).
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Tout le code concerné est en `:core:engine` / `:core:domain`, donc **entièrement couvert par la CI**.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **C1** | 🟠 **Moyen — ✅ corrigé** | Mouvement | La plateforme de défense (`movement = 0`) **pouvait se déplacer d'une case par tour** : le plancher « jamais moins de 1 » lui offrait un hex gratuit |
| **C2** | 🟠 **Moyen — ✅ corrigé** | Exploit | Embarquer une unité ne mémorisait que son **type** : la redéployer la ramenait à **pleins PV** — le porte-vaisseaux servait d'atelier de réparation gratuit |
| **V1** | 🟠 **Moyen — ✅ corrigé** | Victoire | La victoire technologique **comptait** les entrées au lieu de vérifier les identifiants : n'importe quel jeu de la bonne taille déclenchait la victoire |
| C3 | 🟡 Design | Combat | Attaquer une **unité** consomme le déplacement, assiéger une **planète** non — asymétrie non documentée |
| C4 | 🟡 Design | Transport | L'embarquement est une action **gratuite** : ni l'escorte ni le porte-vaisseaux ne sont marqués comme ayant agi |
| V2 | 🟡 Faible | Victoire | Annihilation mutuelle (0 survivant) → **aucun vainqueur**, la partie ne se termine jamais |
| V3 | 💡 Design | Victoire | La victoire aux points (tour 100) ne regarde que les **crédits**, en ignorant planètes et flotte |
| V4 | 🟡 Faible | Victoire | La victoire territoriale Zodiac balaie `Faction.values()`, **`ANCIENT_NPC` compris** |

---

## 2. Bugs corrigés

### C1 — 🟠 La plateforme de défense pouvait se déplacer  ✅ **corrigé**

```kotlin
return (unit.type.movement + moveMod).coerceAtLeast(1)
```

`DEFENSE_PLATFORM` est définie avec `movement = 0` : c'est une tourelle statique (40 PV, portée 2,
coût 15). Mais le plancher `coerceAtLeast(1)` s'appliquait **à toutes** les unités — la plateforme
recevait donc **1 point de mouvement par tour** et pouvait traverser la carte, case par case.

Le plancher a une raison d'être légitime : empêcher qu'un malus (`ION_STORM` −1) ne cloue une unité
qui sait normalement bouger, typiquement le `DREADNOUGHT` (mouvement 1). Il ne devait simplement
jamais s'appliquer à une unité dont le mouvement de base est nul.

**Correctif** — sortie anticipée à `0` quand `unit.type.movement <= 0`, avant tout modificateur.

> 🔍 **Mea culpa** : ce plancher trop large venait de `MovementCalculator`, que j'ai introduit lors
> de l'audit des cartes (B3) — et le test que j'avais écrit alors, `movementNeverDropsBelowOne`,
> **figeait explicitement ce comportement erroné** en affirmant qu'une `DEFENSE_PLATFORM` valait 1.
> Le test est corrigé : il porte désormais sur le `DREADNOUGHT` (le cas que le plancher doit
> réellement protéger), et `immobileStructureStaysImmobile` verrouille le bon comportement.

**Tests** : `mobileUnitNeverDropsBelowOne`, `immobileStructureStaysImmobile`.

### C2 — 🟠 Le porte-vaisseaux réparait gratuitement  ✅ **corrigé**

`GameUnit.cargo` était un `List<UnitType>` : embarquer une unité **jetait ses points de vie**.

```kotlin
newUnits[carrierCoord] = carrier.copy(cargo = carrier.cargo + unit.type)   // HP perdus
…
val newUnit = GameUnit(type = deployedType, …, currentHp = deployedType.maxHp)  // plein PV
```

Exploit : un chasseur à 1 PV embarque puis se redéploie **à 12 PV**. Toute flotte accompagnée d'un
porte-vaisseaux se soignait indéfiniment, sans coût ni technologie — court-circuitant au passage
l'intérêt du héros Nix (soin) et de la tech `tech_nano_armor`.

**Correctif** — nouveau champ `GameUnit.cargoHp: List<Int> = emptyList()`, parallèle à `cargo` :
l'embarquement enregistre les PV courants, le déploiement les restitue. Le champ est **défaillant
par défaut** (liste vide) plutôt que fusionné dans `cargo`, afin que les sauvegardes existantes —
qui stockent le fret comme de simples noms de type — continuent de se décoder ; une entrée héritée
sans PV enregistrés se déploie alors à pleine santé, comme avant.

**Test** : `deployedUnitKeepsTheHpItHadWhenLoaded`.

### V1 — 🟠 Victoire technologique obtenue avec n'importe quoi  ✅ **corrigé**

```kotlin
state.playerStates.values.find { it.techUnlocked.size >= TechRegistry.ALL_TECHS.size }
```

La condition ne regardait que la **taille** de l'ensemble, jamais son contenu. Le test existant en
faisait d'ailleurs involontairement la démonstration : il gagnait la partie avec douze chaînes de
remplissage (`"t1"`…`"t12"`), sans qu'aucune technologie réelle soit débloquée.

En jeu normal `techUnlocked` n'est alimenté qu'avec de vrais identifiants, donc l'écart ne se voyait
pas. Mais il devient réel dès qu'une techno est **renommée ou retirée** de `ALL_TECHS` : une
sauvegarde ancienne conserverait alors plus d'entrées que le registre n'en compte, et déclencherait
une « Domination technologique » sans que l'arbre soit terminé — scénario d'autant plus plausible
maintenant que la couche de migration (S5) rend ce genre d'évolution de schéma envisageable.

**Correctif** — la condition vérifie que **chaque identifiant** du registre est présent.
**Tests** : `techVictoryRequiresTheRealTechIds`, `techVictoryNotAwardedWhenOneTechIsMissing`, et
`techVictoryWhenAllTechsUnlocked` réécrit avec les vrais identifiants.

---

## 3. Signalés, non modifiés (choix de conception)

- **C3 — Siège et déplacement.** `resolveCombat` marque l'attaquant `hasAttacked = true` **et**
  `hasMoved = true` : tirer sur une unité met fin au tour du vaisseau. `siegePlanet` et
  `capturePlanet` ne posent que `hasAttacked` — on peut donc bombarder une planète **puis se
  replier**. C'est peut-être voulu (frappe et retrait), mais l'asymétrie n'est écrite nulle part.
  Trancher, puis aligner le code ou le documenter.
- **C4 — Embarquement gratuit.** `handleLoadUnit` ne vérifie pas si l'escorte a déjà bougé et ne
  marque ni l'escorte ni le porte-vaisseaux comme ayant agi : on peut embarquer, puis déplacer et
  faire tirer le porte-vaisseaux dans le même tour. Le déploiement, lui, pose bien `hasMoved` sur
  l'unité sortante.
- **V2 — Annihilation mutuelle.** La conquête militaire exige `survivors.size == 1`. Si le dernier
  affrontement élimine les deux derniers camps simultanément, on tombe à **0 survivant** et
  *aucune* condition ne se déclenche : la partie continue indéfiniment jusqu'au tour 100. Cas rare,
  mais sans issue. Une règle de match nul explicite serait plus propre.
- **V3 — Score de fin de partie.** Au tour 100, le vainqueur est celui qui a le plus de **crédits**,
  sans considérer planètes tenues, flotte ni technologies. Un joueur thésaurisant bat un joueur
  dominant militairement. Un score composite serait plus fidèle à un 4X.
- **V4 — Zodiac et NPC.** La victoire territoriale parcourt `Faction.values()`, ce qui inclut
  `ANCIENT_NPC` : s'il venait à posséder tous les nœuds, il « gagnerait » la partie alors qu'il n'a
  même pas de `PlayerState`. Restreindre aux factions jouables.

## 4. Ce qui fonctionne bien

- **Riposte bornée par la portée** : le défenseur ne contre-attaque que si l'attaquant est à sa
  portée — l'avantage des unités à longue portée est réel, et la prévisualisation l'affiche.
- **Formule de dégâts unifiée** : `AttackCalculator` sert à la fois la résolution et l'aperçu de
  combat (attaque comme riposte), bonus et terrain compris.
- **Pas d'empilement d'unités** : `units` est indexé par coordonnée et `GameGridMap.isPassable`
  rejette une case occupée — une seule unité par hex, garanti par construction.
- **Objectifs de campagne prioritaires** : en mission, les conditions de victoire standard sont
  court-circuitées, et la défaite (plus d'unité ni de planète) est détectée explicitement.
- **Domination sur la durée** : tenir 60 % des planètes doit être maintenu **6 tours complets**,
  ce qui empêche une victoire sur un pic momentané.
