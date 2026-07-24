# Audit — Gestion de l'arbre des technologies

> Portée : modèle (`TechDefinition`/`TechRegistry`, `TechBranch`), calcul de coût
> (`CostCalculator`, `TechRegistry.baseCost`), file de recherche (`handleResearchTech`,
> `GameEngine`), progression par tour (`TurnManager`), application des bonus (`BonusRegistry`,
> `VisionSystem`, `CombatResolver`, `GameGridMap`, `EventSystem`), recherche IA
> (`UtilityEvaluator`), victoire technologique (`VictoryChecker`) et l'écran
> `TechTreeScreen`.
>
> ⚠️ **Tests non exécutés localement** : le proxy réseau bloque `dl.google.com` (403),
> l'AGP `9.2.1` est inaccessible → build Gradle impossible. Les correctifs moteur sont
> couverts par des tests JVM (CI) ; les correctifs UI sont revus statiquement.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **T1** | 🟠 Moyen — ✅ corrigé | Cohérence UX | Le coût affiché dans l'arbre **ignore la remise d'événement** (`ANCIENT_SIGNAL` -25 %) → coût trop élevé, tech faussement « non finançable » |
| T2 | 🟡 Faible — ✅ corrigé | Cohérence | `Anomaly Analysis` : description « -2 tours » alors que le code fait décroître **2× plus vite** |
| T3 | 💡 Amélio — ✅ fait | Équilibrage | `baseCost` par tier (8/14/22/32) : les techs tardives coûtent plus |
| T4 | 🟡 Faible — ✅ corrigé | UX | Bouton `RESEARCH` **cliquable même sans crédits** → erreur moteur au lieu d'un état désactivé |
| T5 | 💡 Amélio — ✅ fait | Feature gap | `CancelResearch` avec remboursement 50 % (symétrique de `CancelBuild`) |
| T6 | ⚪ Comportement — ✅ fait | IA | Recherche IA **stratégique** : pondérée par la posture (guerre → militaire, paix → expansion) |
| T7 | 🟡 Design — ✅ fait | Événements | `anomaly_analysis` **scopé au propriétaire** de l'événement ciblé (fin du leak global) |
| T8 | 🧹 Propreté — ✅ fait | Mort‑code | `DEEP_SCANNERS` supprimée + **calculateur d'attaque partagé** (fin de la divergence prévisualisation/combat) |

---

## 2. Bugs

### T1 — 🟠 Le coût affiché ignore la remise d'événement  ✅ **corrigé**

`TechTreeScreen.buildUiNode` appelait :

```kotlin
val cost = CostCalculator.techCost(tech.id, unlockedTechs, playerState)  // ← pas d'événement
```

`CostCalculator.techCost` accepte `activeEvent` + `eventTargetFaction`, et **le moteur les
passe** (`handleResearchTech`, `UtilityEvaluator`). L'UI ne les passait pas → pendant un
`ANCIENT_SIGNAL` (-25 % ciblé), le coût affiché reste au prix fort et `canAfford` compare au
prix fort. Conséquence : une tech réellement finançable apparaît en rouge « INSUFFICIENT »,
alors que le moteur l'accorderait au prix réduit. (Les remises de **faction** SYNTH et de
**héros** Kael étaient, elles, correctes car `playerState` était bien transmis.)

**Correctif** — l'UI passe désormais `gameState.activeEvent` et `gameState.eventTargetFaction`.
Affichage = affordabilité = prix réellement débité.

**Test** : `CostCalculatorTest.targetedEventDiscountAppliesOnlyToTargetFaction` (la remise
`ANCIENT_SIGNAL` s'applique à la faction ciblée, pas aux autres).

### T2 — 🟡 Description inexacte d'« Anomaly Analysis »  ✅ **corrigé**

La description disait *« Galactic events end 2 turns sooner »*, mais `EventSystem.tick`
applique `duration -= (1 + 1)` quand la tech est présente → l'événement **décroît deux fois
plus vite** (exactement « -2 tours » seulement pour une durée initiale de 4). Description
alignée : *« Galactic events decay twice as fast »*.

### T4 — 🟡 Bouton de recherche cliquable sans crédits  ✅ **corrigé**

`TechNodeCard` affichait le bouton `RESEARCH` en rouge quand `!canAfford`, mais il restait
**cliquable** → clic ⇒ le moteur rejette (`Not enough credits`) via une erreur. `IndustrialButton`
n'avait pas d'état désactivé. **Correctif** : ajout d'un paramètre `enabled: Boolean = true`
(rétrocompatible, `clickable(enabled = …)` + atténuation), et le bouton passe
`enabled = node.canAfford` avec un libellé `INSUFFICIENT (n C)`.

### T3 — 💡 Barème de coût par tier  ✅ **fait**

`TechDefinition.baseCost` valait 8 pour **toutes** les techs ; le coût ne montait que via
`baseCost + 6 × (techs débloquées dans la branche)`. Une tech tier‑4 déterminante coûtait le
même barème qu'une tech de remplissage. **Correctif** — `baseCost` par tier (constantes
`TechRegistry.TIER_1_COST..TIER_4_COST` = 8 / 14 / 22 / 32), renseigné sur chaque définition.
Le tier 1 conserve 8 (aucune sauvegarde ni test cassé). Escalade d'une branche (ex. militaire) :
8 → 20 → 34 → 50 au lieu de 8 → 14 → 20 → 26.

**Tests** : `CostCalculatorTest.higherTierTechsCostMore` + mise à jour de
`costIncreasesWithUnlockedBranchTechs` (plasma tier‑2 = 20).

### T5 — 💡 Annulation de recherche  ✅ **fait**

Aucun pendant à `CancelBuild` : une recherche lancée par erreur immobilisait les crédits.
**Correctif** — nouvel intent `CancelResearch` + `handleCancelResearch` : rembourse **50 %**
des crédits réellement dépensés (`ResearchProgress.costPaid`, nouveau champ défaut `0` →
compatible sauvegardes) et vide la file. Bouton `CANCEL` ajouté à la carte `RESEARCHING`,
câblé dans `MainActivity`.

**Tests** : `IntentReducerTest.cancelResearchRefundsHalfAndClearsQueue` et
`cancelResearchWithoutQueueIsNoOp`.

---

## 3. Comportement IA / événements

### T6 — ⚪ Recherche IA stratégique  ✅ **fait**

`UtilityEvaluator.evaluateEconomyAndTech` prenait la **première** tech disponible/abordable
dans l'ordre de `ALL_TECHS` → toujours la branche militaire d'abord. **Correctif** — nouveau
`chooseResearchTech(state, playerState)` (`internal`, testable) qui pondère les candidats par la
**posture** : une faction en guerre privilégie `MILITARY`, en paix `EXPANSION` (économie),
`EXPLORATION` au milieu ; le coût le moins élevé départage. **Tests** :
`aiAtWarPrefersMilitaryResearch`, `aiAtPeacePrefersExpansionResearch`.

### T7 — 🟡 `anomaly_analysis` scopé au propriétaire  ✅ **fait**

`EventSystem.tick` testait `state.playerStates.values.any { …contains("tech_anomaly_analysis") }`
→ la tech d'**un seul** joueur accélérait l'événement **partagé** pour tous (leak). **Correctif** —
l'accélération ne s'applique qu'à la **faction ciblée** par un événement ciblé, si elle possède
la tech ; les événements globaux (sans propriétaire) décroissent au rythme normal. Description
alignée : *« Targeted events on your empire decay twice as fast »*. **Tests** :
`anomalyAnalysisAcceleratesOnlyTheTargetedOwner`, `globalEventsDecayNormallyRegardlessOfAnomalyAnalysis`.

## 4. Propreté

### T8 — 🧹 Nettoyage + calculateur d'attaque partagé  ✅ **fait**

- **`DEEP_SCANNERS`** (constante inutilisée) supprimée.
- **Duplication de formule** — la prévisualisation de combat recalculait à la main les bonus
  d'attaque (Vance +15 %, bonus de faction, plasma +2) et **divergeait** de `CombatResolver`
  (chaque source arrondie séparément via `max(1, …)` → total gonflé ; la riposte ignorait
  aussi les bonus du défenseur **et** la portée). Nouveau `AttackCalculator.effectiveBase` /
  `damageRange`, **source unique** utilisée par `CombatResolver` (attaque **et** riposte, la
  fonction est symétrique) et par la prévisualisation UI. Parité de combat préservée (les
  multiplicateurs de terrain valent 1.0 dans tous les tests existants). **Tests** :
  `AttackCalculatorTest` (arrondi combiné, terrains, fourchette ±20 %).

---

## 5. Ce qui fonctionne bien

- **Pipeline de recherche unifié joueur/IA** : depuis un audit précédent, l'IA passe par
  `researchInProgress` et paie le coût, puis `TurnManager` fait décroître la recherche — plus
  de déblocage instantané.
- **Prérequis cohérents** : `handleResearchTech` exige le prérequis **débloqué**, interdit la
  double recherche et vérifie l'affordabilité ; l'UI reflète les 4 états
  (`UNLOCKED/RESEARCHING/AVAILABLE/LOCKED`).
- **Bonus appliqués au bon site** : HP au spawn (`TurnManager`), attaque/siège
  (`CombatResolver`), vision (`VisionSystem` + `VisionBonusProvider`), navigation par trou de
  ver (`GameGridMap`) — via `BonusRegistry`, avec scope d'événement ciblé correct côté moteur.
- **Vitesse de recherche modulaire** : `1 + hubs de recherche + RESEARCH_SPEED` (SYNTH,
  `TECH_RUSH`), et temps de base `tier + 1` (les techs tardives prennent plus de tours).
