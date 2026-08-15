# Audit & suggestions — Campagnes

> Portée : `CampaignConfig` (`CampaignMission`, `CampaignObjective`, `CampaignRegistry`),
> `CampaignState`, la branche campagne de `VictoryChecker`, `handleStartCampaign`,
> `GameViewModel.startCampaignMission` et `CampaignSelectionScreen`.
>
> ⚠️ **Tests non exécutés localement** : proxy bloque `dl.google.com` (403) → AGP inaccessible.
> Les correctifs sont en `:core:*` et couverts par des tests JVM exercés en CI ; la retouche
> d'affichage (mission terminée) est revue statiquement. **L'équilibrage des échéances ajoutées
> n'a pas pu être joué** — voir §4.

## 1. Le constat de départ : un échafaudage, pas une campagne

Trois champs étaient **déclarés et jamais utilisés**, et un type d'objectif rendait toute mission qui
l'employait **impossible à gagner**. Le système donnait l'illusion d'être plus complet qu'il ne
l'était :

| Élément | État avant | Conséquence |
|---------|-----------|-------------|
| `CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET` | déclaré, aucune implémentation → `else -> false` | mission **ingagnable**, sans erreur ni avertissement |
| `CampaignState.completedMissions` | déclaré, jamais écrit ni lu | **aucune progression** : finir une mission ne laissait aucune trace |
| `CampaignState.gloryPoints` | déclaré, jamais écrit ni lu | aucune récompense méta |
| `CampaignMission.enemyBonusCredits` | déclaré (100 sur la mission 2), jamais appliqué | **le seul levier de difficulté par mission était inerte** |
| Limite de tours | inexistante | la branche campagne sort avant la règle des 100 tours → **partie sans fin** si l'objectif devient inatteignable |

## 2. Corrigé — les fondations (P0)

### P0.1 — `CAPTURE_SPECIFIC_PLANET` implémenté

L'objectif est désormais satisfait quand la tuile visée appartient à la faction du joueur. La cible
est portée par `targetString` (champ qui existait déjà, mais n'était jusqu'ici que **affiché**), au
format `"q,r"` — la troisième coordonnée cube est déduite.

`parseTargetCoord` est **tolérant par conception** : une faute de frappe dans les données de mission
renvoie `null` et laisse l'objectif non rempli, plutôt que de lever une exception au milieu d'une
partie. Le risque de faute silencieuse est reporté sur un test
(`everyCaptureObjectiveCarriesAParsableTarget`) qui échoue si une mission de capture porte une cible
illisible.

Une troisième mission exerce enfin ce chemin — **« The Kaelen Gate »**. Sa cible est le monde de
départ de KAELEN : `spawnPointsFor(5)` attribue l'index 4, soit `(-5,5,0)`, à cette faction, donc la
planète existe toujours et est tenue par l'ennemi dès le premier tour.

> ⚠️ Piège évité de justesse : j'avais d'abord écrit `"5,-5"`, qui est le spawn de **TRADERS**, pas
> de KAELEN — la description de la mission aurait été fausse et l'objectif aurait visé la mauvaise
> faction. C'est exactement le genre d'erreur que le test d'intégrité ne peut pas attraper : il
> vérifie que la cible est *lisible*, pas qu'elle est *pertinente*.

### P0.2 — Progression persistée

`completedMissions` est désormais écrit à la réussite d'une mission (dans
`GameEngine.checkVictoryConditions`, quand le vainqueur est la faction du joueur), et lu par l'écran
de sélection, qui marque les missions terminées.

Un point non évident a dû être traité : lancer une mission passe par `StartNewGameWithSize`, donc par
`createInitialState`, qui construit un `GameState` **neuf** — dont le `campaignState` par défaut est
vide. La progression aurait donc été **effacée au lancement de la mission suivante**. Un helper
(`keepingCampaignProgress`) reporte les missions terminées et la gloire à travers la remise à zéro du
plateau, en vidant seulement `activeMissionId`.

### P0.3 — `enemyBonusCredits` appliqué

Le bonus est versé au trésor de l'ennemi scripté dans `handleStartCampaign`, là où la relation de
guerre est déjà forcée. Le levier de difficulté par mission fonctionne enfin.

### P0.4 — Échéance de mission

Nouveau champ `CampaignMission.turnLimit` (0 = aucune). Passé le délai, la mission est un échec et
l'ennemi scripté l'emporte. Cela ferme le cas de la partie sans fin **et** ajoute de la tension.
Un test garde-fou vérifie qu'une mission `SURVIVE_TURNS` ne se voit pas imposer une échéance
antérieure à son propre objectif de survie — combinaison qui la rendrait ingagnable.

## 3. Limite connue de la persistance

La progression vit dans `GameState.campaignState`, donc dans les sauvegardes. Mais l'auto-sauvegarde
**ignore volontairement un état terminal** (correctif G3 de l'audit « gestion des parties », qui
évite de reprendre une partie déjà finie). Conséquence :

> **Terminer une mission puis quitter immédiatement l'application ne conserve pas le résultat.**
> La progression tient pendant toute la session, et se retrouve dans une sauvegarde faite *en cours*
> de mission — mais elle n'est pas durable au sens strict.

Une persistance correcte demande un petit stockage dédié à la progression de campagne, **séparé** de
l'auto-sauvegarde de tour (un fichier JSON de quelques champs via `SaveManager`, ou les
`SharedPreferences`). Je ne l'ai pas fait : c'est de la nouvelle infrastructure, et cela méritait
d'être décidé plutôt que glissé dans un correctif.

## 4. Ce qui n'a pas pu être vérifié

Les valeurs d'échéance (40 tours pour les missions 2 et 3) et le bonus ennemi de la mission 3 (60
crédits) sont des **estimations non jouées**. « The Kaelen Gate » demande de traverser un diamètre de
carte (distance 10 entre les deux capitales), de réduire une planète de niveau 2 à 0 par le siège,
puis de la capturer. C'est plausible en 40 tours, mais je ne peux ni le mesurer ni le ressentir : ni
build, ni émulateur ici. **À jouer avant de considérer ces trois nombres comme réglés.**

## 5. Suggestions pour la suite (non implémentées)

Par ordre de rapport valeur/effort :

1. **Objectifs composites.** Une mission = un objectif plafonne vite la variété. Passer `objective`
   à une **liste** avec un mode ET/OU permettrait « survivre 20 tours **et** tenir 3 mondes », et des
   objectifs secondaires optionnels — qui donneraient enfin un usage aux `gloryPoints`.
2. **Rendre `gloryPoints` dépensable.** Le champ existe et est désormais reporté d'une mission à
   l'autre. En faire une monnaie méta (héros de départ, techno offerte, crédits initiaux) transforme
   une suite de scénarios en véritable campagne. C'est le chaînon manquant le plus structurant.
3. **Conditions de départ scriptées.** Aujourd'hui une mission ne fait varier que carte, faction,
   ennemi, bonus et échéance. Les leviers les plus rentables, dans l'ordre : flotte de départ
   imposée, technologies déjà débloquées, crédits initiaux, planètes pré-possédées. C'est ce qui
   permet des *tempos* différents — un raid à trois vaisseaux sans économie, un siège avec un unique
   dreadnought.
4. **Événements scriptés.** `GalacticEvent` est purement aléatoire. Déclencher un événement à un tour
   donné (« au tour 8, tempête ionique ») créerait des pics dramatiques reproductibles sans nouveau
   moteur — le système d'effets existe déjà.
5. **Narration.** Il n'y a qu'un champ `description`. Un texte d'introduction et un texte de
   conclusion (victoire / défaite) par mission coûtent presque rien et changent radicalement la
   perception d'une campagne.
6. **Déverrouillage séquentiel.** Maintenant que `completedMissions` est peuplé, un champ
   `requiresMissionId` permettrait un ordre de progression — l'écran de sélection sait déjà quelles
   missions sont terminées.

## 6. Ce qui fonctionnait déjà bien

- **La guerre scriptée est forcée** : `handleStartCampaign` met le joueur et l'ennemi en `WAR`, sans
  quoi l'IA — qui n'engage que les factions en guerre — resterait passive et la mission serait vide.
  Le commentaire d'origine explique correctement pourquoi.
- **Détection de défaite** : perte de toutes les unités **et** de toutes les planètes met fin à la
  mission, indépendamment de l'objectif.
- **Priorité de la campagne** : les conditions de victoire standard sont court-circuitées en mission,
  ce qui évite qu'une victoire économique fortuite ne termine un scénario scripté.
- **Taille de carte par mission** : `mapSize` est respecté depuis l'audit des technologies (A4).
