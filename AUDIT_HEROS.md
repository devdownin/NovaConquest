# Audit — Gestion des héros

> Portée : modèle (`Hero`/`HeroRegistry`), recrutement (`handleRecruitHero`), aptitudes actives
> (`handleUseHeroAbility`), bonus passifs (`BonusRegistry`, `TurnManager`, `CombatResolver`,
> `CostCalculator`), recrutement IA (`UtilityEvaluator.evaluateHeroes`) et l'écran
> `HeroAcademyScreen`.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Les correctifs moteur/données sont couverts par des tests JVM (CI) ; l'UI est revue statiquement.
>
> 📌 Une **seconde passe** (HR1–HR10) suit la première partie de ce document : elle repart du code
> actuel et traite ce qui restait — aptitudes jamais utilisées par l'IA, aptitude de Kael gaspillée,
> libellés dupliqués, couverture de tests.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **H1** | 🔴 **Majeur — ✅ corrigé** | Cohérence UX | L'académie codait **sa propre liste de héros** (coûts 1200–2000, factions/​noms erronés) divergente de `HeroRegistry` (coûts **40–75**) → le prix affiché était fictif |
| H2 | 🟡 Design — ✅ tranché (voir AUDIT_DECISIONS_GAMEPLAY.md) | Recrutement | `Hero.targetFaction` **jamais vérifié** : toute faction recrute tout héros. Nix = `ANCIENT_NPC` → verrouillage strict impossible sans réattribuer sa faction |
| H3 | 🟡 Faible — ✅ corrigé | UX | Bouton `RECRUIT` **cliquable sans crédits** → erreur moteur au lieu d'un état désactivé |
| H4 | ⚪ Duplication — ✅ fait | Revenu | `IncomeCalculator` partagé : l'aperçu HUD affiche enfin le revenu réellement versé |
| H5 | ⚪ Comportement — ✅ fait | IA | Recrutement IA scoré : affinité de faction, posture (guerre/paix) et état de la flotte |
| H6 | 🟡 Design — ✅ tranché (voir AUDIT_DECISIONS_GAMEPLAY.md) | Équilibrage | Nix (héros `ANCIENT_NPC`) recrutable par **tous** cumule un **passif** (+1 PV/tour) et un **actif** (soin complet) |

---

## 2. Bugs

### H1 — 🔴 Liste de héros dupliquée et divergente  ✅ **corrigé**

`HeroAcademyScreen` construisait sa **propre** liste :

```kotlin
val allHeroes = listOf(
    Hero("hero_vance", "Commander Vance", Faction.DOMINION, 1500, "+15% Raw Damage Output"),
    Hero("hero_kael",  "Architect Kael",  Faction.SYNTH,    1200, ...),
    Hero("hero_nix",   "High Seer Nix",   Faction.XYLAR,    2000, ...),
    Hero("hero_elara", "Admiral Elara",   Faction.DOMINION, 1800, ...),
)
```

Or `HeroRegistry.ALL_HEROES` — que le moteur utilise réellement (`handleRecruitHero` débite
`HeroRegistry.getHero(id).cost`) — dit tout autre chose :

| Héros | UI (affiché) | Registre (débité réel) |
|-------|--------------|------------------------|
| Vance | 1500 C, DOMINION | **50 C**, DOMINION |
| Kael | 1200 C, SYNTH | **60 C**, SYNTH |
| Nix | 2000 C, **XYLAR** | **75 C**, **ANCIENT_NPC** |
| Elara | 1800 C, **Admiral**, DOMINION | **40 C**, **Captain**, **TRADERS** |

Conséquences : l'académie affichait « 1500 C » et un bouton rouge « non finançable » alors que le
moteur ne facturait que 50 C ; factions et noms faux. Confirmé par
`IntentReducerTest` (recruter Vance ⇒ `100 - 50` crédits).

**Correctif** — l'écran consomme désormais **`HeroRegistry.ALL_HEROES`** (source unique) :
coûts, noms, factions et descriptions viennent du registre. La question **d'équilibrage** (les
héros doivent-ils coûter 40–75 ou 1200–2000 ?) se règle maintenant en un seul endroit — le
registre — sans re-diverger. **Test** : `HeroRegistryTest` (ids uniques, coûts > 0, résolution).

### H3 — 🟡 Bouton de recrutement cliquable sans crédits  ✅ **corrigé**

Comme pour la recherche (T4), le bouton `RECRUIT` restait cliquable en rouge → clic ⇒ erreur
moteur « Not enough credits ». **Correctif** : `enabled = canAfford` (le paramètre existe déjà
sur `IndustrialButton`) + libellé `INSUFFICIENT`.

---

## 3. Design / IA / duplication (signalés)

- **H2 — `targetFaction` non appliqué.** `handleRecruitHero` ne vérifie pas la faction ; l'UI
  n'a jamais filtré. Le champ était **mort**. J'ai commencé à l'exploiter côté UI en affichant
  l'**affinité** (« AFFINITY — <faction> ») sur chaque carte. **Verrouiller** le recrutement à
  la faction est un choix gameplay : ⚠️ Nix a `targetFaction = ANCIENT_NPC`, donc un verrou
  strict le rendrait **irrecrutable** pour tous. Options : (a) laisser les héros « mercenaires »
  (affinité informative, actuel) ; (b) verrouiller à la faction **et** réattribuer Nix à une
  faction jouable ou en faire un héros neutre explicite. À décider.
- **H6 — Puissance de Nix.** Recrutable par tous, Nix cumule soin passif (+1 PV/tour,
  `TurnManager`) et soin complet actif — combo fort, à surveiller à l'équilibrage.

### H4 — ⚪ Calcul de revenu unifié  ✅ **fait**

L'aperçu HUD (`TacticalMapScreen.incomePerTurn`) répliquait la formule à la main et **divergeait
sérieusement** de `TurnManager` :

| Écart | HUD (avant) | Moteur (réel) |
|-------|-------------|---------------|
| Base | **10** | **6** |
| Elara | codée en dur `+10 % +2` | via `BonusRegistry` |
| Événements | `ECONOMIC_BOOM +3` / `PIRATE_RAID −5` **sans vérifier le ciblage** (affichait le boom d'une autre faction !) | scopés à `eventTargetFaction` |
| Comptoir commercial (`TRADE_POST` +8) | **ignoré** | compté |
| `UPKEEP_MODIFIER` (ex. NOMADS) | **ignoré** | appliqué |

**Correctif** — nouveau `IncomeCalculator.perTurn(state, faction)` dans `:core:engine`, **source
unique** : `TurnManager` l'utilise pour créditer, le HUD pour afficher. L'aperçu correspond
désormais exactement aux crédits versés. **Tests** : `IncomeCalculatorTest` (base/planètes,
comptoir, entretien, événement ciblé vs non ciblé, Elara, et **parité avec `advanceTurn`**).

### H5 — ⚪ Recrutement IA situationnel  ✅ **fait**

`evaluateHeroes` prenait toujours `KAEL`, sinon `ELARA`, sinon `VANCE`, sinon `NIX` — ordre fixe,
sans considérer faction ni situation. **Correctif** — nouveau `chooseHero(state, playerState)`
(`internal`, testable) qui score les héros abordables : **affinité** (`Hero.targetFaction`, poids
dominant), puis **posture** (guerre → Vance ; paix → Elara/Kael) et **état de la flotte** (unités
blessées → Nix) ; le coût départage. Cela donne enfin un usage mécanique à `targetFaction`
**sans** verrouiller le recrutement (H2 reste ouvert, Nix reste recrutable). **Tests** :
affinité, posture guerre/paix, flotte blessée, aucun héros si sans crédits.

---

## 4. Ce qui fonctionnait bien (première passe)

- **Bonus passifs au bon site** : Vance (`ATTACK_PERCENT` → `AttackCalculator`/`CombatResolver`),
  Elara (`INCOME_*` → `TurnManager`), Kael (`TECH_COST_PERCENT` → `CostCalculator`), Nix (soin →
  `TurnManager`), tous via `BonusRegistry`.
- **Aptitudes actives à usage unique** : `heroAbilitiesUsed` empêche la réutilisation ; l'UI
  grise le bouton « USED ».
- **Pipeline de recrutement** : `handleRecruitHero` vérifie l'affordabilité et le doublon ;
  débit immédiat au coût du registre.

---

# Seconde passe

> Reprise du même périmètre à partir du code tel qu'il est aujourd'hui, pour chercher ce qui reste —
> pas ce qui a déjà été corrigé ci-dessus. Les constats sont numérotés `HR*` pour ne pas se
> confondre avec les `H*` de la première passe, qui restent valides.
>
> ## ⚠️ Limite de cet audit
>
> Le proxy réseau refuse `dl.google.com` (403 sur le tunnel CONNECT), l'Android Gradle Plugin est
> donc inaccessible : ni build du dépôt, ni émulateur, ni partie jouée. **Aucun équilibrage n'a pu
> être observé en situation** — les seuils de déclenchement des aptitudes IA sont raisonnés, pas
> mesurés.
>
> Ce qui a pu être vérifié, en reconstruisant les modules purs dans un projet Gradle autonome :
> `:core:hex` + `:core:domain` + `:core:engine` compilent, **205 tests passent, 0 échec** — dont les
> 23 tests de héros ajoutés ici (182 avant ce lot). Les sources `:app` sont type-checkées sans erreur portant sur du
> code du dépôt ; la couche Compose n'est pas compilée.

## 6. Seconde passe — résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **HR1** | 🔴 **Majeur — ✅ corrigé** | IA | **L'IA n'utilisait aucune aptitude active** : quatre effets à usage unique réservés de fait au joueur humain |
| **HR2** | 🟠 **Moyen — ✅ corrigé** | Correction | L'aptitude de Kael **se consumait à vide** sans recherche en cours — ressource à usage unique perdue sur un clic |
| **HR3** | 🟠 **Moyen — ✅ corrigé** | Duplication | Les libellés d'aptitude vivaient en **trois exemplaires** (écran, moteur, nulle part d'autoritaire) — le défaut H1, déplacé |
| HR4 | 🟠 Moyen — ✅ corrigé | Cohérence | L'IA recrutait **hors du réducteur**, en écrivant crédits et roster à la main |
| HR5 | 🟡 Faible — ✅ corrigé | Fuite d'info | L'académie affichait la faction **active** : pendant la phase IA, le solde et le roster d'un adversaire |
| HR6 | 🟡 Faible — ✅ corrigé | Modèle | Le passif de Nix était **câblé en dur** dans `TurnManager`, invisible depuis `Hero.bonuses` |
| HR7 | 🟠 Moyen — ✅ corrigé | Tests | Une aptitude sur quatre testée ; **aucun** test sur les gardes, ni sur le cas qui gaspillait Kael |
| HR8 | 🟡 Faible — ✅ corrigé | UX | L'aptitude active était **invisible avant recrutement** : on achetait un héros sans voir la moitié de ce qu'il fait |
| **HR9** | 🟠 **Moyen — ouvert** | Équilibrage | **Trois factions jouables sur six n'ont aucun héros** à leur nom et paient ×2 partout |
| HR10 | 🟡 Design — ouvert | Règle | Les héros ne sont **pas exclusifs** : deux empires peuvent aligner le même commandant en même temps |

---

## 7. Seconde passe — corrigés

### HR1 — 🔴 L'IA ne se servait jamais de ses héros  ✅ **corrigé**

`UtilityEvaluator.evaluateHeroes` recrutait des héros. Et c'était tout : le mot `UseHeroAbility`
n'apparaissait nulle part dans l'IA. Les quatre aptitudes à usage unique étaient donc, dans les
faits, réservées au joueur humain :

| Héros | Aptitude | Ce que l'IA n'utilisait jamais |
|---|---|---|
| Vance | Frappe de Suppression | une **salve de flotte entière** supplémentaire |
| Elara | Convoi Commercial | **+80 crédits** — deux dreadnoughts |
| Nix | Refuge Stellaire | réparation de **la moitié de la coque** de toute la flotte |
| Kael | Prototype | une **technologie instantanée** |

L'avantage était unilatéral et croissait avec chaque héros recruté — d'autant que l'IA, elle,
*payait* ses héros au prix fort (×2 hors affinité).

**Correction.** Deux points d'entrée, parce que les aptitudes n'ont pas le même moment utile :

- **Phase stratégique** (`evaluateHeroAbilities`, avant la production) : Kael, Nix, Elara.
- **Après la phase tactique** (`executeAITurn`) : Vance. Rendre son tir à la flotte n'a de sens
  qu'une fois qu'elle a tiré ; l'aptitude est donc jouée après la boucle d'unités, suivie d'un
  second passage. `actUnit` revérifie `hasAttacked` et `hasMoved` reste vrai — c'est bien un second
  tir, pas un second déplacement.

Chaque aptitude a un critère de déclenchement, parce qu'une aptitude à usage unique jouée trop tôt
est définitivement perdue :

| Héros | Se déclenche quand |
|---|---|
| Kael | une recherche est en cours **et** il reste ≥ 2 tours (sauter la file doit valoir l'usage) |
| Nix | ≥ 2 unités **et** ≥ 2 d'entre elles sous la moitié de leur coque (ne pas réparer des éraflures) |
| Elara | solde < 15 crédits (le convoi débloque réellement un tour de production) |
| Vance | ≥ 2 unités ont tiré **et** au moins une a encore une cible à portée |

Ces seuils sont raisonnés, pas mesurés — voir la limite en tête de document. Ils sont regroupés en
constantes nommées et couverts par `AiHeroAbilityTest`, donc ajustables sans relire la logique.

### HR2 — 🟠 L'aptitude de Kael se consumait à vide  ✅ **corrigé**

```kotlin
val research = playerState.researchInProgress
val newPlayer = if (research != null) playerState.copy(…, heroAbilitiesUsed = markUsed)
                else playerState.copy(heroAbilitiesUsed = markUsed)   // ← consommée pour rien
val msg = if (research != null) "…completed instantly" else "KAEL: Prototype — no research in progress"
```

Sans recherche en cours, l'aptitude **se marquait utilisée** et n'affichait qu'un message
d'explication. Une ressource à usage unique par partie disparaissait sur un clic prématuré, sans
possibilité de revenir dessus. Le message reconnaissait explicitement l'inutilité de l'action tout
en la validant.

**Correction.** Le cas est désormais une erreur (`"No research in progress — Kael's prototype would
be wasted."`), traitée comme les autres refus du réducteur : rien n'est écrit, l'aptitude reste
disponible. C'est la même logique que le bouton `RECRUIT` désactivé sans crédits (H3) — on refuse
l'action impossible plutôt que de l'exécuter à perte.

### HR3 — 🟠 Les libellés d'aptitude en trois exemplaires  ✅ **corrigé**

H1 avait supprimé la liste de héros dupliquée dans l'écran. Le même défaut subsistait pour les
**aptitudes**, une couche plus bas :

| Où | Quoi |
|---|---|
| `HeroAcademyScreen` | une table `heroAbilityDescriptions` codée en dur |
| `handleUseHeroAbility` | les mêmes phrases, recopiées dans les notifications |
| `Hero` | **rien** — le modèle ne décrivait que le passif (`bonusDescription`) |

Rien ne tenait les deux copies d'accord, et surtout aucune n'était la référence : changer le
comportement de Nix dans le moteur n'aurait alerté personne sur les deux textes devenus faux.

**Correction.** `HeroAbility(name, description)` entre dans le modèle, et `Hero` gagne un champ
`ability`. L'écran et les notifications lisent le registre. `HeroAbilityTest` vérifie que chaque
héros expose son aptitude, donc en ajouter un sans la décrire casse le build.

### HR4 — 🟠 L'IA recrutait hors du réducteur  ✅ **corrigé**

```kotlin
newPlayerStates[faction] = playerState.copy(
    credits = playerState.credits - HeroCostCalculator.costFor(selectedHero, faction),
    recruitedHeroes = playerState.recruitedHeroes + selectedHero.id
)
```

Deux chemins pour une même règle : `handleRecruitHero` pour le joueur, cette écriture directe pour
l'IA. Les deux étaient d'accord aujourd'hui, mais toute règle ajoutée au recrutement — un plafond,
une exclusivité (HR10), un prérequis — aurait été contournée en silence par l'IA.

**Correction.** `evaluateHeroes` passe par `reduce(state, GameIntent.RecruitHero(id))`, comme
`evaluateDiplomacy` le fait déjà pour les relations. Le `reduce` était déjà disponible dans la
signature de `executeAITurn` ; il n'était simplement pas utilisé ici.

### HR5 — 🟡 L'académie montrait la faction active  ✅ **corrigé**

```kotlin
val playerState = gameState.playerStates[gameState.activeFaction]
```

Pendant la phase IA, `activeFaction` désigne un adversaire. L'écran affichait donc **son** solde de
crédits, **son** roster de héros, et calculait les prix de recrutement pour **sa** faction.

Ce n'est pas exploitable — `GameEngine.handleIntent` refuse toute intention tant que
`isAiThinking` (« AI is thinking, please wait ») — mais dans un jeu à brouillard de guerre,
l'économie d'un adversaire n'a pas à s'afficher.

**Correction.** L'écran lit `humanFaction`. Hors phase IA les deux coïncident, donc rien ne change
pour le joueur ; pendant la phase IA il voit ses propres données.

### HR6 — 🟡 Le passif de Nix hors du rail commun  ✅ **corrigé**

Trois héros sur quatre décrivaient leur passif comme un `BonusModifier` consommé par
`BonusRegistry` au site de calcul. Nix était l'exception : `Hero.bonuses` vide, et un `if` nommément
câblé dans `TurnManager`.

```kotlin
if (activePlayerState?.recruitedHeroes?.contains(HeroRegistry.NIX) == true) { … +1 PV … }
```

Conséquences : le soin était invisible depuis le modèle, et aucune techno, faction ou événement ne
pouvait en accorder — le mécanisme n'existait que pour ce héros précis.

**Correction.** Nouveau `BonusType.FLEET_REPAIR_PER_TURN`, porté par `Nix.bonuses`, sommé par
`BonusRegistry` dans `TurnManager` comme les onze autres types. Comportement identique (+1 PV/tour),
mais le soin devient un effet du jeu plutôt qu'un cas particulier — et il est désormais cumulable et
attribuable ailleurs. `bonusDescription` mentionne enfin la valeur (« +1 HP/turn »).

### HR7 — 🟠 Les aptitudes n'étaient presque pas testées  ✅ **corrigé**

Un seul test couvrait une aptitude (`nixAbilityRepairsHalfTheHullNotAllOfIt`, issu de H6). Vance,
Elara, Kael, et les trois gardes (héros non recruté, aptitude déjà utilisée, identifiant inconnu)
n'en avaient aucun — et le chemin non testé de Kael était précisément celui qui gaspillait
l'aptitude (HR2).

**Correction.** 23 tests ajoutés, tous dans `:core:engine` que la CI exécute :

- `HeroAbilityTest` (12) — les quatre aptitudes, les trois gardes, les bornes (pas de dépassement de
  coque, pas de tir rendu à l'ennemi, pas de déplacement rendu), et la cohérence modèle/moteur ;
- `AiHeroAbilityTest` (11) — les critères de déclenchement IA, et surtout les cas de
  **non**-déclenchement : recherche trop courte, flotte à peine éraflée, unité isolée, caisses
  pleines, pas de cible à portée, pas d'état de guerre.

### HR8 — 🟡 On achetait un héros sans voir son aptitude  ✅ **corrigé**

La carte de recrutement affichait `hero.bonusDescription` sous le titre « SIGNATURE ABILITY » — donc
le **passif**, sous un intitulé qui promettait l'aptitude. L'aptitude active n'apparaissait qu'une
fois le héros recruté, dans la liste « YOUR COMMANDERS ».

**Correction.** La carte distingue « PASSIF » et « APTITUDE — 1 FOIS PAR PARTIE », les deux lus
depuis le registre. La liste des commandants affiche aussi le passif, qui y manquait
symétriquement. Le bouton `USE ABILITY` passe par `enabled` au lieu d'un garde dans `onClick` :
une aptitude épuisée annonce enfin son état aux services d'accessibilité, comme `RECRUIT` le fait
depuis H3.

---

## 8. Seconde passe — ouverts

### HR9 — 🟠 La moitié des factions n'a pas de héros

Le casting compte quatre héros pour six factions jouables :

| Faction | Son héros | Ce qu'elle paie |
|---|---|---|
| DOMINION | Vance | 50 · puis 80 / 120 / 75 |
| TRADERS | Elara | 40 · puis 100 / 120 / 75 |
| SYNTH | Kael | 60 · puis 100 / 80 / 75 |
| **NOMADS** | — | 100 / 80 / 120 / 75 |
| **KAELEN** | — | 100 / 80 / 120 / 75 |
| **XYLAR** | — | 100 / 80 / 120 / 75 |

Trois factions sur six n'ont **aucun** tarif de base sur un héros de faction : leur seule option au
prix affiché est le mercenaire (Nix, 75). Sur les quatre héros, elles paient le double partout
ailleurs — soit **375 crédits** pour le roster complet contre **315 à 335** pour les trois autres,
40 à 60 crédits d'écart, l'équivalent d'un héros de faction ou d'un dreadnought et demi.

C'est la piste déjà notée dans `AUDIT_DECISIONS_GAMEPLAY.md` (« doter NOMADS, KAELEN et XYLAR de
leur propre héros »), et l'écart se chiffre maintenant. Trois héros supplémentaires sont un travail
de **contenu** — nom, allégeance, passif équilibré, aptitude à usage unique — pas une correction de
code, et je ne l'ai pas tranché unilatéralement. L'infrastructure est prête : ajouter un héros ne
demande qu'une entrée dans `ALL_HEROES`, et `HeroAbilityTest` refusera celle qui oublierait de
décrire son aptitude.

Alternative moins coûteuse si l'on ne veut pas écrire de contenu : faire de l'affinité une propriété
à plusieurs factions (`targetFactions: Set<Faction>`) et rattacher les héros existants à deux
factions chacun.

### HR10 — 🟡 Les héros ne sont pas exclusifs

Rien n'empêche DOMINION et SYNTH de recruter Vance tous les deux, dans la même partie, et de
bénéficier chacun de son +15 % d'attaque. Le registre est une liste de modèles, `recruitedHeroes`
un ensemble par joueur : il n'existe aucune notion de héros « pris ».

Deux conséquences : narrativement, un même commandant sert plusieurs empires à la fois ; et
mécaniquement, le recrutement n'est jamais une **course** — attendre ne coûte rien, personne ne peut
vous devancer.

Rendre les héros exclusifs serait une bonne mécanique de tension, mais c'est une décision de
conception qui touche l'équilibrage (le premier à payer bloque les autres, ce qui avantage la
faction la plus riche) et l'IA (il faudrait qu'elle anticipe). Signalé, non tranché.

---

## 9. Ce qui fonctionne bien (après seconde passe)

- **Les passifs sont appliqués au site de calcul**, via `BonusRegistry` : Vance dans
  `AttackCalculator`, Elara dans `IncomeCalculator`, Kael dans `CostCalculator`, Nix désormais dans
  `TurnManager` par le même chemin. Aucun n'est un « buff » stocké dans l'état.
- **L'affinité tarifée** (`HeroCostCalculator`) reste la bonne réponse à H2 : elle donne un sens
  mécanique à `targetFaction` sans amputer trois factions d'un pan du jeu.
- **Le choix de héros de l'IA** (`chooseHero`) est situationnel — affinité, posture de guerre, état
  de la flotte — et testé.
- **`heroAbilitiesUsed` est bien dans `PlayerState`**, donc sauvegardé : une aptitude consommée le
  reste après un rechargement.

## 10. Fichiers touchés — seconde passe

| Fichier | Rôle |
|---|---|
| `core/domain/…/models/Hero.kt` | `HeroAbility`, `Hero.ability`, passif de Nix en `BonusModifier` |
| `core/domain/…/models/BonusModifier.kt` | `BonusType.FLEET_REPAIR_PER_TURN` |
| `core/engine/…/TurnManager.kt` | Soin de flotte via `BonusRegistry` |
| `core/engine/…/IntentHandlers.kt` | Kael refuse au lieu de se gaspiller ; notifications lues du registre |
| `core/engine/…/UtilityEvaluator.kt` | Aptitudes IA, recrutement via le réducteur |
| `app/…/ui/screens/HeroAcademyScreen.kt` | Aptitude visible avant achat, faction humaine, bouton désactivé |
| `core/engine/src/test/…/HeroAbilityTest.kt` | 12 tests (nouveau) |
| `core/engine/src/test/…/AiHeroAbilityTest.kt` | 11 tests (nouveau) |
