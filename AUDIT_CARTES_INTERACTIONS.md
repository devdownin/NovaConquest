# Audit — Gestion des cartes & interactions joueur (optimisation + ergonomie)

> Portée : la caméra et les gestes de la carte tactique (`TacticalMapScreen`), la boucle de
> rendu du plateau, et le coût des calculs déclenchés par la sélection / le pathfinding
> (`GameGridMap`, `HexPathfinder`). Fait suite à `AUDIT_CARTES.md`, qui couvrait la génération
> de carte, les effets de terrain et la cohérence des portées ; ce second passage se concentre
> sur ce que le joueur *ressent* — la réponse au doigt — et sur ce que le GPU redessine.
>
> ✅ **Vérification.** Le proxy réseau refuse `dl.google.com` (403), donc l'Android Gradle Plugin
> est inaccessible *dans cet environnement* et `:app` ne s'y compile pas. Contournement : les
> trois modules purs ont été compilés et exécutés hors Gradle (compilateur Kotlin 1.9.23 depuis
> Maven Central) — **210 tests au vert**, dont les 16 nouveaux. Et **CI a tranché pour `:app`** :
> le run de la branche est vert, étape « Assemble debug APK » comprise. La *compilation* de tout
> ce qui suit est donc vérifiée ; le *comportement* des changements Compose, lui, reste relu
> statiquement (aucun test instrumenté ne couvre cet écran, et il n'y a pas d'émulateur ici).

## 1. Résumé exécutif

| # | Sévérité | Domaine | Constat |
|---|----------|---------|---------|
| **G1** | 🔴 Majeur — ✅ corrigé | Gestes | Le déplacement de la carte **ne suit pas le doigt** : facteur `1/(zoom+1)`, soit 4× trop lent à 3× de zoom |
| **G2** | 🔴 Majeur — ✅ corrigé | Gestes | Le pincement zoome autour du **centre de l'écran**, pas des doigts — le secteur visé fuit sous la main |
| **G3** | 🟠 Moyen — ✅ corrigé | Gestes | Un appui long sur sa propre flotte ouvrait **la fiche terrain *et*** démarrait le glisser ; la fiche restait par‑dessus la carte |
| **G4** | 🟠 Moyen — ✅ corrigé | Ergonomie | Zoom et cadrage **réinitialisés à chaque aller‑retour** vers l'onglet SYSTEM / TECH / INTEL |
| **G5** | 🟠 Moyen — ✅ corrigé | Ergonomie | Taper une case hors de portée envoyait un ordre que le moteur ne pouvait que refuser (snackbar d'erreur) |
| **G6** | 🟡 Faible — ✅ corrigé | Rendu | Le vaisseau en déplacement était dessiné **deux fois** pendant les 350 ms d'animation |
| **G7** | 🟡 Faible — ✅ corrigé | Ergonomie | La carte restait pleinement interactive pendant le tour de l'IA → file de « AI is thinking, please wait. » |
| **G8** | 🟡 Faible — ✅ corrigé | Ergonomie | Aucune limite de panoramique : la galaxie pouvait être poussée entièrement hors écran |
| **G9** | 🟡 Faible — ✅ corrigé | Ergonomie | Zoom minimum 0.5× — **insuffisant pour voir une carte GIGANTIC** en entier |
| O1 | ⚪ Optim — ✅ fait | Rendu | Les couches n'avaient **pas de calque propre** : la scanline à 60 fps ré-enregistrait tout le terrain à chaque frame |
| O2 | ⚪ Optim — ✅ fait | Rendu | Aucun *culling* : les 469 tuiles d'une GIGANTIC étaient parcourues même quand ~80 sont visibles |
| O3 | ⚪ Optim — ✅ fait | Rendu | Les flottes vivaient dans la couche terrain → **tout le plateau repeint à chaque déplacement** |
| O4 | ⚪ Optim — ✅ fait | Rendu | `drawHexagonPath` recalculait 12 cos/sin par hexagone et par passe (~11 000 par frame) |
| O5 | ⚪ Optim — ✅ fait | Rendu | Un `drawText` natif par tuile, même à un zoom où le texte est illisible |
| O6 | ⚪ Optim — ✅ fait | Pathfinding | `GameGridMap.getNeighbors` rescannait **toute la carte** à chaque nœud étendu (A* quadratique) |
| O7 | ⚪ Optim — ✅ fait | Sélection | 3 balayages de la carte entière à chaque sélection d'unité (portée d'attaque, capture, siège) |
| D1 | ⚪ Dette — ✅ fait | Duplication | `hexRound` dupliqué entre `:core:hex` et l'écran ; maths de caméra intestables en CI |
| D2 | ⚪ Dette — ✅ fait | Dette | Paramètre `onEndTurnClick` mort (le bouton FIN DE TOUR vit dans `MainActivity`) |
| **A1** | 🟠 Moyen — ✅ corrigé | Accessibilité | La carte est un `Canvas` sans sémantique : **injouable au lecteur d'écran**, et impossible à piloter au clavier / D-pad |
| R2 | 💡 Reco — ❌ écarté | Ergonomie | Double‑tap pour zoomer : coûte ~300 ms de latence sur *chaque* tap (voir §5) |

---

## 2. Gestes : la carte ne suivait pas le doigt

### G1 / G2 — la boucle de rétroaction des coordonnées  ✅ **corrigés**

Les trois détecteurs (`detectTransformGestures`, `detectTapGestures`,
`detectDragGesturesAfterLongPress`) étaient posés **à l'intérieur** du `Modifier.graphicsLayer`
qui applique le zoom et le panoramique. Un commentaire présentait ça comme un avantage :
Compose inverse la transformation, donc les coordonnées reçues sont directement celles du plan
de dessin et alimentent `pixelToHex` sans conversion.

C'est vrai pour le **hit‑testing**. C'est faux dès que le geste **modifie** cette même
transformation, parce que le système de coordonnées dans lequel l'événement suivant est rapporté
dépend alors de ce qu'on vient d'écrire.

Avec `pan += panChange` et un doigt qui avance de `ΔP` pixels écran par événement, le delta
rapporté vaut `panChange = (ΔP − Δ_précédent) / zoom`. Le point fixe de cette récurrence est :

```
Δ = ΔP / (zoom + 1)
```

Autrement dit la carte se déplace **toujours moins vite que le doigt** : moitié moins à 1×, un
quart à 3×. C'est le genre de défaut qu'on ne sait pas nommer en jouant — la carte « colle »,
on refait le geste trois fois.

Le zoom souffrait du même problème sous une autre forme : `scale = scale * zoom` sans toucher au
panoramique met le point fixe **au centre de l'écran**. Le joueur qui pince sur une planète en
bord d'écran la voit partir hors cadre.

**Correctif** — les détecteurs passent **au-dessus** du `graphicsLayer` (un `Box` parent), donc
les coordonnées sont en pixels écran, stables. L'inverse de la transformation est appliqué à la
main par `HexLayout.hexAtScreen`, et le panoramique devient un `pan += panChange` honnête
(1 pixel écran = 1 pixel de doigt, à tout zoom). Le zoom est ancré sur le centroïde du
pincement :

```
pan' = pan + (centroïde − centre − pan) × (1 − nouveauZoom / ancienZoom)
```

Le glisser d'une flotte prend désormais la main sur le panoramique (`if (dragStartHex != null)
return`), sinon le plateau glissait sous le tracé fantôme.

### G3 — appui long : deux détecteurs, un seul doigt  ✅ **corrigé**

`detectTapGestures(onLongPress = …)` ouvrait la fiche terrain, et
`detectDragGesturesAfterLongPress` démarrait le glisser‑déplacer. Les deux se déclenchent sur
**leurs propres minuteurs**, indépendamment de toute consommation d'événement : appuyer
longuement sur son propre croiseur ouvrait donc la fiche plein écran *pendant* le glisser, et la
fiche — dont le fond est cliquable pour se fermer — restait posée sur la carte après le lâcher.

**Correctif** — la fiche terrain n'est plus proposée quand l'appui porte sur une flotte alliée
encore mobile, c'est‑à‑dire exactement la condition qui démarre un glisser. Déterministe, sans
dépendre de l'ordre des minuteurs. L'appui long conserve son rôle sur tout le reste : terrain
nu, planètes, unités ennemies, flottes déjà déplacées.

### G5 — un ordre condamné d'avance  ✅ **corrigé**

Le second tap sur une case vide envoyait `MoveUnit` **sans vérifier la portée**. Le réducteur
répondait « Target position is unreachable or too far. » dans un snackbar. Or la surbrillance
cyan affiche déjà la portée exacte (`MovementCalculator`, cf. B3 de l'audit précédent) : taper
au‑delà n'est jamais une tentative de déplacement, c'est un changement de sélection.

**Correctif** — la branche « déplacer » exige `coord in reachableHexes` ; sinon on retombe sur
la branche « re‑sélectionner ». Une erreur de moins, une intention respectée.

### G7 / G8 / G9 — trois frictions de cadrage  ✅ **corrigés**

- **Pendant le tour de l'IA**, `GameEngine.handleIntent` refuse tout (sauf chargement/nouvelle
  partie). La carte, elle, restait interactive : chaque tap produisait un snackbar. Les taps et
  les glissers sont maintenant ignorés (avec un tick haptique) tant que `isAiThinking` est vrai —
  **le panoramique et le zoom restent actifs**, pour pouvoir suivre ce que fait l'adversaire.
- **Le panoramique est borné** : la boîte englobante de la galaxie garde toujours 96 px de
  recouvrement avec l'écran.
- **Le zoom minimum passe de 0.5× à 0.25×.** À 3× de densité, une carte GIGANTIC (rayon 12) fait
  ~1 870 px de demi‑largeur contre ~540 px de demi‑écran : à 0.5× on n'en voyait que le tiers, et
  aucun réglage ne permettait la vue d'ensemble qu'un 4X doit offrir.

### G4 — la caméra jetée à chaque onglet  ✅ **corrigé**

`MainActivity` compose la carte dans un `when (currentTab)` : ouvrir SYSTEM, TECH ou INTEL
**détruit** `TacticalMapScreen`, et son `remember { scale/pan }` avec. En pratique le joueur
était renvoyé sur sa capitale à 0.8× plusieurs fois par tour — juste pour avoir consulté un
arbre technologique.

**Correctif** — nouvelle classe `MapCameraState` (zoom + panoramique), instanciée dans
`GameScreen` qui, lui, reste composé. `TacticalMapScreen` la prend en paramètre avec un défaut
`remember { … }` pour les aperçus. Le cadrage survit désormais aux allers‑retours.

---

## 3. Rendu : trois calques au lieu d'un

### O1 — le calque unique  ✅ **fait**

L'audit précédent (O2) avait sorti les surbrillances de sélection de la boucle de terrain, dans
un second `Canvas`. Le raisonnement était bon mais **incomplet** : deux `Canvas` frères sans
`graphicsLayer` propre dessinent tous les deux dans le calque du parent, et invalider l'un
invalide le calque partagé. La scanline animée — 60 fps par construction — forçait donc quand
même le ré-enregistrement de la totalité du parcours de tuiles, à chaque frame. Le gain annoncé
n'existait pas.

**Correctif** — répartition explicite en trois couches, chacune avec son propre `RenderNode` là
où c'est utile :

| Couche | Contenu | Se redessine quand |
|--------|---------|--------------------|
| **Terrain** (`graphicsLayer`) | tuiles, décor, indicateur de production | la carte, le brouillard ou la caméra changent |
| **Flottes + sélection** (`graphicsLayer`) | portées, cibles, contour sélectionné, vaisseaux | une unité bouge, ou la sélection change |
| **Animations** (sans calque) | scanline, halo « unité disponible », tracé fantôme, laser/explosion, vaisseau en vol | chaque frame — c'est son rôle |

### O2 — *viewport culling*  ✅ **fait**

La boucle parcourait `map.tiles.values` en entier. Un téléphone en affiche ~80 au zoom nominal.
L'inverse de la transformation donne la tranche du plan réellement à l'écran ; les tuiles hors
cadre sont sautées, dans la couche terrain comme dans la couche flottes. Contrepartie assumée :
la couche terrain lit maintenant `pan`/`scale` et se ré-enregistre pendant un geste — mais sur
~80 tuiles au lieu de 469.

### O3 / O4 / O5 — coût par tuile  ✅ **faits**

- Les vaisseaux étaient dessinés **dans la boucle de terrain**. Chaque déplacement — le geste le
  plus fréquent du jeu — repeignait donc tout le plateau. Ils passent dans la couche flottes.
- `drawHexagonPath` recalculait `cos`/`sin` pour ses 6 sommets à chaque appel, alors que tous
  les hexagones ont la même forme : ~11 000 appels trigonométriques par frame sur une GIGANTIC.
  Les 12 valeurs unitaires sont désormais calculées une fois.
- Un `nativeCanvas.drawText` par tuile pour l'identifiant de secteur (l'opération la plus chère
  de la boucle) — sauté sous 0.9× de zoom, où les glyphes ne sont plus que du bruit.

### O6 — A* quadratique avec la nav par trou de ver  ✅ **fait**

`GameGridMap.getNeighbors` est appelé pour **chaque nœud étendu** par A* et par le flood‑fill de
portée. Pour une faction ayant `tech_wormhole_nav`, il rescannait `map.tiles.values` en entier à
chaque appel afin de retrouver les sorties de trou de ver : O(nœuds × tuiles), soit ~220 000
visites de tuile pour un seul trajet sur une GIGANTIC. La liste des sorties est maintenant
résolue une fois par instance (`by lazy`). Le résultat est identique — c'est ce que verrouillent
les nouveaux tests.

### O7 — balayages de carte à chaque sélection  ✅ **fait**

Sélectionner une unité recalculait `attackRangeHexes` en filtrant **toutes** les clés de la
carte par distance, et `capturableCoords` / `siegeableCoords` en filtrant **toutes** les tuiles
pour n'en garder que des voisines immédiates. Remplacés par une énumération du disque de portée
(≤ 36 hexagones) et par les 6 voisins. Indépendant de la taille de carte.

---

## 4. Dette adressée au passage

- **D1** — `hexRound` existait en double : `HexCoord.round` dans `:core:hex` et une copie privée
  dans l'écran. Toute la géométrie écran ↔ hexagone (espacement, centre d'un hexagone,
  conversion inverse de la caméra, zoom focal, bornes de panoramique) est regroupée dans un
  nouvel objet **`HexLayout`** (`:core:hex`). Bénéfice principal : ces maths — la partie la plus
  facile à casser en silence — sont désormais **couvertes par les tests JVM que CI exécute**,
  alors que rien dans `:app` ne l'est.
- **D2** — `onEndTurnClick` était passé à `TacticalMapScreen` et jamais lu (le bouton FIN DE TOUR
  vit dans la barre de `MainActivity`). Supprimé.

---

## 5. Ce qui a été délibérément écarté

- **Double‑tap pour zoomer (R2).** Standard sur une carte, mais `detectTapGestures` doit alors
  attendre le délai de double‑tap (~300 ms) avant de livrer *chaque* tap simple. Sur une carte
  où le tap est l'action principale (sélectionner, déplacer, attaquer), payer 300 ms de latence
  sur toutes les actions pour un raccourci de zoom est un mauvais échange. Le bouton « Reset
  view » et le pincement — désormais correct — couvrent le besoin.
- **Une grille sémantique par hexagone.** L'approche « un nœud focalisable par case » aurait
  donné à TalkBack un balayage case par case, mais elle impose des centaines de nœuds de
  sémantique qui suivent le zoom et le panoramique. Le curseur clavier + région live (§4bis)
  couvre le même besoin pour un coût de rendu nul. À revoir si des retours utilisateurs le
  demandent.
- **Fiche terrain en modale plein écran.** Une *bottom sheet* ancrée serait plus conforme aux
  usages Material 3, mais le composant actuel fonctionne et le conflit qui le rendait pénible
  (G3) est traité. À ne pas confondre avec la **fiche de secteur**, elle, désormais adaptative
  (§5bis, T5).

---

## 4bis. Accessibilité — curseur clavier et annonces  ✅ **fait (A1)**

Le plateau est dessiné dans un `Canvas`. Un `Canvas` ne porte **aucune sémantique** : TalkBack
n'avait donc littéralement rien à annoncer sur la carte, et sans pointage précis il n'existait
aucun moyen de sélectionner une case. Le jeu était inutilisable au lecteur d'écran, et
impilotable au clavier ou au D‑pad.

**Curseur clavier, distinct de la sélection.** Un 4X tactique se joue en deux temps — choisir une
flotte, *puis* choisir sa cible — donc un seul état ne suffit pas : `cursorHex` est là où le
joueur *regarde*, `selectedHex` ce qu'il a *validé*.

| Touche | Effet |
|--------|-------|
| ← / → | voisin ouest / est |
| ↑ / ↓ | voisin nord‑ouest / sud‑est |
| Maj + ← / → | voisin sud‑ouest / nord‑est |
| Entrée, Espace, D‑pad centre | agit sur la case du curseur (mêmes règles qu'un tap) |
| Échap | annule la sélection |

Un hexagone *pointy‑top* n'a pas de voisin à la verticale : les quatre flèches sont donc mappées
sur les quatre directions « plates », et Maj atteint les deux diagonales restantes. Les quatre
flèches suffisent à elles seules à atteindre n'importe quelle case (les axes `q` et `r` forment
une base du réseau) ; Maj n'est qu'un raccourci.

**La caméra suit — mais pas à chaque touche.** Recentrer à chaque pression fait tressauter le
plateau ; ne jamais recentrer laisse le curseur sortir de l'écran, ce dont un joueur au clavier
ne peut pas se remettre. `HexLayout.isComfortablyVisible` ne déclenche le recentrage que lorsque
le curseur quitte les 70 % centraux du viewport.

**Ce que TalkBack annonce.** La carte porte une `contentDescription` en `liveRegion` *polite*,
recalculée à chaque mouvement du curseur : secteur, terrain, propriétaire et niveau de la
planète, occupant (type, faction, PV, disponibilité) — **et surtout ce que ferait Entrée** :
« déplacer la flotte ici », « attaquer », « assiéger la planète », « désélectionner »… Sans cette
dernière phrase, le flux en deux temps est impossible à suivre à l'oreille. Le brouillard de
guerre est respecté : une case inexplorée s'annonce « inexploré », et une unité ennemie hors
vision n'est pas révélée.

**Effet de bord bénéfique** : la logique de décision d'un tap (désélectionner / attaquer /
assiéger / déplacer / re‑sélectionner) a été extraite du gestionnaire tactile dans une fonction
`activateHex` partagée par les deux entrées. Le clavier et le doigt ne peuvent plus diverger.

Le curseur n'est dessiné (anneau blanc) qu'après une première pression de touche : un joueur au
doigt ne voit pas un second contour suivre ses taps.

---

## 5bis. Suites données au rapport

Trois chantiers issus des suggestions de fin d'audit, menés après coup.

### T1 — Les règles d'interaction sortent du Composable  ✅

`:app` n'avait **aucun test** : ni répertoire, ni fichier, alors que les dépendances
`androidTest` et un job CI étaient déclarés depuis longtemps. Les cinq branches de la sélection
en deux temps vivaient dans le gestionnaire de tap, hors de portée de tout gate.

Elles forment désormais `MapInteraction` (`:core:engine`), une fonction pure qui renvoie une
`MapAction` décrivant ce qu'il faut faire ; le Composable ne garde que les effets. Les règles
existaient en **trois exemplaires** — tap, clavier, glisser — il n'en reste qu'un.

**Deux bugs sont tombés en écrivant les tests.** `MapFactory` sème des planètes **sans
propriétaire au niveau 2 à 4** (nœuds Zodiac au niveau 5). Pour ces mondes, le moteur autorise
l'assaut (`handleSiegePlanet` ne refuse qu'une planète que l'on possède déjà), la surbrillance
les cerclait, le bouton du panneau latéral proposait l'action — mais la branche du tap exigeait
`owner != null`. Taper une forteresse neutre cerclée y **envoyait la flotte** au lieu de
l'assiéger, les planètes étant franchissables. Même défaut dans le glisser. C'est la classe de
bug visée par B3 de l'audit précédent, sur le contenu de carte le plus courant qui soit.
*Changement de comportement assumé* : taper une planète neutre adjacente ouvre maintenant la
boîte assaut/capture au lieu de s'y déplacer.

### T2 — Points de mouvement partiels  ✅

`handleMoveUnit` posait `hasMoved = true` **quelle que soit la distance** : un SCOUT (mouvement
5) qui avançait d'une case perdait les quatre autres. Le trajet est désormais facturé à son coût
réel (`HexPathfinder.pathCost`, terrain difficile compris), `GameUnit.movementUsed` porte la
dépense, et `hasMoved` ne bascule qu'à l'épuisement du budget — ou lors d'un combat, qui consomme
le tour entier comme avant.

`MovementCalculator.remainingMovement` devient la source unique pour la surbrillance, la
prévisualisation du glisser et le réducteur, dans la continuité de B3. Le champ est **défaillant
à 0 et ajouté en fin de constructeur** : les sauvegardes existantes décodent toujours (il n'y a
pas de couche de migration) et les appels positionnels compilent encore. L'IA n'est pas affectée
— elle n'itère qu'une fois par unité et ne déplace qu'une fois.

### T3 — Annulation dans le tour  ✅

Il n'existait aucun undo. L'architecture le rendait pourtant presque gratuit : `GameState` est
immuable et tout passe par `reduce`, donc une pile d'états suffit. `UndoHistory` (objet séparé,
testable sans piloter la coroutine d'intents), profondeur 20, vidée à la fin de tour et au
chargement d'une partie.

**Le choix de conception, explicite : l'annulation s'arrête au brouillard.** Une action qui
découvre du terrain jamais vu est définitive. Revenir en arrière restaurerait `exploredHexes`,
mais pas la mémoire du joueur : avancer, regarder, annuler serait de la reconnaissance gratuite.

Conséquence non évidente, et c'est le cœur de la mise en œuvre : une telle action doit vider
**tout** l'historique, pas seulement s'abstenir de s'y inscrire. Revenir à n'importe quel état
antérieur ré-occulterait la même zone — l'exploit serait simplement différé d'un coup.

Bouton dans la barre de la carte, `Ctrl+Z` au clavier.

⚠️ **Coût ergonomique à surveiller en partie d'essai.** Un éclaireur avance presque toujours dans
le brouillard : en pratique, l'annulation sera indisponible après la plupart des déplacements
d'exploration, et le bouton se grisera sans explication. C'est inhérent à l'option retenue, pas un
défaut de réalisation — mais si cela se révèle frustrant, la piste serait d'afficher *pourquoi*
l'historique s'est fermé plutôt que d'assouplir la règle.

### T4 — L'événement de combat sort de l'état  ✅

`CLAUDE.md` annonçait `SharedFlow` comme direction visée pour les effets ponctuels. Le moteur
possédait **déjà** un flux `_effects` et écrivait *en plus* l'événement dans
`GameState.lastCombatEvent` : la même information, deux transports.

Ce n'était pas qu'une redondance. `CombatEvent` ne porte que trois champs — attaquant, cible,
cible détruite — donc **deux attaques identiques d'affilée produisent un événement égal**. Or
l'ancien code déclenchait l'animation sur `LaunchedEffect(gameState.lastCombatEvent)` et
n'émettait les effets que si `combat != currentState.lastCombatEvent`. Conséquence : un vaisseau
qui tire deux tours de suite depuis la même case sur la même cible survivante voyait le **second
tir se dérouler sans laser, sans explosion, sans son et sans notification**.

`CombatSystem.resolveCombat` renvoie désormais un `CombatOutcome` (état + événement), le réducteur
le remonte par `GameResult` — comme il remontait déjà `notification` — et le moteur émet
`GameEffect.CombatResolved`. L'écran collecte un `Flow<CombatEvent>` au lieu de surveiller une clé
d'état. Le champ disparaît de `GameState`.

Un test verrouille la propriété qui manquait : deux attaques depuis le même état produisent chacune
leur événement, *et* ces événements sont égaux — c'est cette égalité qui masquait le second.

### T5 — La fiche de secteur descend en bas sur téléphone  ✅

220 dp fixes en `CenterEnd` recouvraient le milieu droit du plateau — c'est-à-dire l'endroit que
le joueur regarde, puisqu'il vient d'y taper. Sous le seuil compact de Material (600 dp), la fiche
passe en bas, pleine largeur, hauteur plafonnée ; au-dessus, la colonne latérale est conservée, la
largeur ne manquant pas.

Conséquence traitée : le journal de combat et la graine sont dessinés **après** la fiche, donc
au-dessus d'elle. Sur téléphone ils occupaient le même bas d'écran et l'auraient recouverte — ils
s'effacent pendant qu'une case est sélectionnée. C'est de l'information d'ambiance ; la fiche est
ce que le joueur vient de demander.

⚠️ **Non vérifié visuellement.** Aucun test ne regarde une mise en page, et il n'y a pas
d'appareil ici. La pile verticale de boutons d'action, en particulier, mérite un coup d'œil : elle
était acceptable en colonne latérale, elle est peut-être à mettre en ligne en bas.

### T6 — Les garde-fous de style qui n'en étaient pas  ✅

`Spotless check` et `Detekt` tournaient à chaque run, enveloppés dans
`|| echo "... not configured, skipping"`. Aucun des deux plugins n'est appliqué dans le build :
les étapes étaient donc **toujours vertes et ne prouvaient rien**, pour 80 s par run — et depuis
qu'une pull request est ouverte, ces deux coches vertes s'affichent au relecteur.

Elles sont supprimées plutôt que laissées à suggérer un contrôle qui n'existe pas. Les activer
pour de bon reste souhaitable, mais c'est un changement à part : le premier passage reformate ou
signale l'essentiel du dépôt — `TacticalMapScreen.kt` fait à lui seul ~2 500 lignes et contient
6 imports génériques que ktlint refuse. `CLAUDE.md` note la marche à suivre (`ratchetFrom` pour
Spotless, une *baseline* pour Detekt).

---

## 6. Tests

Nouveaux (`:core:hex`, `HexLayoutTest` — 9 cas) :

- aller‑retour hexagone → pixel → hexagone ;
- hit‑testing sous caméra arbitraire (zoom 1.7×, panoramique non nul) ;
- le panoramique de centrage met bien l'hexagone au centre de l'écran ;
- **le zoom focal garde l'hexagone sous les doigts** — et le cas témoin qui échoue sans la
  correction (zoom naïf : l'hexagone dérive) ;
- zoomer pile au centre de l'écran ne déplace pas le panoramique ;
- bornes de panoramique (saturation, et absence d'effet sur une vue raisonnable) ;
- espacements de la grille *pointy‑top*.

Nouveaux (`:core:engine`, `GameGridMapTest` — 3 cas) : les sorties de trou de ver ne sont
voisines qu'avec la techno, un hexagone n'est jamais son propre voisin, les appels répétés
renvoient la même liste (verrouille la mise en cache), et les voisins se limitent aux tuiles qui
existent.

Ajoutés ensuite pour le suivi de caméra du curseur clavier (4 cas) : `localToScreen` est bien
l'inverse de `screenToLocal`, une case centrée est « confortablement visible », une case poussée
au bord ne l'est pas, et un viewport pas encore mesuré (première frame) est traité comme
confortable — sinon le curseur recentrerait la caméra dès la première touche.

Ajoutés par les chantiers de la §5bis : `MapInteractionTest` (18 cas, dont les mondes neutres),
`PartialMovementTest` (6 cas : coût réel, deux déplacements dans un tour, épuisement du budget,
terrain difficile, structure immobile) et `UndoHistoryTest` (9 cas : ordre inverse, éviction au-delà
de la profondeur, politique par type d'intent, et la règle du brouillard — y compris le fait que
seule la faction agissante compte, pour que l'exploration de l'IA ne ferme pas l'historique du
joueur).

**Les tests `:app` existent enfin**, répartis selon ce que chaque harnais sait faire :

| Harnais | Contenu | Tourne |
|---------|---------|--------|
| `test/` — Robolectric | contrat d'accessibilité (7 cas) | à chaque poussée |
| `androidTest/` — appareil | gestes : tap, curseur clavier, bouton d'annulation (6 cas) | sur `main` |

Les tests de gestes ont d'abord été écrits sous Robolectric, où ils échouent sur « Failed to
inject touch input » et « Failed to perform RequestFocus action » : Robolectric rend l'arbre de
sémantique mais n'assure ni l'injection d'entrées ni le focus fenêtre. Les déplacer n'est pas un
repli — c'est leur harnais correct. Une étape CI dédiée les **compile** sur chaque branche, faute
de quoi ils ne casseraient qu'après fusion.

Conséquence à assumer : sur une branche, le câblage des gestes n'est pas gaté. Les *règles* qu'ils
exercent le restent, elles, par `MapInteractionTest`.

Exécution locale hors Gradle (AGP inaccessible ici) : **243 tests, 0 échec**.
Compilation de `:app` : vérifiée par CI (« Assemble debug APK », vert).
