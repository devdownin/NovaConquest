# Nova Empire - Architecture et Spécifications Techniques

Ce document décrit l'architecture logicielle, les mécaniques de jeu implémentées et le statut actuel du prototype de *Nova Empire*, un jeu 4X mobile.

## 1. Architecture Modulaire

Le projet est divisé en modules fortement découplés pour garantir des tests unitaires rapides et une séparation stricte entre la logique métier et l'interface utilisateur.

*   `:core:hex` : Bibliothèque pure Kotlin contenant les mathématiques hexagonales (Coordonnées cubiques `HexCoord`, distances) et l'algorithme de Pathfinding A* (`HexPathfinder`). Ne dépend d'aucun autre module.
*   `:core:domain` : Définit les modèles de données fondamentaux (`Faction`, `UnitType`, `TerrainType`, `Hero`, `MapArchetype`) ainsi que la structure immuable de l'état du jeu (`GameState`, `PlayerState`).
*   `:core:engine` : Cœur du jeu en pur Kotlin. Contient la boucle principale (`GameEngine`) sécurisée par une file d'attente séquentielle d'intentions (`Channel`), la résolution des combats (`CombatResolver`), la logique d'intelligence artificielle avancée (`UtilityEvaluator`), le système de vision (Fog of War via raycasting) et la gestion des sauvegardes atomiques (`SaveManager`).
*   `:app` : Module Android utilisant Jetpack Compose pour le rendu de l'interface utilisateur. Connecte le moteur via le `GameViewModel`. Gère également les retours sensoriels (`AudioManager`, `LocalHapticFeedback`).

## 2. Flux de Données Unidirectionnel (UDF)

Le jeu repose sur un pattern UDF (Unidirectional Data Flow) strict, garantissant prédictibilité et facilité de sauvegarde :
1.  **État (`GameState`)** : Une classe de données immuable contenant l'intégralité de la partie (carte, unités, crédits, diplomatie, événements). Elle est exposée sous forme de `StateFlow` depuis le `GameEngine`.
2.  **Intentions (`GameIntent`)** : Toute action (joueur ou système) est encapsulée dans un *Intent* (ex: `MoveUnit`, `AttackUnit`, `EndTurn`, `ResearchTech`).
3.  **Réduction (`GameEngine.reduce`)** : Le moteur prend le `GameState` actuel, applique l'intention de manière pure via un système de `GameResult` (incluant des messages d'erreur explicites), et émet un nouveau `GameState`.

## 3. Systèmes et Mécaniques de Jeu

### 3.1 Carte Tactique & Pathfinding
*   Génération procédurale via `MapFactory` avec support de plusieurs tailles (Small, Medium, Large, Gigantic) et d'archétypes (Standard, Zodiac).
*   **Garantie de connexité** : après le placement du terrain, `MapFactory` lance un parcours en largeur (BFS) sur les cases franchissables et creuse des couloirs (Astéroïdes → Vide) jusqu'à ce que tous les points d'apparition et toutes les planètes appartiennent à une **unique région franchissable**.
*   Pathfinding A* qui respecte la topologie hexagonale et les types de terrain infranchissables.
*   Rendu natif sur le `Canvas` de Jetpack Compose avec support du Pan et Pinch-to-Zoom.

### 3.2 Brouillard de Guerre & Lignes de Vue
*   `VisionSystem` recalcule les cases visibles après chaque mouvement.
*   L'algorithme de raycasting bloque la vision à travers certains terrains. L'UI assombrit les zones explorées mais non visibles, et masque les unités ennemies.

### 3.3 Économie, Siège et Production
*   À chaque fin de tour, un joueur gagne des crédits en fonction des planètes possédées.
*   Les unités peuvent "Assiéger" une planète pour réduire son niveau, puis la "Capturer" pour en prendre le contrôle.
*   Production d'unités (incluant les nouveaux types : **Dreadnought**, **Defense Platform**) depuis les planètes contrôlées.

### 3.4 Combats & Animations
*   `CombatResolver` gère les calculs tactiques incluant les bonus de héros (Vance) et les bonus d'attaque spécifiques aux factions.
*   Animations synchronisées de lasers et d'explosions déclenchées par des événements d'état, accompagnées de retours sonores et haptiques (via le système `GameEffect`).

### 3.5 Intelligence Artificielle (Utility Evaluator)
L'IA non-joueuse évalue ses priorités lors de son tour :
1.  **Diplomatie** : Gère les alliances et les déclarations de guerre selon les rapports de force.
2.  **Héros & Économie** : Recrute des héros stratégiquement (Priorité Kael/Elara pour l'économie, Vance/Nix pour la guerre) et achète des technologies (en prenant en compte les réductions de coût de la faction).
3.  **Production** : Produit des unités variées selon son budget (Dreadnought, Battleship, Cruiser, etc.).
4.  **Tactique** : Déplace ses unités vers les cibles prioritaires (en utilisant les bonus de mouvement de faction). Gère intelligemment le siège et la capture de systèmes planétaires.

### 3.6 Factions Asymétriques
Chaque faction possède des traits uniques qui influencent la stratégie :
*   **Dominion** : Puissance militaire (+10% d'attaque).
*   **Traders** : Économie forte (+5 crédits bonus par tour).
*   **Synth** : Avantage technologique (-15% de coût de recherche).
*   **Nomads** : Mobilité et exploration (+1 mouvement, +1 vision).
*   **Kaelen** : Surveillance accrue (+2 portée de vision).
*   **Xylar** : Essaim agressif (+1 mouvement, +5% d'attaque).

### 3.7 Bonus Passifs (Héros & Technologies)
*   *Commander Vance* : +15% de dégâts.
*   *Architect Kael* : Réduction des coûts technologiques.
*   *High Elara* : Bonus de revenus de crédits.
*   *High Seer Nix* : Réparation automatique de la flotte.

### 3.8 Conditions de Victoire
Le jeu propose quatre manières de l'emporter :
1.  **Victoire Technologique** : Débloquer 6 technologies.
2.  **Victoire Économique** : Accumuler 500 crédits.
3.  **Victoire Territoriale (Céleste)** : Contrôler tous les Nœuds du Zodiaque.
4.  **Victoire au Score** : Avoir le plus de crédits au 60ème tour.

### 3.9 Sauvegarde & Reprise
Le jeu sauvegarde automatiquement via `SaveManager`. L'état inclut un système de **versioning**. Le gestionnaire utilise une **écriture atomique** (fichier temporaire + renommage) pour garantir l'intégrité, avec un "Ring Buffer" de 3 fichiers.

## 4. Design System (Graphic Noir Futurism)

L'UI utilise Jetpack Compose :
*   **Visuels Unités** : Dessins vectoriels complexes via `Path` (Dreadnought massif, Plateforme de défense statique, etc.).
*   **Planètes** : Rendu avec anneaux orbitaux techniques et bordures de faction.
*   **HUD "Technical Blueprint"** : Effet de ligne de scan radar animée, affichage des coordonnées de secteurs (`q, r`) sur les zones explorées en police monospace.
*   **Audio & Effets** : L'architecture découplée (`GameEffect` SharedFlow) déclenche les sons et notifications dynamiquement via `AudioManager`.

## 5. Statut Actuel du Projet

**Statut : Prototype "Vertical Slice" Stabilisé et Équilibré.**

### Ce qui est implémenté :
- [x] Moteur séquentiel robuste avec `GameEffect` pour les "side-effects".
- [x] Sauvegardes atomiques et versionnées.
- [x] 6 Factions asymétriques entièrement jouables (IA comprise).
- [x] IA tactique complète (Siège, Capture, Recrutement Héros, Bonus de faction).
- [x] HUD Blueprint et visuels vectoriels stylisés.
- [x] Conditions de victoire multiples (Économie, Zodiaque, Tech, Score).
