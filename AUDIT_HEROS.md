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
| H4 | ⚪ Duplication | Revenu | La prévisualisation de revenu (`TacticalMapScreen`) **recalcule à la main** le bonus d'Elara (+10 %+2) au lieu d'un calculateur partagé avec `TurnManager` |
| H5 | ⚪ Comportement | IA | `evaluateHeroes` recrute dans un ordre fixe (`KAEL>ELARA>VANCE>NIX`), sans tenir compte de la faction ni de la posture |
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
- **H4 — Duplication du calcul de revenu.** L'aperçu de revenu (`TacticalMapScreen.incomePerTurn`)
  réplique à la main le bonus d'Elara (`+10 % +2`) et toute la formule de revenu, en parallèle de
  `TurnManager` (via `BonusRegistry`). Même risque de divergence que la prévisualisation de combat
  (corrigée par `AttackCalculator`). Piste : extraire un `IncomeCalculator` partagé.
- **H5 — Recrutement IA rigide.** `evaluateHeroes` prend toujours `KAEL`, sinon `ELARA`, sinon
  `VANCE`, sinon `NIX`, sans considérer la faction (H2) ni la posture (comme la recherche T6).
  Si H2 est verrouillé, l'IA devra aussi respecter l'affinité.
- **H6 — Puissance de Nix.** Recrutable par tous, Nix cumule soin passif (+1 PV/tour,
  `TurnManager`) et soin complet actif — combo fort, à surveiller à l'équilibrage.

---

## 4. Ce qui fonctionne bien

- **Bonus passifs au bon site** : Vance (`ATTACK_PERCENT` → `AttackCalculator`/`CombatResolver`),
  Elara (`INCOME_*` → `TurnManager`), Kael (`TECH_COST_PERCENT` → `CostCalculator`), Nix (soin →
  `TurnManager`), tous via `BonusRegistry`.
- **Aptitudes actives à usage unique** : `heroAbilitiesUsed` empêche la réutilisation ; l'UI
  grise le bouton « USED ».
- **Pipeline de recrutement** : `handleRecruitHero` vérifie l'affordabilité et le doublon ;
  débit immédiat au coût du registre.
