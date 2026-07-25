# Décisions de conception — arbitrage des points laissés ouverts

> Les audits précédents (cartes, technologies, héros, sauvegarde, combat/victoire, production) ont
> livré les correctifs techniques mais laissé **sept points ouverts**, parce qu'ils engageaient des
> choix d'équilibrage plutôt que la correction d'un défaut. Ce document tranche chacun, explique
> le raisonnement, et référence les tests correspondants.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Tout le code décidé ici est en `:core:*` (hors affichage), donc exercé par la CI.

| # | Sujet | Décision |
|---|-------|----------|
| **P4** | Durée de construction | Indexée sur la classe de coque (1 → 5 tours) |
| **P6** | Production IA | Selon la posture : défense si menacé, puissance en guerre, rendement en paix |
| **C3** | Siège et déplacement | Aligné sur le combat : assiéger/capturer consomme le tour |
| **V2** | Annihilation mutuelle | **Match nul** explicite (vainqueur `null`) |
| **V3** | Score de fin de partie | Composite : crédits + territoire + flotte + recherche |
| **H2** | Affinité des héros | **Tarifée**, pas verrouillée (×2 hors faction, mercenaire au tarif de base) |
| **H6** | Puissance de Nix | Soin actif ramené à la moitié de la coque |

---

## P4 — Durée de construction indexée sur la classe

**Constat.** `buildTurns` renvoyait **2 tours pour six types sur sept** (seul le dreadnought en
demandait 3). Un éclaireur à 3 crédits sortait donc aussi vite qu'un porte-vaisseaux à 25 : le temps
ne jouait aucun rôle, seul le trésor comptait.

**Décision.** Barème par classe : Scout 1 · Fighter/Cruiser 2 · Defense Platform/Battleship 3 ·
Carrier 4 · Dreadnought 5. Les grosses coques deviennent un engagement, et les accélérateurs
existants (mondes-forges ×2, bonus `PRODUCTION_SPEED` de XYLAR) gagnent enfin un intérêt réel.

**Tests** — `BuildTurnsTest` : progression avec la classe, minimum d'un tour, et **monotonie par
rapport au coût** (aucune coque plus chère ne se construit plus vite qu'une moins chère).

## P6 — Production IA selon la posture

**Constat.** `evaluateProduction` prenait systématiquement **l'unité la plus chère abordable**.
En paix, l'IA vidait son trésor en dreadnoughts sans rien à combattre — et n'avait plus de quoi
chercher ni recruter.

**Décision.** `chooseUnitToBuild` score les coques abordables :
planète menacée → **plateforme de défense** ; en guerre → **meilleure puissance brute** ; en paix →
**meilleur rendement par crédit**, ce qui favorise les coques bon marché et préserve la trésorerie.
Les plateformes sont exclues du choix de paix : immobiles, elles gèleraient l'expansion.

C'est la même logique que pour la recherche (T6) et le recrutement de héros (H5) — l'IA raisonne
désormais uniformément sur sa situation.

**Tests** — dreadnought en guerre, coque abordable en paix, plateforme si menacé, rien sans crédits.

## C3 — Assiéger consomme le déplacement

**Constat.** `resolveCombat` posait `hasAttacked` **et** `hasMoved` : tirer sur une unité met fin au
tour du vaisseau. `siegePlanet`/`capturePlanet` ne posaient que `hasAttacked` — on pouvait donc
bombarder une planète **puis se replier hors de portée** dans le même tour.

**Décision.** Aligner : attaquer quoi que ce soit — unité ou monde — consomme le tour. La règle
devient uniforme et énonçable en une phrase, et le siège cesse d'être une frappe sans risque.

**Tests** — `siegeConsumesTheShipsMovement`, `captureConsumesTheShipsMovement`.

## V2 — Match nul en cas d'annihilation mutuelle

**Constat.** La conquête militaire exigeait `survivors.size == 1`. Si le dernier affrontement
éliminait les deux derniers camps, on tombait à **zéro survivant** et *aucune* condition ne se
déclenchait : la partie continuait, plateau vide, jusqu'au tour 100.

**Décision.** Match nul explicite. `VictoryResult.winner` devient **nullable** : la partie se
termine avec une raison mais sans vainqueur. Trois ajustements en découlent :

- le passage en revue se fait désormais sur `victoryReason` et non sur `winner` — sinon un nul,
  dont le vainqueur est `null`, serait ré-évalué à chaque tour ;
- `MainActivity` déclenche l'écran de fin sur `victoryReason`, pour que le nul y mène aussi ;
- la bannière moteur affiche « MATCH NUL » au lieu d'un nom de faction.

La détection exige une carte non vide, ce qui l'empêche de se déclencher sur un état squelette qui
n'a légitimement pas encore de territoire.

**Tests** — `mutualAnnihilationEndsInADraw`, `aSettledDrawStaysSettled`.

## V3 — Score de fin de partie composite

**Constat.** Au tour 100, le vainqueur était **celui qui avait le plus de crédits**. Un joueur
thésaurisant, sans jamais quitter son système, battait celui qui avait conquis la galaxie.

**Décision.** `VictoryChecker.empireScore` additionne : crédits + territoire
(40 par planète + 10 par niveau) + flotte (coût cumulé des unités) + recherche (20 par techno).
Les quatre axes du 4X pèsent, l'accumulation stérile ne suffit plus.

**Tests** — `timeLimitScoreCountsTerritoryNotJustCredits` (le détenteur d'un monde de niveau 2 bat
le thésauriseur), `empireScoreRewardsFleetAndResearch`.

## H2 — Affinité tarifée plutôt que verrouillée

**Constat.** `Hero.targetFaction` existait mais n'était **jamais vérifié** : n'importe quelle
faction recrutait n'importe qui, au même prix. Le champ était décoratif.

**Décision — et pourquoi pas un verrou.** Le réflexe serait d'interdire le recrutement hors
affinité. C'est un piège avec le casting actuel : sur quatre héros, seuls DOMINION, TRADERS et SYNTH
ont le leur, et Nix jure allégeance à `ANCIENT_NPC`. Un verrou strict laisserait donc **NOMADS,
KAELEN et XYLAR sans aucun héros** — trois factions jouables sur six amputées d'un pan entier du jeu.

L'affinité est donc **tarifée** (`HeroCostCalculator`) :

| Cas | Prix |
|-----|------|
| Le héros de votre faction | tarif de base |
| Le héros d'une autre faction | **×2** (il faut le convaincre) |
| Mercenaire (`ANCIENT_NPC`, soit Nix) | tarif de base pour tous |

Chaque faction garde accès à tout le casting, mais le sien est nettement le plus avantageux. Le
calculateur est partagé par le moteur, l'IA et l'académie — donc aucune divergence d'affichage
possible, comme pour `AttackCalculator` et `IncomeCalculator`.

**Tests** — `HeroCostCalculatorTest`, dont `everyFactionCanStillReachEveryHero` qui verrouille
précisément la propriété qu'un verrou strict aurait cassée.

> 💡 Piste laissée ouverte : doter NOMADS, KAELEN et XYLAR de leur propre héros. Le verrou strict
> deviendrait alors défendable — mais cela relève de la création de contenu, pas de l'audit.

## H6 — Nix ramené à un soin partiel

**Constat.** Nix cumulait un soin **passif** (+1 PV/tour) et un soin **actif remettant toute la
flotte à pleins PV** — et, étant le mercenaire, il est recrutable par tout le monde. Perdre une
bataille rangée n'avait presque aucune conséquence.

**Décision.** L'aptitude « Refuge Stellaire » répare désormais **la moitié de la coque** de chaque
unité (arrondi au supérieur) au lieu de la restaurer entièrement. Elle reste un puissant retournement
de situation — sans effacer le résultat d'un engagement. Le passif est conservé.

**Test** — `nixAbilityRepairsHalfTheHullNotAllOfIt`.
