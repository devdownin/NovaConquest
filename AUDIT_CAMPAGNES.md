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
> joué** — voir §6.

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

- **Gagner** : `CampaignMission.gloryReward`, versé **à la première réussite seulement**. Sans ce garde-fou, la mission la plus courte devient une ferme à
  perks : rejouer, encaisser, rejouer.
- **Dépenser** : `GloryRegistry` propose des perks achetés avant le lancement. Les trois premiers
  sont volontairement chiffrés — coffre de guerre (+150 crédits), coques prototypes
  (`tech_hull_plating`) et éclaireurs avancés (`tech_deep_scanners`) — et valident l'économie sans
  rien inventer. Les trois suivants changent la manière de jouer : voir §5.

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

## 4. Corrigé — objectifs composites (P2)

C'était la première suggestion de la version précédente : une mission = un objectif plafonne vite la
variété, et rien ne permettait « survivre **et** tenir un trésor », ni « bloquer l'ennemi **ou**
l'éliminer ».

`CampaignMission.objective` devient `objectives: List<CampaignObjective>` combinée par
`objectiveMode` :

| Mode | Sens | Ce que ça permet |
|------|------|------------------|
| `ALL` | tous requis | une **check-list** — la mission a plusieurs fronts à tenir en même temps |
| `ANY` | un seul suffit | des **routes parallèles** — le joueur choisit sa stratégie |

S'y ajoute `bonusObjectives`, des objectifs **facultatifs** qui ne conditionnent jamais la victoire
et paient leur propre gloire. Ils donnent enfin une seconde source de gloire, dosable finement,
là où le seul levier était la récompense de mission.

### Deux pièges traités

**`all` sur une liste vide vaut `true`.** Une mission sans objectif déclaré aurait été gagnée au
premier tour, en silence — exactement le genre de défaut que cet audit a passé son temps à corriger.
Le garde-fou est doublé : `VictoryChecker` exige explicitement une liste non vide, et un test vérifie
la donnée. Le premier attrape la règle, le second attrape le contenu du registre.

**Une seule fonction juge un objectif.** `VictoryChecker.isObjectiveMet` est le seul endroit où un
`CampaignObjectiveType` devient une règle : objectifs requis et facultatifs y passent tous. Sans
cela, un nouveau type pouvait signifier une chose pour la victoire et une autre pour la prime — la
divergence UI/moteur qui revient dans presque tous les audits de ce dépôt, transposée à l'intérieur
du moteur.

### Quand les objectifs facultatifs sont évalués

**Au moment de la victoire, pas suivis pendant la partie.** « Détenir 250 crédits » veut dire les
détenir à la fin, pas être passé par 250 à un moment. C'est cohérent avec la façon dont les objectifs
principaux lisent déjà l'état courant, et cela évite d'ajouter au `GameState` un suivi qu'il faudrait
sérialiser, défaulter et migrer pour une récompense vue une fois. C'est une limite assumée : un
objectif du type « ne jamais perdre d'unité » demanderait ce suivi.

### Les missions

- **mission_1** gagne un objectif facultatif (tenir 120 crédits, +1 gloire) — enseigne l'idée sans
  punir qui l'ignore.
- **mission_2** passe en `ANY` : amasser 500 crédits **ou** éliminer le rival qui allait les prendre.
- **mission_3** gagne un objectif facultatif (250 crédits, +2 gloire) : prendre la porte vite vaut
  mieux que la prendre à tout prix.
- **mission_4 « Twin Anvils »** (nouvelle) exerce `ALL` : survivre 20 tours **et** ne pas laisser le
  trésor sous 300. Elle ne porte volontairement **aucune coordonnée** — une mauvaise case est la
  seule erreur de donnée que les tests d'intégrité ne peuvent pas attraper, et cette mission existe
  pour prouver la logique de combinaison, pas la carte.

## 5. Corrigé — des perks qui changent la manière de jouer (P3)

Les trois premiers perks étaient volontairement ternes : ils validaient l'économie sans rien
inventer. Un catalogue qui ne contient que des nombres fait de la gloire une seconde monnaie, pas un
choix. Trois entrées s'y ajoutent, chacune faisant ce que ni crédits ni technos ne peuvent faire :

| Perk | Coût | Ce qu'il change |
|------|------|-----------------|
| **Vanguard Cruiser** | 3 | Un croiseur **en service au tour 1**. Ce n'est pas « les crédits pour l'acheter » : la production prend des tours, pas ce perk. Il change le tempo d'ouverture. |
| **Captured Star Charts** | 2 | La carte est **explorée** dès le départ — mais pas *visible*. Le terrain et les mondes sont connus, les flottes ennemies non. Cela achète de la **planification**, pas du renseignement. |
| **The Seer's Contract** | 4 | Nix sous contrat au tour 1 : +1 PV/tour sur toute la flotte. Change entièrement l'arithmétique d'attrition. |

Trois points de conception valent d'être signalés :

**Explored ≠ visible.** C'est tout le sel du perk de carte, et c'est aussi ce qui l'empêche d'être
trop fort. `updateVision` n'ajoute jamais que des hexs à `exploredHexes`, donc l'amorçage survit à
tous les recalculs ultérieurs, et le brouillard continue de masquer les unités. Un test vérifie
précisément cette distinction : toute la carte explorée, **rien** de visible (la vision ne vient que
des unités, et le plateau n'en a encore aucune).

**Un seul propriétaire pour la règle de placement.** Le croiseur offert et la file de construction
doivent s'accorder sur ce qu'est une case libre. `UnitPlacement.freeHexNear` est extrait de
`TurnManager` et partagé : une règle qui dirait « la planète, sinon une case voisine » ici et quelque
chose de subtilement différent là-bas est la façon dont un perk finirait par faire apparaître un
vaisseau dans un champ d'astéroïdes que le chantier aurait refusé.

**Une capitale encerclée ne rend pas la gloire.** Si aucune case n'est libre, le vaisseau n'apparaît
pas et le perk reste débité. C'est délibéré : refuser le lancement à ce stade — plateau déjà
construit — serait pire. Le cas est documenté et testé plutôt que laissé à découvrir en jeu.

**Seuls les mercenaires sont offrables.** Un champion de faction au service de la mission d'un rival
contredirait la tarification par affinité sur laquelle repose tout le système de héros. Nix est le
seul mercenaire du roster, et un test garde-fou refuse tout perk qui offrirait un héros loyal à une
faction.

### Effet sur l'équilibre

Le catalogue passe de 8 à **17 points d'achats pour 16 points gagnables** : il n'est plus possible de
tout acheter, donc **choisir redevient un arbitrage**. C'était le déséquilibre signalé à la fin du
lot précédent, et c'est la bonne façon de le corriger — étoffer l'offre plutôt que renchérir ce qui
existait.

## 6. Ce qui n'a pas pu être vérifié

**L'équilibrage n'a toujours pas été joué**, et la surface non jouée vient de s'élargir :

| Nombre | Valeur | Statut |
|--------|--------|--------|
| Échéances missions 2 et 3 | 40 tours | estimation |
| Bonus ennemi mission 3 | 60 crédits | estimation |
| Récompenses de mission | 2 / 3 / 4 / 4 | **estimation, non jouée** |
| Coûts des perks | 2 / 3 / 3 / 3 / 2 / 4 | **estimation, non jouée** |
| Primes d'objectifs facultatifs | +1 / +2 | **estimation, non jouée** |
| mission_4 : 20 tours, 300 crédits, échéance 35 | — | **estimation, non jouée** |

« The Kaelen Gate » demande de traverser un diamètre de carte (distance 10 entre les deux capitales),
de réduire une planète de niveau 2 à 0 par le siège, puis de la capturer. C'est plausible en 40 tours,
mais je ne peux ni le mesurer ni le ressentir : ni build, ni émulateur ici.

Le rapport gains/coûts de la gloire s'est **rééquilibré sur le papier** : 16 points gagnables pour 17
points d'achats, contre 16 pour 8 avant l'ajout des trois derniers perks. Tout acheter n'est plus
possible, donc choisir redevient un arbitrage. Mais « sur le papier » est le mot juste — un catalogue
équilibré en total peut très bien contenir un perk qui écrase les autres, et **c'est précisément ce
qu'un tableau ne montre pas.** Le croiseur du tour 1 est mon principal suspect : arriver avec une
flotte que l'adversaire n'a pas encore pourrait décider une mission courte à lui seul.

Le fait que les primes soient jugées **à l'instant de la victoire** a une conséquence de jeu que je
ne peux pas évaluer sans jouer : sur mission_3, il peut devenir rationnel de retarder la capture
d'un tour pour finir de remplir le trésor. Est-ce une tension intéressante ou une manipulation
pénible ? Je n'en sais rien — c'est à ressentir, pas à raisonner.

L'écran de sélection n'a été relu que statiquement. La liste d'objectifs et celle des perks sont dans
la même zone défilante et le bouton de lancement est en dehors, mais **je n'ai pas pu regarder cet
écran** — et il vient de gagner deux blocs de texte.

## 7. Suggestions pour la suite (non implémentées)

Par ordre de rapport valeur/effort :

1. **Conditions de départ scriptées.** Aujourd'hui une mission ne fait varier que carte, faction,
   ennemi, bonus et échéance. Les leviers les plus rentables, dans l'ordre : flotte de départ
   imposée, technologies déjà débloquées, crédits initiaux, planètes pré-possédées. C'est ce qui
   permet des *tempos* différents — un raid à trois vaisseaux sans économie, un siège avec un unique
   dreadnought. Les perks de gloire ouvrent déjà deux de ces leviers (crédits, technos) : la
   structure de données est là, il reste à la mettre entre les mains du concepteur de mission.
2. **Événements scriptés.** `GalacticEvent` est purement aléatoire. Déclencher un événement à un tour
   donné (« au tour 8, tempête ionique ») créerait des pics dramatiques reproductibles sans nouveau
   moteur — le système d'effets existe déjà.
3. **Narration.** Il n'y a qu'un champ `description`. Un texte d'introduction et un texte de
   conclusion (victoire / défaite) par mission coûtent presque rien et changent radicalement la
   perception d'une campagne.
4. **Déverrouillage séquentiel.** Maintenant que `completedMissions` est peuplé *et durable*, un
   champ `requiresMissionId` permettrait un ordre de progression — l'écran de sélection sait déjà
   quelles missions sont terminées.

## 8. Ce qui fonctionnait déjà bien

- **La guerre scriptée est forcée** : `handleStartCampaign` met le joueur et l'ennemi en `WAR`, sans
  quoi l'IA — qui n'engage que les factions en guerre — resterait passive et la mission serait vide.
  Le commentaire d'origine explique correctement pourquoi.
- **Détection de défaite** : perte de toutes les unités **et** de toutes les planètes met fin à la
  mission, indépendamment de l'objectif.
- **Priorité de la campagne** : les conditions de victoire standard sont court-circuitées en mission,
  ce qui évite qu'une victoire économique fortuite ne termine un scénario scripté.
- **Taille de carte par mission** : `mapSize` est respecté depuis l'audit des technologies (A4).
