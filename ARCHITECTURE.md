# Nova Empire - Architecture et Spécifications Techniques

Ce document décrit l'architecture logicielle, les mécaniques de jeu implémentées et le statut actuel du prototype de *Nova Empire*, un jeu 4X mobile.

## 1. Architecture Modulaire

Le projet est divisé en modules fortement découplés pour garantir des tests unitaires rapides et une séparation stricte entre la logique métier et l'interface utilisateur.

*   `:core:hex` : Bibliothèque pure Kotlin contenant les mathématiques hexagonales (Coordonnées cubiques `HexCoord`, distances) et l'algorithme de Pathfinding A* (`HexPathfinder`). Ne dépend d'aucun autre module.
*   `:core:domain` : Définit les modèles de données fondamentaux (`Faction`, `UnitType`, `TerrainType`, `Hero`, `MapArchetype`) ainsi que la structure immuable de l'état du jeu (`GameState`, `PlayerState`).
*   `:core:engine` : Cœur du jeu en pur Kotlin. Contient la boucle principale (`GameEngine`), la résolution des combats (`CombatResolver`), la logique d'intelligence artificielle (`UtilityEvaluator`), le système de vision (Fog of War via raycasting) et la gestion des sauvegardes JSON (`SaveManager`).
*   `:app` : Module Android utilisant Jetpack Compose pour le rendu de l'interface utilisateur. Connecte le moteur via le `GameViewModel`. Gère également les retours sensoriels (`AudioManager`, `LocalHapticFeedback`).

## 2. Flux de Données Unidirectionnel (UDF)

Le jeu repose sur un pattern UDF (Unidirectional Data Flow) strict, garantissant prédictibilité et facilité de sauvegarde :
1.  **État (`GameState`)** : Une classe de données immuable contenant l'intégralité de la partie (carte, unités, crédits, diplomatie, événements). Elle est exposée sous forme de `StateFlow` depuis le `GameEngine`.
2.  **Intentions (`GameIntent`)** : Toute action (joueur ou système) est encapsulée dans un *Intent* (ex: `MoveUnit`, `AttackUnit`, `EndTurn`, `ResearchTech`).
3.  **Réduction (`GameEngine.reduce`)** : Le moteur prend le `GameState` actuel, applique l'intention de manière pure, et émet un nouveau `GameState`.

## 3. Systèmes et Mécaniques de Jeu

### 3.1 Carte Tactique & Pathfinding
*   Génération procédurale via `MapFactory` avec support de plusieurs tailles (Small, Medium, Large, Gigantic) et d'archétypes (Standard, Zodiac).
*   Pathfinding A* qui respecte la topologie hexagonale et les types de terrain infranchissables (ex: Astéroïdes).
*   Rendu natif sur le `Canvas` de Jetpack Compose avec support du Pan (déplacement) et Pinch-to-Zoom.

### 3.2 Brouillard de Guerre & Lignes de Vue
*   `VisionSystem` recalcule les cases visibles après chaque mouvement.
*   L'algorithme de raycasting bloque la vision à travers certains terrains (ex: Nébuleuses). L'interface utilisateur assombrit (alpha réduit) les zones explorées mais non visibles actuellement, et rend invisibles les unités ennemies qui s'y trouvent.

### 3.3 Économie, Siège et Production
*   À chaque fin de tour, un joueur gagne des crédits en fonction des planètes qu'il possède (`base + systemLevel * 2`).
*   Les unités peuvent "Assiéger" (`SiegePlanet`) une planète ennemie pour réduire son niveau de système, puis la "Capturer" (`CapturePlanet`) pour en prendre le contrôle.
*   Le joueur peut produire de nouvelles unités (`BuildUnit`) depuis n'importe quelle planète qu'il contrôle via l'interface de gestion de système.

### 3.4 Combats & Animations
*   `CombatResolver` gère les calculs tactiques : L'attaquant inflige des dégâts ; si le défenseur survit, il riposte. Les unités à 0 HP sont supprimées.
*   Sur le plan visuel, la destruction d'une unité déclenche un `CombatEvent` dans l'état. L'UI intercepte cet événement pour animer un rayon laser puis une explosion (via Compose `Animatable`) et un retour haptique fort, avant de faire disparaître l'unité.

### 3.5 Intelligence Artificielle (Utility Evaluator)
L'IA non-joueuse évalue ses priorités lors de son tour :
1.  **Diplomatie** : Évalue la puissance économique et militaire. Propose des alliances aux empires trop puissants, déclare la guerre aux empires faibles, ou tente de faire la paix si une guerre tourne mal.
2.  **Économie/Recherche** : Tente d'acheter la première technologie abordable dans l'arbre.
3.  **Production** : Produit en boucle des Cruisers, Fighters ou Scouts selon son budget.
4.  **Tactique** : Déplace ses unités en utilisant l'algorithme A* vers l'ennemi le plus proche, et l'attaque s'il est à portée.

### 3.6 Bonus Passifs (Héros & Technologies)
L'achat d'un héros ou d'une technologie modifie *concrètement* les mathématiques du jeu :
*   *Commander Vance* : Ajoute +15% de dégâts bruts dans `CombatResolver`.
*   *Architect Kael* : Réduit de 10% le coût calculé par `TechRegistry`.
*   *High Seer Nix* : Soigne automatiquement la flotte de +1 HP à la fin du tour via `GameEngine`.
*   *Deep Scanners (Tech)* : Ajoute +1 à la portée de vision dans `VisionSystem`.

### 3.7 Sauvegarde & Reprise
Le jeu sauvegarde automatiquement à la fin de chaque tour via `SaveManager`. L'état est sérialisé en JSON (`kotlinx.serialization`). Le gestionnaire utilise un "Ring Buffer" de 3 fichiers. S'il détecte une sauvegarde corrompue au chargement, il la place en quarantaine et tente de charger la précédente.

## 4. Design System (Graphic Noir Futurism)

L'UI utilise Jetpack Compose pour reproduire fidèlement l'ambiance sci-fi industrielle :
*   **Couleurs** : Fond "Void Black" (`#111316`), avec des accents néons (Cyan, Rouge, Orange, Or, Magenta).
*   **Composants** : Panneaux en "Frosted Glass" (légèrement transparents avec bordures lumineuses), boutons industriels (angles coupés).
*   **Rendu Canvas** : Les hexagones utilisent des `RadialGradient` pour simuler des éléments holographiques rétro-éclairés. Les planètes ont des anneaux orbitaux stylisés, et les astéroïdes sont dessinés comme des amas géométriques.

## 5. Statut Actuel du Projet

**Statut : Prototype "Vertical Slice" Complété (Jeu Jouable de bout en bout).**

### Ce qui est implémenté :
- [x] Architecture modulaire et moteur pur Kotlin.
- [x] Grille, Pathfinding, Caméra (Pan/Zoom).
- [x] Brouillard de Guerre et événements galactiques.
- [x] Arbre technologique complet et production sur planètes multiples.
- [x] Combats avec prévisualisation, résolution, animations et haptique.
- [x] IA complète (Diplomatie, Achat, Déplacement, Attaque).
- [x] Système de Héros avec bonus mécaniques actifs.
- [x] Sauvegarde automatique et Menu Principal.
- [x] Mode "Campagne Zodiacale" (conditions de victoires alternatives).
- [x] UI complète en Compose (8 écrans).

### Prochaines étapes (Roadmap V1) :
- Ajout des vrais assets audio (fichiers .wav/.mp3) dans `res/raw`. L'infrastructure (AudioManager) est déjà 100% opérationnelle.
- Intégration de visuels 2D/3D réels pour les portraits de héros et les vaisseaux (actuellement dessinés de manière géométrique sur le Canvas).
- Ajout d'une surcouche multijoueur (l'architecture UDF permettant facilement la transmission d'Intentions via WebSocket).
- Extension de l'IA diplomatique : Actuellement l'IA évalue ses rapports de force, mais pourrait intégrer des notions de "Traîtrise" ou de "Loyauté".
- Tutoriel interactif intégré.
