# Audit — Diplomatie & relations

> Portée : `DiplomaticRelation`, `PlayerState.relations`, `handleChangeRelation`, la diplomatie IA
> (`UtilityEvaluator.evaluateDiplomacy`), l'usage des relations dans le ciblage
> (`enemyUnitsOf` / `enemyPlanetsOf`) et l'écran `DiplomacyIntelScreen`.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Tout le code corrigé est en `:core:engine` (hors filtrage d'affichage), donc couvert par la CI.

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **D1** | 🔴 **Majeur — ✅ corrigé** | Exploit | L'alliance était **imposée sans consentement** : un joueur pouvait se déclarer allié de tous et devenir **intouchable** |
| **D2** | 🟠 **Moyen — ✅ corrigé** | Validation | `handleChangeRelation` ne validait **rien** : relation avec soi-même possible, ou avec une faction qui ne joue pas |
| D3 | 🟡 Faible — ✅ corrigé | UX | L'écran listait `ANCIENT_NPC` comme partenaire diplomatique, alors que toute relation avec lui est sans effet |
| D4 | 💡 Design | Équilibrage | Aucun **coût ni délai** pour changer de relation |
| D5 | 💡 Design | Contenu | L'alliance n'apporte **que la non-agression** : ni vision partagée, ni échange, ni coût de trahison |

---

## 2. Bugs corrigés

### D1 — 🔴 Alliance imposée : immunité gratuite  ✅ **corrigé**

`handleChangeRelation` écrivait la relation demandée sur **les deux camps**, sans que la cible ait
son mot à dire :

```kotlin
newPlayerStates[state.activeFaction] = playerState.copy(relations = … it[intent.targetFaction] = intent.newRelation)
val targetState = newPlayerStates[intent.targetFaction]
if (targetState != null) {
    newPlayerStates[intent.targetFaction] = targetState.copy(relations = … it[state.activeFaction] = intent.newRelation)
}
```

Or le ciblage de l'IA ne retient que les factions **en guerre** avec elle :

```kotlin
// UtilityEvaluator.enemyUnitsOf
rel?.get(it.faction) == DiplomaticRelation.WAR || it.faction == Faction.ANCIENT_NPC
```

…et `enemyPlanetsOf` exclut les mondes d'un allié. Conséquence : l'écran de diplomatie proposait un
bouton « PROPOSER UNE ALLIANCE » qui **imposait** l'alliance, et le joueur pouvait, dès le premier
tour et gratuitement, s'allier avec les cinq IA — devenant **définitivement inattaquable**. Pire :
la branche « déclarer la guerre » de l'IA exige `currentRelation != ALLIANCE`, donc aucune IA ne
pouvait jamais revenir sur cette alliance forcée.

**Correctif** — nouveau `DiplomacyEvaluator.wouldAccept`, avec une règle asymétrique qui correspond
à la réalité de la chose :

| Proposition | Consentement |
|-------------|--------------|
| **Guerre** | jamais requis — on n'a pas besoin d'autorisation pour attaquer |
| **Alliance** | acceptée si le proposant **pèse réellement** (≥ 75 % de la puissance de la cible) **ou** si la cible est déjà en guerre ailleurs et a besoin d'amis |
| **Paix (neutre)** | acceptée sauf si la cible **domine largement** (> 1,5×) et préfère poursuivre l'avantage |

La puissance (`credits` + PV de la flotte) est celle qu'utilisait déjà l'IA ; `evaluateDiplomacy`
s'appuie désormais sur la **même** fonction, de sorte que l'IA raisonne exactement comme son
interlocuteur évaluera sa proposition.

**Tests** — `DiplomacyEvaluatorTest` (guerre sans consentement, alliance refusée d'un proposant
insignifiant, acceptée d'un pair, acceptée par une faction en guerre ailleurs, cessez-le-feu refusé
par qui l'emporte) et, côté réducteur, `allianceCannotBeImposedOnAnUnwillingFaction` et
`warIsAlwaysUnilateral`.

### D2 — 🟠 Aucune validation de la cible  ✅ **corrigé**

Le handler acceptait n'importe quelle faction, y compris :

- **soi-même** — `relations[maFaction] = WAR` polluait l'état, et cela n'était pas anodin :
  `chooseResearchTech` et `chooseHero` déterminent la posture via
  `relations.values.any { it == WAR }`, donc une auto-déclaration de guerre aurait fait basculer
  l'IA en posture militaire sans le moindre ennemi ;
- une faction **sans `PlayerState`** (`ANCIENT_NPC`) — la relation était inscrite d'un seul côté,
  sans effet puisque le NPC est hostile par construction.

**Correctif** — les deux cas sont rejetés avec un message explicite.
**Test** — `aFactionCannotSetARelationWithItself`.

### D3 — 🟡 Le NPC listé comme partenaire diplomatique  ✅ **corrigé**

`DiplomacyIntelScreen` itérait `Faction.values()`, affichant donc `ANCIENT_NPC` avec ses boutons
« alliance » et « guerre » — des actions sans aucun effet réel. La liste ne retient plus que les
factions dotées d'un `PlayerState`.

---

## 3. Signalés, non modifiés

- **D4 — Diplomatie gratuite et instantanée.** Changer de relation ne coûte ni crédits ni tour, et
  rien n'empêche d'enchaîner les revirements. Le consentement (D1) ferme l'essentiel de l'abus —
  on ne peut plus s'acheter la paix d'un simple clic quand on est en train de perdre — mais un coût
  diplomatique ou un délai de carence donnerait du poids à l'engagement. *Ajouterait de l'état
  (dernier tour de changement), donc laissé ouvert.*
- **D5 — L'alliance n'apporte que la non-agression.** Pas de vision partagée, pas d'accord
  commercial, pas de pénalité en cas de trahison : rompre une alliance pour attaquer par surprise
  ne coûte rien. Un malus de réputation, ou un bonus concret à l'alliance, étofferait nettement cet
  axe du jeu.

## 4. Ce qui fonctionne bien

- **Relations bilatérales cohérentes** : un changement accepté est inscrit des deux côtés, jamais
  seulement chez le proposant — l'état diplomatique ne peut pas devenir asymétrique.
- **Ciblage IA fondé sur les relations** : `enemyUnitsOf` / `enemyPlanetsOf` respectent les
  alliances (on n'attaque ni les unités ni les mondes d'un allié) et traitent les Anciens comme
  hostiles en toutes circonstances.
- **Diplomatie IA lisible en trois règles** : s'allier à nettement plus fort, déclarer la guerre à
  nettement plus faible, demander la paix quand l'adversaire est deux fois supérieur — les trois
  branches sont atteignables et ordonnées correctement.
- **Campagne cohérente** : `handleStartCampaign` force l'état de guerre entre le joueur et l'ennemi
  scripté, sans quoi l'IA — qui n'engage que les factions en guerre — resterait passive et la
  mission serait vide.
