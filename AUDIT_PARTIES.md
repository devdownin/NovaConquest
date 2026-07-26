# Audit — Gestion des parties (cycle de vie)

> Portée : démarrage d'une partie (escarmouche et campagne), sélection de faction, reprise d'une
> sauvegarde, navigation entre écrans (`MainActivity`), auto-sauvegarde et fin de partie
> (`VictoryScreen`, retour au menu). Transversal aux audits précédents, qui couvraient chacun un
> sous-système ; celui-ci examine ce qui les relie.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> L'essentiel des constats est ici côté `:app`, donc **revu statiquement** ; seule l'invariante de
> fin de partie est verrouillée par un test moteur exécuté en CI.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **G1** | 🟠 **Moyen — ✅ corrigé** | Fin de partie | Un **match nul** s'affichait « VICTORY ACHIEVED » — régression introduite par la PR #62 et non propagée à l'écran de fin |
| **G2** | 🟠 **Moyen — ✅ corrigé** | Fin de partie | Le « FINAL SCORE » affichait les seuls **crédits**, alors que le vainqueur est départagé au score composite (PR #62) |
| **G3** | 🟠 **Moyen — ✅ corrigé** | Reprise | L'auto-sauvegarde enregistrait la partie **terminée** : « RESUME COMMAND » ramenait droit sur l'écran de victoire |
| G4 | 🟡 Faible — ✅ corrigé | Performance | `hasSavedGame()` lisait le disque **à chaque recomposition** du menu principal |
| G5 | 💡 Design | Sécurité | Lancer une nouvelle partie **écrase la partie en cours** sans confirmation |

---

## 2. Bugs corrigés

### G1 — 🟠 Un match nul annoncé comme une victoire  ✅ **corrigé**

```kotlin
isDefeat = gameState.winner != null && gameState.winner != gameState.humanFaction
```

La PR #62 a introduit le match nul en rendant `VictoryResult.winner` **nullable**. Mais l'écran de
fin ne connaît que deux issues : victoire ou défaite. Pour un nul, `winner` vaut `null` → `isDefeat`
est `false` → le joueur voyait s'afficher :

> **VICTORY ACHIEVED**
> *Mutual Annihilation — no empire survives*

…c'est-à-dire des félicitations pour l'anéantissement réciproque de toutes les factions.

> 🔍 **Régression de ma part** : j'ai propagé le vainqueur nullable dans le moteur, le passage en
> revue des conditions et le déclenchement de l'écran de fin (`victoryReason`), mais **pas** dans
> l'affichage de cet écran. Le correctif est précisément ce qui manquait.

**Correctif** — `VictoryScreen` prend désormais une issue explicite (`GameOutcome.VICTORY /
DEFEAT / DRAW`), avec titre et couleur dédiés pour le nul, et « — » à la place du vainqueur.
`MainActivity` la calcule : pas de vainqueur ⇒ nul, vainqueur = faction humaine ⇒ victoire, sinon
défaite. Comme l'écran n'est atteint que lorsque `victoryReason` est renseigné, un `winner` nul y
signifie sans ambiguïté un match nul.

**Test** — `aFinishedGameAlwaysCarriesAReason` verrouille l'invariante dont dépendent l'écran de fin
**et** la garde d'auto-sauvegarde : toute issue terminale renseigne `victoryReason`, y compris le nul.

### G2 — 🟠 Un score final qui n'est pas celui qui départage  ✅ **corrigé**

L'écran affichait `playerStates[humanFaction].credits` sous l'intitulé « FINAL SCORE ». Or la PR #62
a fait du **score composite** (`VictoryChecker.empireScore` : crédits + territoire + flotte +
recherche) le critère qui désigne le vainqueur au tour 100. Le joueur pouvait donc perdre en
affichant le plus gros « score final » — exactement le motif de divergence affichage/moteur corrigé
tout au long de cette série (`AttackCalculator`, `IncomeCalculator`, `MovementCalculator`…).

**Correctif** — l'écran appelle `VictoryChecker.empireScore`, la fonction qui décide réellement.

### G3 — 🟠 Reprendre une partie déjà terminée  ✅ **corrigé**

La victoire est détectée pendant le même `EndTurn` qui incrémente le compteur de tours. Or
l'auto-sauvegarde observe précisément ce compteur :

```kotlin
if (state.turn != lastSavedTurn) { … saveGame(state) }
```

Elle enregistrait donc un état **terminal**. Enchaînement :

1. la partie s'achève, l'auto-save écrit un état avec `winner`/`victoryReason` ;
2. de retour au menu, « RESUME COMMAND » est proposé (une sauvegarde existe bien) ;
3. le chargement déclenche `checkVictoryConditions`, qui repasse aussitôt le résultat déjà acquis ;
4. le joueur atterrit **directement sur l'écran de victoire** d'une partie qu'il a déjà finie —
   sans jamais pouvoir reprendre quoi que ce soit.

**Correctif** — deux verrous complémentaires :
- l'auto-sauvegarde **ignore un état terminal**, ce qui laisse sur le disque la sauvegarde du tour
  précédent — jouable, et permettant même de rejouer le dernier tour ;
- `loadGame` **refuse** une sauvegarde terminale avec un message explicite, pour les fichiers
  écrits avant ce correctif.

### G4 — 🟡 Lecture disque à chaque recomposition  ✅ **corrigé**

`val hasSave = gameViewModel.hasSavedGame()` était évalué dans le corps du composable du menu :
un accès au système de fichiers à **chaque recomposition**. Encapsulé dans un `remember` clé sur
l'écran courant — la valeur est recalculée à l'entrée dans le menu, c'est-à-dire au seul moment où
elle peut avoir changé.

---

## 3. Signalés, non modifiés

- **G5 — Nouvelle partie sans confirmation.** Depuis le menu, « SKIRMISH » ou une mission de
  campagne réinitialise l'état immédiatement ; si une partie était en cours, elle est perdue (et
  l'anneau d'auto-sauvegarde finira par la faire tourner hors de portée). Une confirmation quand
  une partie est en cours serait une garde peu coûteuse.

## 4. Ce qui fonctionne bien

- **Ordre des intentions garanti** : le lancement d'une campagne enchaîne
  `StartNewGameWithSize` → `SelectFaction` → `StartCampaign` ; le canal d'intentions étant FIFO, la
  séquence arrive toujours dans cet ordre au réducteur.
- **Réinitialisation complète** : `createInitialState` reconstruit un `GameState` neuf, ce qui
  efface d'office vainqueur, raison de victoire et état de campagne — démarrer une escarmouche après
  une campagne ne laisse aucun résidu.
- **Garde pendant le tour de l'IA** : les intentions de jeu sont rejetées tant que `isAiThinking`
  est actif, à l'exception de celles qui démarrent ou chargent une partie — on ne peut pas jouer
  par-dessus la résolution asynchrone.
- **Auto-sauvegarde après résolution** : elle observe le compteur de tours et capture donc l'état
  **après** la boucle d'IA, jamais l'instantané d'avant-tour.
- **Erreurs de chargement remontées** : `loadGame` distingue succès, absence de sauvegarde et échec,
  et le menu affiche la raison dans une snackbar plutôt que d'échouer en silence.
