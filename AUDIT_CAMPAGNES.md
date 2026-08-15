# Audit & suggestions — Campagnes

> Portée : `CampaignConfig` (`CampaignMission`, `CampaignObjective`, `CampaignRegistry`),
> `CampaignState` / `CampaignProgress`, `GloryPerk` / `GloryRegistry`, la branche campagne de
> `VictoryChecker`, `handleStartCampaign`, `CampaignProgressStore`, `GameViewModel` et
> `CampaignSelectionScreen`.
>
> ⚠️ **Rien n'a été compilé ni exécuté localement** : le proxy bloque `dl.google.com` (403), donc AGP
> est inaccessible et Gradle ne démarre pas. Les correctifs sont en `:core:*` et couverts par des
> tests JVM exercés en CI ; les retouches d'affichage sont revues statiquement et vérifiées à la
> compilation seulement (`:app:assembleDebug` en CI). **Aucun des nombres d'équilibrage n'a été
> joué** — voir §4.

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

## 3. Corrigé — la persistance et l'économie de gloire (P1)

Ces deux points étaient signalés comme limites connues dans la première version de cet audit. Ils
sont désormais traités.

### P1.1 — Un stockage durable, séparé de l'auto-sauvegarde

Le diagnostic initial : la progression vivait dans `GameState.campaignState`, donc dans les
sauvegardes — mais l'auto-sauvegarde **ignore volontairement un état terminal** (correctif G3 de
l'audit « gestion des parties »). Autrement dit, **le seul instant qui produit de la progression —
gagner une mission — était précisément celui où rien n'était écrit.** Terminer une mission puis
quitter perdait le résultat.

`CampaignProgressStore` écrit désormais `CampaignProgress` (missions terminées + gloire) dans son
propre fichier, `saves/campaign_progress.json`, à chaque changement. Écriture par fichier temporaire
puis déplacement atomique, comme les sauvegardes : un plantage en cours d'écriture ne peut pas
remplacer un enregistrement valide par un demi-fichier.

Trois décisions valent d'être explicitées :

- **`CampaignProgress` n'est pas `CampaignState`.** `activeMissionId` en est volontairement absent :
  la mission en cours appartient à une partie, pas au palmarès du joueur. La restaurer au démarrage
  ressusciterait une mission sans plateau.
- **Un enregistrement corrompu n'est pas mis en quarantaine**, contrairement à une sauvegarde. Il n'y
  a rien à récupérer et aucun choix à proposer : il est lu comme vide et écrasé à la prochaine
  écriture. Le coût d'une erreur est un compteur de médailles, pas une partie en cours.
- **Au chargement d'une sauvegarde, le magasin fait autorité.** Un fichier écrit avant la dernière
  victoire porte un palmarès plus ancien, et une gloire depuis dépensée. Les missions terminées sont
  donc **unionnées** (une mission gagnée le reste, quel que soit le support qui s'en souvient) alors
  que la gloire vient du seul magasin — un maximum rembourserait silencieusement les perks achetés.

Un piège d'ordonnancement a dû être traité : les intents passent par un canal, donc les premières
émissions d'état portent encore un palmarès vide. Le miroir vers le disque attend que la restauration
ait atterri (`engine.state.first { … == stored }`) avant de commencer à écrire — sans quoi la
première émission aurait écrasé le fichier tout juste lu.

### P1.2 — La gloire devient une monnaie

C'était la suggestion la plus structurante de la version précédente de cet audit : le champ existait,
était reporté d'une mission à l'autre, et ne servait à rien.

- **Gagner** : `CampaignMission.gloryReward` (2 / 3 / 4 pour les trois missions), versé **à la
  première réussite seulement**. Sans ce garde-fou, la mission la plus courte devient une ferme à
  perks : rejouer, encaisser, rejouer.
- **Dépenser** : `GloryRegistry` propose trois perks achetés avant le lancement — coffre de guerre
  (+150 crédits), coques prototypes (`tech_hull_plating` acquise) et éclaireurs avancés
  (`tech_deep_scanners` acquise).

Les effets sont **des données, pas du code** : un perk déclare ce qu'il accorde, et
`handleStartCampaign` applique chaque champ de la même façon pour tous. Un `when (perk.id)` aurait
été plus court à écrire et aurait pris un trou silencieux au premier ajout — exactement le défaut qui
rendait `CAPTURE_SPECIFIC_PLANET` ingagnable.

Deux règles assumées :

- **Tout ou rien au lancement.** Un chargement inabordable ou un perk inconnu fait échouer le
  lancement sans rien modifier. L'échec dangereux serait le demi-lancement : gloire débitée, mission
  démarrée, bonus manquants.
- **Dépensé est dépensé, victoire ou défaite.** Rembourser sur échec rendrait chaque perk gratuit à
  essayer, et choisir quoi emporter est l'essentiel de ce qui fait de la gloire une décision.

## 4. Ce qui n'a pas pu être vérifié

**L'équilibrage n'a toujours pas été joué**, et la surface non jouée vient de s'élargir :

| Nombre | Valeur | Statut |
|--------|--------|--------|
| Échéances missions 2 et 3 | 40 tours | estimation |
| Bonus ennemi mission 3 | 60 crédits | estimation |
| Récompenses de gloire | 2 / 3 / 4 | **estimation, non jouée** |
| Coûts des perks | 2 / 3 / 3 | **estimation, non jouée** |

« The Kaelen Gate » demande de traverser un diamètre de carte (distance 10 entre les deux capitales),
de réduire une planète de niveau 2 à 0 par le siège, puis de la capturer. C'est plausible en 40 tours,
mais je ne peux ni le mesurer ni le ressentir : ni build, ni émulateur ici.

Le rapport gains/coûts de la gloire est le plus fragile des quatre : avec 9 points gagnables au total
et 8 de perks disponibles, un joueur qui termine tout peut presque tout acheter. C'est délibérément
généreux — une monnaie qu'on n'atteint jamais ne se ressent pas — mais **c'est un premier jet, pas un
réglage.** La façon la plus simple de le resserrer, si le jeu le demande, est d'augmenter les coûts,
pas de baisser les gains : un gain visible motive, un coût élevé se négocie.

L'écran de sélection lui-même n'a été relu que statiquement. La liste de perks est scrollable et le
bouton de lancement est hors de la zone défilante, mais **je n'ai pas pu regarder cet écran**.

## 5. Suggestions pour la suite (non implémentées)

Par ordre de rapport valeur/effort :

1. **Objectifs composites.** Une mission = un objectif plafonne vite la variété. Passer `objective`
   à une **liste** avec un mode ET/OU permettrait « survivre 20 tours **et** tenir 3 mondes », et des
   objectifs secondaires optionnels — qui donneraient une seconde source de gloire, dosable.
2. **Conditions de départ scriptées.** Aujourd'hui une mission ne fait varier que carte, faction,
   ennemi, bonus et échéance. Les leviers les plus rentables, dans l'ordre : flotte de départ
   imposée, technologies déjà débloquées, crédits initiaux, planètes pré-possédées. C'est ce qui
   permet des *tempos* différents — un raid à trois vaisseaux sans économie, un siège avec un unique
   dreadnought. Les perks de gloire ouvrent déjà deux de ces leviers (crédits, technos) : la
   structure de données est là, il reste à la mettre entre les mains du concepteur de mission.
3. **Des perks qui ne soient pas que des bonus chiffrés.** Les trois premiers sont volontairement
   ternes — ils valident l'économie sans rien inventer. Un héros de départ, une unité offerte ou une
   carte partiellement révélée changeraient une manière de jouer plutôt qu'un total.
4. **Événements scriptés.** `GalacticEvent` est purement aléatoire. Déclencher un événement à un tour
   donné (« au tour 8, tempête ionique ») créerait des pics dramatiques reproductibles sans nouveau
   moteur — le système d'effets existe déjà.
5. **Narration.** Il n'y a qu'un champ `description`. Un texte d'introduction et un texte de
   conclusion (victoire / défaite) par mission coûtent presque rien et changent radicalement la
   perception d'une campagne.
6. **Déverrouillage séquentiel.** Maintenant que `completedMissions` est peuplé *et durable*, un
   champ `requiresMissionId` permettrait un ordre de progression — l'écran de sélection sait déjà
   quelles missions sont terminées.

## 6. Ce qui fonctionnait déjà bien

- **La guerre scriptée est forcée** : `handleStartCampaign` met le joueur et l'ennemi en `WAR`, sans
  quoi l'IA — qui n'engage que les factions en guerre — resterait passive et la mission serait vide.
  Le commentaire d'origine explique correctement pourquoi.
- **Détection de défaite** : perte de toutes les unités **et** de toutes les planètes met fin à la
  mission, indépendamment de l'objectif.
- **Priorité de la campagne** : les conditions de victoire standard sont court-circuitées en mission,
  ce qui évite qu'une victoire économique fortuite ne termine un scénario scripté.
- **Taille de carte par mission** : `mapSize` est respecté depuis l'audit des technologies (A4).
