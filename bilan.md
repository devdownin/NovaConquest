● Synthèse de l'audit

  Bugs corrigés

  Critiques — interactions joueur avec la carte

  1. MainActivity.kt:197 — visibleHexes = emptySet() codé en dur : le brouillard de guerre était cassé, aucune case n'apparaissait "actuellement visible". → branché sur playerStates[activeFaction].visibleHexes.
  2. MainActivity.kt:199 — onOpenSystemManagement = { } vide : le bouton de gestion du système ne faisait rien. → bascule sur l'onglet SYSTEM avec la coord sélectionnée.
  3. MainActivity.kt:205 — GameTab.SYSTEM utilisait tiles.keys.first() au lieu de la coord sélectionnée par le joueur. → utilise le selectedCoord propagé depuis la carte, fallback sur capitale puis 1ʳᵉ planète possédée.
  4. TacticalMapScreen.kt — onSiegePlanet / onCapturePlanet déclarés mais jamais appelés. → boutons contextuels sur le panneau latéral quand une unité amie est adjacente à une planète ennemie, et drag-to-siege si glisser sur planète
  ennemie adjacente.
  5. TacticalMapScreen.kt — onHexClick / onClearSelection déclarés mais jamais appelés. → appelés sur tap/désélection.
  6. TacticalMapScreen.kt:114-119 — Impossible de désélectionner (taps sur cases non-explorées ignorés). → tap sur la case déjà sélectionnée la déselectionne, bouton X dans le panneau aussi.
  7. TacticalMapScreen.kt:167 — Distance de drag limitée à maxCost = 4 codé en dur, identique pour toutes les unités. → utilise unit.type.movement.
  8. GameEngine.kt:189 — MoveUnit utilisait unit.type.attack comme limite de mouvement. Un Battleship (atk=8) pouvait bouger 8 cases, un Scout (atk=2) seulement 2. → ajout du champ movement à UnitType (Scout=5, Fighter=4, Cruiser=3,
  Battleship=2, Carrier=3) ; MoveUnit et UtilityEvaluator corrigés.
  9. UtilityEvaluator.kt:48 — Même bug pour l'IA. Corrigé idem.
  10. TacticalMapScreen.kt:158 — currentHoveredHex jamais réinitialisé à la fin du drag → état périmé. Corrigé dans onDragEnd/onDragCancel.
  11. TacticalMapScreen.kt:124-153 — Deux pointerInput(gameState) séparés réinitialisés à chaque changement d'état du jeu (cancel du geste en cours pendant une animation/IA). → changé en pointerInput(Unit), l'état est lu via les
  mutableStateOf.
  12. Action panel "Move / Defend / Scout" — handlers vides (/* Move */). → supprimés, remplacés par actions contextuelles utiles (Manage System, Siege, Capture).

  HUD

  13. Crédits affichés en dur "145,200 C" → $credits C réel depuis playerStates.
  14. "3 IDLE FLEETS" codé en dur → calcul réel des unités amies sans action restante (+ pluriel correct).
  15. Point indicateur de faction active toujours NeonRed → couleur réelle via getFactionColor().
  16. Menu burger top-left sans handler → ouvre l'Academy.
  17. Icône "Wallet" doublon → remplacée par un spacer.
  18. Sons combat utilisaient END_TURN (commentaire // using available sound) → COMBAT_LASER puis COMBAT_EXPLOSION (les constantes existaient déjà dans SoundType).
  19. Animation d'explosion avec radius = 0 → garde-fou pour éviter le brush dégénéré.
  20. Barre de vie ajoutée sous chaque vaisseau, opacité réduite pour unités épuisées.

  Texte / interpolation

  21. 6 fichiers avaient des \${...} échappés affichant littéralement ${...} à l'écran : TacticalMapScreen (SECTOR), CombatPreviewScreen (tous les stats), TechTreeScreen (crédits + cost RESEARCH), DiplomacyIntelScreen (TURN, STATUS,
  rank), CampaignArchiveScreen (faction/status/score), BlackMarketScreen (price), AudioManager (logs). Tous corrigés.
  22. DiplomacyIntelScreen.kt:78 — "...UNKNOWN\\nCAPITAL..." (littéral \n) → vrai retour ligne \n.

  Combat / robustesse

  23. combatPreviewData ne vérifiait pas que les unités existaient encore au moment de confirmer (race possible avec IA) → si null, la preview se ferme proprement.

✻ Coms iting conversation… corr8gés (hors scope)
✶      c  n                                 3
  - Spotless/Detekt configurés || echo skip dans CI mais aucun plugin Gradle appliqué.
  - Spaetinager sans couche de 6igration : un nouveau champ non-default dans GameState casserait toutes les sauvegardes existantes (déjà documenté dans CLAUDE.md).
* - minch-to-zoom n'utilise pas le centroïde — zoom ancré sur (0,0) du layout.
  - Seuls Dominion/Traders ont des spawn points ; les 4 autres factions ont un playerStates sans capitale, donc ne produisent jamais d'unité.
  - IndustrialButton force Modifier.fillMaxWidth() en interne, ce qui annule un Modifier.width(...) passé en argument.

---

## Audit du 2026-05-28 — Génération de cartes, mobilité des vaisseaux & performances

### Corrigés

  Critiques

  1. `MapFactory` — aucune garantie de connexité : ~13,5 % de cases étaient des astéroïdes infranchissables placés sans contrôle, un vaisseau pouvait naître encerclé ou la carte être coupée en régions isolées. → ajout d'un parcours BFS qui creuse des couloirs (Astéroïdes → Vide) jusqu'à ce que tous les spawns et planètes forment une seule région franchissable. Garantit « un déplacement toujours possible ». Couvert par `MapFactoryTest` (50 seeds × radii 3/5/8/12 + Zodiac).
  2. `UtilityEvaluator.kt` — l'IA ne rejoignait jamais un ennemi distant : `findPath(start, enemyPos)` ciblait une case occupée donc infranchissable → A* renvoyait toujours `null`. → l'IA vise désormais une case franchissable adjacente à l'ennemi, path sans plafond, puis tronque au budget de mouvement.

  Majeurs

  3. `HexPathfinder.kt:44` — heuristique A* erronée (`current.distanceTo(goal)` au lieu de `next.distanceTo(goal)`) → A* dégradé en quasi-Dijkstra, sortie anticipée potentiellement sous-optimale. Corrigé. Couvert par `HexPathfinderTest`.
  4. `GameEngine.createInitialState` — les unités de départ apparaissaient sur les 2 premières planètes dans l'ordre d'itération de la map, rendant morte la logique de spawns symétriques de `MapFactory`. → utilise désormais `MapFactory.spawnPointsFor(radius)`.
  5. `GameEngine.updateVision` — recalculait les 7 factions à chaque mutation (poste de coût moteur dominant). → signature `updateVision(state, factions)` ; ne recalcule que les factions concernées (active pour move/build/research, attaquant + défenseur pour un combat).

  Mineurs

  6. `MapFactory` — `random.nextDouble()` chaîné dans le `when` : chaque branche tirait un nouveau nombre, faussant la distribution. → tirage unique + buckets cumulatifs.

  Performances UI

  7. `TacticalMapScreen` — le chemin fantôme (drag) et les FX de combat partageaient le même `Canvas` que le terrain, ré-exécutant la boucle de dessin de toutes les tuiles à chaque frame de drag / tick d'animation. → scindé en deux Canvas superposés (terrain statique + overlay dynamique) dans le même `Box` transformé.

### Hors scope (signalé, non corrigé)

  - 4 factions sur 6 (Synth/Nomads/Kaelen/Xylar) restent inertes : pas de capitale ni d'unités instanciées — décision de design, pas un bug.
  - Garantie de mobilité : elle porte sur le **terrain** (région connexe + spawn jamais encerclé). Un blocage *tactique* par d'autres unités reste possible et relève du gameplay.

## Audit - Bug fixes & UI optimisations

  - **Bug in `HexCoord` rounding:** `java.lang.Math.round` was incorrectly rounding negative numbers like -0.5 to 0.0 (half-up) instead of half-to-even, creating asymmetry in hexagonal tie-breaking. Replaced all occurrences in `HexCoord.kt` and `TacticalMapScreen.kt` with `kotlin.math.roundToInt()`.
  - **Dead Code / UI simplification:** Cleaned up unused `else ->` block inside `TacticalMapScreen` `drawUnit()` function, removed unreachable warning branches.
  - **Replaced `Math.PI`:** Adjusted standard `Math.PI` calls to `kotlin.math.PI`.
  - **Adjusted Top Navigation Bar:** Adjusted the `padding` to match `8.dp` vertical padding, maintaining symmetry across UI bars as per design guidelines.

## Graphics Enhancements (Graphic Noir Futurism)

  - **UI Frosted Glass Effect:** Added a `BlurEffect` via `graphicsLayer` rendering to `IndustrialPanel` to simulate frosted glass on the HUD elements for devices running Android S and above.
  - **Unit Details:** Upgraded the custom Canvas vector shapes in `drawUnit` with more technical components (antennas, sensor dishes, engine glow, thrusters, and cockpit glass) following the comic-book style lines and proportions.
  - **Particle System:** Created a `ParticleSystem` and wired it into `TacticalMapScreen` `activeCombatEvent` to generate sparks and debris using a `LaunchedEffect` game loop when combats occur, instead of basic animated circles.
  - **Comic-book Style Inking:** Added heavy, textured comic-book style outlines (`StrokeWidth = 4f, StrokeCap.Round, StrokeJoin.Round`) to planets, replacing simple radial gradients with cross-hatching shading.
  - **Typography (Monospace):** Replaced default Inter/Rajdhani fallback fonts with `FontFamily.Monospace` to increase the technical, industrial look across coordinate overlays and the entire HUD.

## Theming Architecture (Graphic Noir Futurism)

  - **Theme Config & State:** Added a `ThemeConfig` data model directly inside `GameState` holding the `currentTheme` (`DEFAULT`, `HALLOWEEN`, `WINTER`).
  - **Dynamic Theme Loading:** Built `ThemeManager` inside `:app` exposing `getColorSchemeForTheme` to retrieve alternate dark color palettes (`HALLOWEEN` and `WINTER`) alongside the `DEFAULT`.
  - **Automatic Date Detection:** Implemented `getActiveTheme` which falls back to reading the system calendar (October 25 to Nov 5 for Halloween, Dec 20 to Jan 5 for Winter) if the state's `currentTheme` is `DEFAULT`. The state will now pass the resolved theme to the UI's `NovaEmpireTheme(themeType = ...)` wrapper in `MainActivity`.
  - **Environment & Unit Renderers:** Introduced `UnitRenderer` and `EnvironmentRenderer` interfaces to abstract away the direct drawing logic. This provides the architectural foundation necessary to later swap out the current "Canvas Vector" pack with external `VectorDrawable` XMLs or rasterized "Texture Packs" dynamically.
  - **Faction Cosmestics:** Added distinct geometry-based emblems for every faction (`FactionCosmetics`) which are drawn on top of owned planets and on the hull of Dreadnought units to break faction visual symmetry.
  - **Biomes & Parallax:** Implemented `BackgroundRenderer` that adds depth via scrolling parallax stars. The background adapts based on the Map Archetype (adding a purple nebula layer for ZODIAC maps).
  - **Dynamic Combat Impacts:** Introduced screen shake (`cameraShakeX`, `cameraShakeY`) when an attack lands. The shake intensity doubles if the target is destroyed. Weapon fire duration varies based on the unit type (e.g. 500ms heavy lasers for Dreadnoughts, 200ms quick bursts for smaller ships).
  - **JSON Theming Architecture:** Themes are now completely decoupled from hardcoded Kotlin strings and extracted into the `assets/themes/` directory as editable `JSON` files.
  - **Graphics Config Extraction:** Along with colors, visual multipliers (such as `outlineStrokeWidth`, `blurRadius`, `planetShadowAlpha` and `particleCountMultiplier`) have been extracted into the JSON theme file for live adjustment by artists.
  - **Graphic Designer Documentation:** Rewrote `THEME_GUIDE_FR.md` to instruct graphic designers on using the `Material Theme Builder` web tool, generating `.json` files, and tweaking game visual effects without touching the codebase.
