# Audit technique et gameplay — Nova Empire

> Audit statique complet du dépôt (branche `claude/gameplay-audit-complete-7juusz`).
> Objectif : identifier bugs, risques, optimisations et pistes d'amélioration gameplay.
> Les tests n'ont pas pu être exécutés dans l'environnement d'audit (voir §3 : la
> configuration de build ne se résout pas hors-ligne). Les constats reposent donc sur
> l'analyse du code source.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| B1 | 🟢 Ajusté | Build | Config **fonctionnelle en CI** (mon 1ᵉʳ diagnostic « bloquant » était faux). Vraie incohérence corrigée : wrapper Gradle `9.4.1`→`9.6.1` (requis par AGP 9.2.1) ; `local.properties` Windows dé-suivi ; doc resynchronisée (voir §3) |
| B2 | ✅ Corrigé | Combat | Siège et capture de planète **sans contrôle d'adjacence** — possibles depuis n'importe où sur la carte |
| B3 | ✅ Corrigé | Combat | La riposte s'applique même quand l'attaquant est **hors de portée** du défenseur — annule l'avantage des unités à distance |
| B4 | ✅ Corrigé | Combat | `AttackUnit` n'interdit pas le tir ami (attaque de ses propres unités) |
| B5 | ✅ Corrigé | Sauvegarde | L'auto-save capture l'état **avant** la résolution asynchrone du tour → sauvegarde périmée |
| B6 | ✅ Corrigé | Équilibrage | L'IA débloque les technologies **instantanément** (paye le coût, ignore le temps de recherche) |
| B7 | ✅ Corrigé | Pureté | `Random` global utilisé dans le réducteur (censé être pur) → non déterministe/non testable |
| B8 | ✅ Corrigé | Cohérence | `ECONOMIC_BOOM` : la description dit « +3 par système » mais le code applique un +3 forfaitaire |
| B9 | ✅ Corrigé | Combat | La riposte ignore tous les bonus du défenseur (tech/héros/terrain) — asymétrie non documentée |
| B10 | ✅ Corrigé | Propreté | 47 fichiers `.kt`/`.java` de debug à la racine, hors source-set (orphelins) |
| B11 | 🟢 Faible | Doc | `CLAUDE.md` décrit des versions obsolètes (AGP 8.4.1, Kotlin 1.9.23, Compose compiler 1.5.11) |
| B12 | 🟢 Faible | Gameplay | Héros non liés à leur faction (`targetFaction` défini mais jamais vérifié) |

---

## 2. Bugs de logique et de gameplay

> **✅ Correctifs B2–B10 appliqués.** Résumé de l'implémentation :
> - **B2** : `handleSiegePlanet` / `handleCapturePlanet` exigent désormais l'adjacence (`distanceTo ≤ 1`).
> - **B3** : la riposte n'a lieu que si `attaquant.distanceTo(défenseur) ≤ défenseur.range` (`CombatResolver`).
> - **B4** : `handleAttackUnit` rejette l'attaque d'une unité de sa propre faction.
> - **B5** : l'auto-save écoute désormais l'incrément de `turn` (état résolu) au lieu d'un instantané synchrone.
> - **B6** : l'IA passe par `researchInProgress` (mêmes tours de recherche que le joueur).
> - **B7** : `Random` est injecté via `GameEngineDependencies.rng` et propagé à `handleMoveUnit`.
> - **B8** : description d'`ECONOMIC_BOOM` alignée sur l'effet réel (+3 forfaitaire).
> - **B9** : la riposte applique les bonus d'attaque et le terrain du défenseur (formule miroir).
> - **B10** : 47 fichiers de debug orphelins supprimés de la racine.
>
> Tests ajoutés : `CombatResolverTest` (B3, B9) et `IntentReducerTest` (B2, B4).

### B2 — Siège et capture sans contrôle d'adjacence 🟠
`core/engine/IntentHandlers.kt:141` (`handleSiegePlanet`) et `:151` (`handleCapturePlanet`).

Les deux handlers valident : propriété de l'unité, action non consommée, cible = planète,
et (pour la capture) niveau 0. **Aucune vérification de distance** entre l'unité et la planète.
Un joueur peut donc, via l'intent, assiéger ou capturer une planète à l'autre bout de la carte.

L'IA masque le problème car elle ne cible que l'adjacence (`UtilityEvaluator.kt:66`,
`distanceTo(...) <= 1`), mais l'UI/intent humain n'est pas protégé.

**Correctif** : ajouter dans les deux handlers
```kotlin
if (intent.attackerCoord.distanceTo(intent.planetCoord) > 1)
    return GameResult(state, "You must be adjacent to the planet.")
```
(portée 1 pour rester cohérent avec le comportement de l'IA ; ou `<= unit.type.range` si
l'on veut autoriser le bombardement à distance de manière volontaire).

### B3 — La riposte ignore la portée de l'attaquant 🟠
`core/engine/CombatResolver.kt:42-55`.

`resolveCombatWithRng` applique systématiquement une contre-attaque si le défenseur survit,
**sans vérifier que l'attaquant est à portée du défenseur**. Un DREADNOUGHT (portée 3) qui
frappe un CRUISER (portée 1) à 3 cases subit malgré tout la riposte comme s'il était au
contact. Cela annule tout l'intérêt tactique des unités à longue portée (BATTLESHIP, CARRIER,
DREADNOUGHT, DEFENSE_PLATFORM).

**Correctif** : ne déclencher la riposte que si
`attackerCoord.distanceTo(defenderCoord) <= defender.type.range`.

### B4 — Tir ami / attaque d'un allié autorisés 🟠
`core/engine/IntentHandlers.kt:73` (`handleAttackUnit`).

Le handler vérifie la portée mais pas la faction ni la relation diplomatique du défenseur.
On peut donc attaquer ses propres unités ou celles d'un allié (`ALLIANCE`). Si c'est un choix
de design (trahison possible), il faut au minimum le rendre explicite ; sinon, bloquer :
```kotlin
if (defender.faction == unit.faction) return GameResult(state, "Cannot attack your own units.")
// et éventuellement bloquer les ALLIANCE
```

### B5 — L'auto-save enregistre un état périmé 🟠
`app/ui/viewmodels/GameViewModel.kt:87-91`.

```kotlin
if (intent is GameIntent.EndTurn) {
    val snapshot = engine.state.value            // état AVANT traitement
    viewModelScope.launch(Dispatchers.IO) { saveRepository.saveGame(snapshot) }
}
```
`processIntent` ne fait qu'empiler l'intent dans un `Channel` (`GameEngine.kt:106-108`) ; la
résolution du `EndTurn` (tours IA, revenus, production…) est **asynchrone** et peut durer
jusqu'à 10 s (`withTimeout(10_000L)`). Le `snapshot` lu juste après est donc l'état d'**avant**
la fin de tour. La sauvegarde est en retard d'un tour et ne contient pas les actions de l'IA.

**Correctif** : sauvegarder en réaction à la mise à jour d'état, p. ex. en observant
`engine.state` (collecter et persister après chaque changement de `turn`), ou exposer un
effet/callback « tour terminé » depuis le moteur et sauvegarder à ce moment-là.

### B6 — L'IA débloque la tech instantanément 🟡
`core/engine/UtilityEvaluator.kt:208-227` (`evaluateEconomyAndTech`).

L'IA ajoute directement `affordableTech.id` à `techUnlocked` en payant le coût, **sans passer
par `ResearchProgress`** ni consommer de tours (contrairement au joueur, cf.
`handleResearchTech` + `TurnManager.advanceTurn:113-133`). L'IA obtient donc ses technologies
sans délai, ce qui déséquilibre nettement la course technologique et facilite la victoire
technologique de l'IA. Idem, `evaluateHeroes:126-147` recrute instantanément (cohérent avec le
joueur, moins gênant).

**Piste** : router l'IA par le même pipeline `ResearchTech`/tick de recherche, ou assumer le
raccourci comme « handicap » réglable par difficulté (à documenter).

### B8 — `ECONOMIC_BOOM` : description ≠ effet 🟡
`core/domain/models/GalacticEvent.kt:8` décrit « +3 Credits per system owned » mais
`core/engine/EventEffectRegistry.kt:12` applique `INCOME_FLAT, 3` (forfait unique, appliqué via
`TurnManager.advanceTurn:57-58`). Soit corriger la description, soit implémenter le « par
système » (multiplier par le nombre de planètes possédées).

### B9 — La riposte ignore les bonus du défenseur 🟡
`core/engine/CombatResolver.kt:44`. L'attaque initiale intègre bonus tech/héros
(`ATTACK_FLAT`/`ATTACK_PERCENT`), variance et terrain (`:22-33`) ; la riposte, elle, n'utilise
que `defender.type.attack * counterVariance`. Un défenseur avec Plasma Weapons / Commander
Vance / nébuleuse ne profite d'aucun de ses bonus en défense. À harmoniser ou à documenter
comme règle voulue.

### B12 — Héros non liés à leur faction 🟢
`core/domain/models/Hero.kt:6` définit `targetFaction`, mais ni `handleRecruitHero`
(`IntentHandlers.kt:115`) ni l'IA ne le vérifient : n'importe quelle faction peut recruter
n'importe quel héros. Si les héros sont censés être spécifiques à une faction, ajouter le
contrôle ; sinon retirer le champ pour éviter la confusion.

### Autres points de gameplay mineurs
- **Load/Deploy comme « recharge » d'action** : `handleDeployUnit` (`IntentHandlers.kt:236`)
  crée l'unité avec `hasMoved = true` mais **pas** `hasAttacked` ; embarquer puis redéployer une
  unité ayant déjà tiré lui rend sa capacité d'attaque. Petit exploit.
- **Planète assiégée à niveau 0 encore rentable** : dans `TurnManager.advanceTurn:51-54`, une
  planète niveau 0 toujours détenue rapporte encore `5` crédits tant qu'elle n'est pas capturée.
- **Oscillation diplomatique de l'IA** : `evaluateDiplomacy` (`UtilityEvaluator.kt:171`) écrit
  des relations symétriques ; deux IA peuvent alterner ALLIANCE/WAR d'un tour à l'autre selon le
  rapport de force. Envisager une hystérésis / un cooldown.

---

## 3. Configuration de build — B1 (mise au point)

**Le build n'est PAS cassé en CI.** Vérification faite via GitHub Actions : le run CI sur la
config d'origine (AGP `9.2.1`, Kotlin `2.2.10` Android + `1.9.23` JVM, wrapper Gradle `9.4.1`,
Java 21) **passe intégralement**, y compris `:app:assembleDebug`. L'échec observé pendant l'audit
était **spécifique à l'environnement d'audit** : `dl.google.com` bloqué par le proxy (donc AGP
irrésolvable), aucun SDK Android, et un `gradle` système trop ancien (8.14.3). Mon diagnostic
initial « build bloquant » était donc **erroné** sur les points suivants :

- **AGP `9.2.1` existe et est requis.** La CI n'exécute pas `./gradlew` mais le `gradle` **système
  (9.6.x)**. Or l'AGP **8.x** utilise une API interne (`org.gradle.api.problems.internal.InternalProblems`)
  **supprimée dans Gradle 9.6.0** → seule une AGP **9.x** fonctionne sur cette CI. (Un essai de
  retour à AGP 8.13.2 a d'ailleurs cassé la CI avec exactement cette erreur, confirmant le point.)
- **Les deux versions de Kotlin coexistent volontairement** : Gradle isole le classpath de plugins
  par sous-projet, donc `:app` en `2.2.10` (+ plugin Compose) et les modules JVM en `1.9.23`
  compilent sans conflit. Ce n'est pas idéal (fragile, à surveiller) mais ce n'est pas bloquant.

### Vraie incohérence corrigée
- **Wrapper Gradle `9.4.1` → `9.6.1`.** AGP 9.2.1 exige Gradle ≥ 9.6 ; le wrapper pinné à `9.4.1`
  n'était jamais exercé par la CI (qui utilise le `gradle` système 9.6.x), mais un développeur
  lançant `./gradlew` en local aurait échoué. Le wrapper pointe désormais sur la version que la CI
  utilise réellement.
- **`local.properties` retiré du suivi git + ajouté au `.gitignore`.** Il contenait un `sdk.dir`
  Windows (`C:\Users\COMPAGNON\...`) qui n'a pas de sens hors de la machine d'origine. La CI n'en
  dépend pas (elle résout le SDK via `ANDROID_SDK_ROOT`), mais un fichier machine-spécifique n'a
  rien à faire dans le dépôt.
- **`CLAUDE.md` resynchronisé** sur les versions réelles (AGP 9.2.1, Kotlin 2.2.10/1.9.23, Java 21,
  Gradle 9.6.x, plugin Compose au lieu de `kotlinCompilerExtensionVersion`).

### Pistes d'assainissement (non bloquantes, non appliquées ici pour ne rien casser)
- Unifier Kotlin sur une seule version (idéalement `2.2.10` partout) et centraliser AGP/Kotlin/
  Gradle dans `gradle/libs.versions.toml` pour éviter la dérive.
- Faire lancer `./gradlew` par la CI (au lieu du `gradle` système) afin que la version testée soit
  celle que voient les développeurs.

---

## 4. Performance / optimisations

- **Recalcul complet de la vision à chaque sous-tour IA** :
  `GameEngine.handleIntent` appelait `updateVision(currentState)` (toutes factions) à chaque
  itération de la boucle IA, **juste avant** un `reduce(EndTurn)` qui recalcule déjà toute la
  vision — recompute redondant. **✅ Corrigé** : l'appel redondant a été retiré (`GameEngine.kt`).
- **`TurnManager.advanceTurn`** comptait les planètes par faction en `N` scans (`allFactions.size`
  appels à `count`). **✅ Corrigé** : un seul passage `groupingBy { owner }.eachCount()`.
  (`VictoryChecker.check` reparcourt encore plusieurs fois `map.tiles` — mutualisable de même,
  laissé de côté car non chaud.)
- **Pathfinding** *(proposition)* : `HexPathfinder.findPath` utilise une `PriorityQueue` de
  `Pair` sans suppression paresseuse ni cache, réinstanciée à chaque déplacement IA. Pour de
  grandes cartes, envisager `findReachable` (déjà présent) pour précalculer l'ensemble atteignable
  une fois par tour. *(non appliqué — le pathfinder fonctionne, changement risqué.)*
- **Rendu** *(proposition)* : le commit `c1e0319` a déjà séparé terrain statique / overlay
  dynamique ; vérifier que les `Canvas` lourds sont `remember`isés et que la LoS n'est pas
  recalculée côté UI. *(module `:app`, non vérifiable par la CI ici.)*

---

## 5. Qualité de code / maintenance

- **B10 — Fichiers orphelins à la racine** : **✅ Corrigé** (47 fichiers `.kt`/`.java` de debug
  d'arrondi supprimés ; `HexCoord.round` est **correct**, conforme à Red Blob Games).
- **B7 — `Random` global dans le réducteur** : **✅ Corrigé** (injecté via
  `GameEngineDependencies.rng`, propagé à `handleMoveUnit`/`applyExplorationDiscovery`).
- **`kotlinx.coroutines.delay(0)`** inutile en tête de `UtilityEvaluator.executeAITurn` :
  **✅ Corrigé** (retiré).
- **Nullabilité incohérente de `campaignState`** : **✅ Corrigé** (`GameEngine.reduce` et
  `CampaignManager` traitent désormais le champ comme non-nullable).
- **Code mort** : bloc d'observation audio commenté dans `GameViewModel` : **✅ Corrigé** (retiré).
- **`lastCombatEvent` jamais remis à null côté moteur** *(proposition)* : `@Transient`, positionné
  par le combat mais jamais nettoyé ; deux combats identiques consécutifs (mêmes coords, même issue)
  ne re-déclenchent pas le `LaunchedEffect` (clé égale). Le vrai correctif est architectural — router
  les effets ponctuels via le `SharedFlow` `GameEffect` (déjà amorcé) plutôt que via l'état ; touche
  le module `:app`. *(non appliqué : refonte UI hors périmètre sûr.)*

---

## 6. Pistes d'amélioration gameplay

- **Campagne — ennemi scénarisé passif** : **✅ Corrigé**. `handleStartCampaign` met désormais
  le couple joueur ↔ ennemi de la mission en `WAR` au lancement, sinon l'IA (qui n'engage que les
  cibles `WAR`/`NPC`) restait passive et les missions étaient triviales. Enrichir davantage
  l'initialisation (forces scénarisées, objectif `CAPTURE_SPECIFIC_PLANET` non géré) reste ouvert.

Les points suivants sont des **décisions de design / d'équilibrage** (plusieurs directions
valables, impact balance) : non appliqués unilatéralement — à cadrer avant implémentation.

- **Feedback tactique de portée** *(UI)* : afficher les cases atteignables (`findReachable`) et
  les cibles à portée avant l'ordre, pour rendre lisibles les corrections B2/B3.
- **Différenciation des factions** : **✅ Appliqué**. Chaque faction a désormais une identité
  distincte et non redondante via un champ `extraBonuses` (les 5 champs numériques hérités restent
  pour les aperçus UI) :
  - **DOMINION** — flotte d'élite : +10% attaque **+ 3 PV** sur les unités construites.
  - **TRADERS** — marchands : +5 crédits/tour **+ 15% de revenu**.
  - **SYNTH** — recherche : −15% coût tech **+ 1 vitesse de recherche**.
  - **NOMADS** — nomades : +1 déplacement **+ entretien de flotte réduit** (`UPKEEP_MODIFIER`).
  - **KAELEN** — voyants : +2 vision **+ mondes capturés à un niveau supérieur**.
  - **XYLAR** — essaim : +1 déplacement, +5% attaque **+ production plus rapide** (`PRODUCTION_SPEED`).

  Deux nouveaux `BonusType` (`PRODUCTION_SPEED`, `UPKEEP_MODIFIER`) câblés dans `TurnManager` ;
  les autres réutilisent des types déjà en place. Tests : `TurnManagerTest` (production XYLAR,
  upkeep NOMADS).
- **Boucle économique** : l'upkeep (`UnitType.upkeepCost`) peut rendre le revenu négatif ; ajouter
  un plancher/alerte et un coût d'entretien visible dans l'UI.
- **Événements galactiques ciblés** : effets par faction plutôt que globaux, pour de l'asymétrie.
- **IA — évaluation d'utilité réelle** : **✅ Appliqué**. La couche tactique de `UtilityEvaluator`
  ne fait plus des heuristiques greedy séquentielles : chaque unité génère des **actions candidates
  scorées** (attaque, capture, siège, déplacement, repli, regroupement) et exécute la meilleure via
  `reduce`. Le scoring récompense l'achèvement des cibles blessées, le tir **hors de portée ennemie**
  (dégâts sans riposte, exploite B3), pénalise les attaques/sièges suicidaires, priorise la **défense
  des planètes menacées**, fait **avancer** les unités vers des objectifs lointains (au-delà d'un
  tour de mouvement) et **regroupe** les unités isolées vers le centroïde de la flotte. Reste
  déterministe (sans RNG). Tests : `UtilityEvaluatorTest`. Les phases stratégiques (diplomatie,
  économie/tech, héros, production) sont conservées.

---

## 7. Priorisation recommandée

1. **B1** — Rétablir une configuration de build cohérente et testée (débloque tout le reste).
2. **B2, B3, B4** — Corriger les règles de combat (adjacence siège/capture, riposte hors-portée,
   tir ami) : impact direct sur l'équité du jeu, correctifs localisés et testables.
3. **B5** — Fiabiliser l'auto-save (perte de progression réelle pour le joueur).
4. **B6, B8, B9** — Équilibrage (IA tech instantanée, cohérence événements, riposte).
5. **B7, B10, B11** — Nettoyage (déterminisme du réducteur, fichiers orphelins, doc).

Chaque correctif de combat (B2/B3/B4/B9) est accompagnable d'un test unitaire dans
`core/engine/src/test/.../CombatResolverTest.kt` / `IntentReducerTest.kt`, modules déjà couverts
par la CI.
