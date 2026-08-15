# Audit — Gestion des cartes & interactions joueur (optimisation + ergonomie)

> Portée : la caméra et les gestes de la carte tactique (`TacticalMapScreen`), la boucle de
> rendu du plateau, et le coût des calculs déclenchés par la sélection / le pathfinding
> (`GameGridMap`, `HexPathfinder`). Fait suite à `AUDIT_CARTES.md`, qui couvrait la génération
> de carte, les effets de terrain et la cohérence des portées ; ce second passage se concentre
> sur ce que le joueur *ressent* — la réponse au doigt — et sur ce que le GPU redessine.
>
> ✅ **Tests exécutés.** Le proxy réseau refuse toujours `dl.google.com` (403), donc l'Android
> Gradle Plugin reste inaccessible et `:app` ne compile pas ici. Les trois modules purs ont en
> revanche été compilés et exécutés hors Gradle (compilateur Kotlin 1.9.23 récupéré depuis Maven
> Central) : **206 tests au vert**, dont les 12 nouveaux. Les changements dans `:app` reposent
> sur une relecture statique — c'est la limite de cet audit, et CI reste le juge.

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
| R1 | 💡 Reco — ❌ non fait | Accessibilité | La carte est un `Canvas` sans sémantique : **injouable au lecteur d'écran** |
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
- **Accessibilité (R1).** La carte est un `Canvas` sans `semantics` : TalkBack n'a rien à
  annoncer, le jeu est inutilisable au lecteur d'écran. Le corriger proprement suppose une grille
  sémantique par hexagone (libellé secteur/terrain/occupant) et une navigation au clavier/D‑pad ;
  c'est un chantier à part entière, hors de la portée de cet audit, mais c'est le plus gros écart
  restant vis‑à‑vis de l'état de l'art.
- **Fiche terrain en modale plein écran.** Une *bottom sheet* ancrée serait plus conforme aux
  usages Material 3, mais le composant actuel fonctionne et le conflit qui le rendait pénible
  (G3) est traité.

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

Exécution locale hors Gradle (AGP inaccessible) : **206 tests, 0 échec**.
