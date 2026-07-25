# Audit — Gestion des héros

> Portée : modèle (`Hero`/`HeroRegistry`), recrutement (`handleRecruitHero`), aptitudes actives
> (`handleUseHeroAbility`), bonus passifs (`BonusRegistry`, `TurnManager`, `CombatResolver`,
> `CostCalculator`), recrutement IA (`UtilityEvaluator.evaluateHeroes`) et l'écran
> `HeroAcademyScreen`.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Les correctifs moteur/données sont couverts par des tests JVM (CI) ; l'UI est revue statiquement.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **H1** | 🔴 **Majeur — ✅ corrigé** | Cohérence UX | L'académie codait **sa propre liste de héros** (coûts 1200–2000, factions/​noms erronés) divergente de `HeroRegistry` (coûts **40–75**) → le prix affiché était fictif |
| H2 | 🟡 Design | Recrutement | `Hero.targetFaction` **jamais vérifié** : toute faction recrute tout héros. Nix = `ANCIENT_NPC` → verrouillage strict impossible sans réattribuer sa faction |
| H3 | 🟡 Faible — ✅ corrigé | UX | Bouton `RECRUIT` **cliquable sans crédits** → erreur moteur au lieu d'un état désactivé |
| H4 | ⚪ Duplication — ✅ fait | Revenu | `IncomeCalculator` partagé : l'aperçu HUD affiche enfin le revenu réellement versé |
| H5 | ⚪ Comportement — ✅ fait | IA | Recrutement IA scoré : affinité de faction, posture (guerre/paix) et état de la flotte |
| H6 | 🟡 Design | Équilibrage | Nix (héros `ANCIENT_NPC`) recrutable par **tous** cumule un **passif** (+1 PV/tour) et un **actif** (soin complet) |

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

## 4. Ce qui fonctionne bien

- **Bonus passifs au bon site** : Vance (`ATTACK_PERCENT` → `AttackCalculator`/`CombatResolver`),
  Elara (`INCOME_*` → `TurnManager`), Kael (`TECH_COST_PERCENT` → `CostCalculator`), Nix (soin →
  `TurnManager`), tous via `BonusRegistry`.
- **Aptitudes actives à usage unique** : `heroAbilitiesUsed` empêche la réutilisation ; l'UI
  grise le bouton « USED ».
- **Pipeline de recrutement** : `handleRecruitHero` vérifie l'affordabilité et le doublon ;
  débit immédiat au coût du registre.
