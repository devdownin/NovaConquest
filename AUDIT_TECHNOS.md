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
| T3 | 💡 Amélio | Équilibrage | `baseCost` **jamais personnalisé** (toutes les techs = 8) : le coût ne reflète pas la puissance (tier‑4 = filler) |
| T4 | 🟡 Faible — ✅ corrigé | UX | Bouton `RESEARCH` **cliquable même sans crédits** → erreur moteur au lieu d'un état désactivé |
| T5 | 💡 Amélio | Feature gap | Pas d'**annulation de recherche** (asymétrie avec `CancelBuild`) → crédits perdus sur erreur |
| T6 | ⚪ Comportement | IA | Recherche IA **naïve** : toujours dans l'ordre de `ALL_TECHS` (militaire d'abord), sans stratégie |
| T7 | 🟡 Design | Événements | `anomaly_analysis` est **global** : la tech de n'importe quel joueur raccourcit l'événement partagé pour tout le monde |
| T8 | 🧹 Propreté | Mort‑code | Constante `TechRegistry.DEEP_SCANNERS` **jamais référencée** |

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

---

## 3. Design / équilibrage / IA (signalés, non modifiés)

- **T3 — `baseCost` inexploité.** `TechDefinition.baseCost` vaut 8 par défaut et n'est
  **jamais surchargé** ; le coût ne varie que via `baseCost + 6 × (techs débloquées dans la
  branche)`. Une tech tier‑4 déterminante (`nano_armor` +5 PV, `stellar_mining` +15 crédits,
  `quantum_relay` +20 % revenu) coûte le même barème qu'une tech de remplissage. Piste :
  renseigner `baseCost` par tech/tier. *(Changement d'équilibrage — laissé à votre décision.)*
- **T5 — Pas d'annulation de recherche.** `CancelBuild` existe et rembourse 50 %, mais aucun
  `CancelResearch` : une recherche lancée par erreur immobilise les crédits jusqu'à la fin (ou
  l'aptitude du héros Kael). Piste : ajouter un intent symétrique avec remboursement partiel.
- **T6 — Recherche IA naïve.** `UtilityEvaluator.evaluateEconomyAndTech` prend la **première**
  tech disponible et abordable dans l'ordre de `ALL_TECHS` (militaire → expansion →
  exploration). L'IA ne priorise ni par valeur, ni selon sa posture (agressive/économique).
  Fonctionnel mais prévisible.
- **T7 — `anomaly_analysis` global.** `EventSystem.tick` teste `state.playerStates.values.any {
    …contains("tech_anomaly_analysis") }` : la tech d'**un seul** joueur accélère la fin de
  l'événement **partagé** pour tous. Comme l'état d'événement est unique et global, un
  scope par faction demanderait un suivi d'événement par joueur (refactor). Signalé.

## 4. Propreté

- **T8** — `TechRegistry.DEEP_SCANNERS` (constante publique) n'est référencée nulle part.
  Laissée en place (API publique potentielle) mais candidate à suppression.
- **Duplication de formule (rappel B3 côté cartes)** — la prévisualisation de combat
  (`TacticalMapScreen`) **recalcule à la main** les bonus d'attaque (`tech_plasma_weapons` +2,
  bonus de faction, héros Vance) au lieu d'appeler un calculateur partagé avec
  `CombatResolver`. Même risque de divergence que pour le mouvement : un futur ajout de bonus
  d'attaque devra être répliqué aux deux endroits.

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
