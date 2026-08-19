# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

*Nova Empire* — a turn-based 4X Android game (Kotlin + Jetpack Compose). Gradle project name is `NovaEmpire`; the working directory is `NovaConquest`. Authoritative design and architecture docs are `ARCHITECTURE.md` (French) and `DESIGN.md`. `suggestions.md` is an audit/wishlist — not a description of the current code.

## Common commands

The CI workflow uses the system `gradle` binary (not the wrapper). Both work locally:

```powershell
# Build the debug APK
./gradlew :app:assembleDebug

# Run all core unit tests (the pure-Kotlin modules; what CI gates on)
./gradlew :core:hex:test :core:domain:test :core:engine:test

# Run tests for a single module
./gradlew :core:engine:test

# Run a single test class
./gradlew :core:engine:test --tests "com.novaempire.core.engine.CombatResolverTest"

# Run a single test method
./gradlew :core:engine:test --tests "com.novaempire.core.engine.CombatResolverTest.someTest"

# Android instrumented tests (requires emulator/device)
./gradlew :app:connectedDebugAndroidTest

# Release APK (signed in CI; locally unsigned unless KEYSTORE_* env vars are set)
./gradlew :app:assembleRelease
```

**The style gate is Spotless + ktlint, and it ratchets.** `gradle spotlessCheck` runs in CI and fails for real — unlike the two steps that used to carry these names wrapped in `|| echo "... not configured, skipping"`. `spotlessApply` fixes most of what it finds.

`ratchetFrom("origin/main")` is the load-bearing part: only files that differ from `main` are checked. A full run reports ~585 violations across 135 files, so without the ratchet the first green build would require reformatting the whole repository — rewriting the blame of every line — or turning the check off. With it, the debt is paid per file, by whoever was already editing that file. Two consequences:

- **CI checks out with `fetch-depth: 0`** and re-fetches `main` explicitly. A shallow clone has no `origin/main`, and Spotless then fails instead of checking anything.
- **Touching an old file may cost more than the change itself.** 217 lines exceed the 120-column limit and 36 wildcard imports remain, concentrated in `MainActivity.kt`, `TacticalMapScreen.kt` and the older screens. Neither is auto-fixable. Budget for it, or split the formatting into its own commit so the behavioural diff stays readable.

Which rules apply is set in `.editorconfig`, and each disabled rule was disabled against a measurement rather than a preference: the default `ktlint_official` style reported 4 723 violations, almost all from wrapping rules that would have rewritten every Compose call in the project. `function-naming` is off because a `@Composable` is PascalCase by convention and the rule counts 51 false positives. A check nobody can satisfy gets switched off at the first blocker; this one is tuned to survive.

**Detekt is still not wired in.** It needs a generated baseline to keep existing debt from blocking every push, and a baseline written without running the tool is worthless. It is its own change.

## Module graph and dependency direction

Four Gradle modules with a strict one-way dependency chain — keep edges going downward, never upward:

```
:app  ──►  :core:engine  ──►  :core:domain  ──►  :core:hex
            │                      │
            └──────────────────────┴──►  :core:hex
```

- **`:core:hex`** — pure Kotlin. Cube-coordinate hex math (`HexCoord`), A* (`HexPathfinder`), and `HexLayout` — the pixel↔hex geometry *and* the inverse of the map camera (pan/zoom), kept here so it falls under the JVM tests CI runs rather than living untested in the Compose screen. No dependencies on other project modules.
- **`:core:domain`** — pure Kotlin (`java-library`). Immutable data models (`Faction`, `UnitType`, `TerrainType`, `Hero`, `MapArchetype`, `TechNode`, `GalacticEvent`, `HexTile`, `GameMap`) and the immutable `GameState` / `PlayerState` shape. Also registries: `TechRegistry`, `HeroRegistry`. Uses `kotlinx.serialization`.
- **`:core:engine`** — pure Kotlin. The reducer + side-effect machinery. Houses `GameEngine` (StateFlow holder), `MapFactory`, `CombatResolver`, `VisionSystem`, `UtilityEvaluator` (AI), `GameGridMap` (adapter so `HexPathfinder` can query the live `GameState`), and `save/` (`SaveManager`, `SavedGameSnapshotCodec`, `SaveMigrations`). Rules the UI would otherwise duplicate also live here: `MapInteraction` (what tapping a hex means), `MovementCalculator` / `FleetActions` (what a fleet may still do), `UndoHistory`. **No Android imports.** Tests against this module run on the JVM and are what CI actually exercises.
- **`:app`** — the only Android module. Single-activity Compose UI (`MainActivity`), per-screen Composables under `ui/screens/`, shared widgets under `ui/components/`, theming under `ui/theme/`, `audio/AudioManager`, and `ui/viewmodels/GameViewModel` (`AndroidViewModel`) which owns a `GameEngine` and a `SaveManager`. Auto-saves on every `EndTurn`.

**Animation goes through `ui/components/Motion.kt`, never through a hard-coded duration.** `AppSettings.reducedMotion` is only honoured if every duration passes through `motionMillis` / `motionDelay` (which return 0 when it is set, so the animation still *runs* — same states, same transitions — it just lands on the first frame), and continuous loops go through `rememberMotionLoop`, which drops the `InfiniteTransition` entirely rather than setting its duration to zero (a zero-duration `infiniteRepeatable` spins every frame for a still image). The setting is forced on when the OS reports `ANIMATOR_DURATION_SCALE == 0`, resolved once in `NovaEmpireTheme` — consumers read `LocalDisplaySettings`, not the raw `AppSettings`. It is distinct from `holographicEffects`, which only governs decoration (panel blur, film grain, map sweep).

Gameplay animation state lives in `TacticalMapScreen` and is fed from two places: `GameEffect` flows for one-off events the engine reports (`combatEvents`, `shakeEvents`), and diffs of successive `GameState`s for everything else. The split is not a style choice — an AI turn publishes exactly one state, at the end, because every intermediate state has the *AI* as `activeFaction` and the HUD reads that faction's credits, fog and colour from it. So AI moves, planet captures and sieges are only ever visible as a diff, while AI combats are collected during the turn (`AiCombat`) and replayed on the effects flow afterwards, once the state has settled.

Two obligations follow, and both have already been the source of bugs:

- **Filter against `visibleHexes`.** An animation drawn from an unobserved hex leaks the fog of war: `visiblePathSuffix` trims a movement trail to its visible tail, and `replayAiCombats` drops a fight neither of whose ends the player can see. (A shot with *one* visible end is shown on purpose — a ship that fires reveals itself.)
- **Clear diff-driven animation state in a `finally`, guarded by reference identity.** A new state cancels the running `LaunchedEffect`, and a half-finished animation otherwise leaves fleets permanently undrawn or hexes permanently veiled.

Pacing of the AI combat replay is not the engine's business: `_effects` has no buffer, so each `emit` waits for the UI to finish animating the previous one. Back-pressure is the clock — never a `delay` in the engine matched to a UI constant.

JVM target is 21 across every module (`jvmToolchain(21)` / `VERSION_21`). AGP `9.2.1`, `compileSdk/targetSdk` 34, `minSdk` 26, Compose BOM `2024.05.00`. Kotlin is `2.2.10` for the Android module (`:app`) plus the Compose plugin (`org.jetbrains.kotlin.plugin.compose`, so there is no `kotlinCompilerExtensionVersion`), and `1.9.23` for the pure-JVM modules and the serialization plugin — the two Kotlin plugin versions coexist because Gradle isolates each subproject's plugin classpath. AGP 9.x needs Gradle ≥ 9.6: the wrapper pins `9.6.1`, and CI runs the system `gradle` (also 9.6.x), not `./gradlew`.

## Unidirectional data flow — the single most important pattern

Every state change goes through the same pipeline:

1. UI dispatches a `GameIntent` (sealed class at the bottom of `core/engine/GameEngine.kt`).
2. `GameEngine.processIntent` calls `reduce(currentState, intent)` — a **pure** function returning a new `GameState`.
3. The new state replaces the `MutableStateFlow`, Compose observes it.

Implications for any change you make:
- **Never mutate `GameState` or anything reachable from it.** Always `.copy(...)`. The maps inside (`units`, `tiles`, `playerStates`) are read-only — go through `toMutableMap()` → mutate → assign back.
- **One-off effects travel on `GameEngine.effects`, never in state.** A reducer reports them through `GameResult` (`notification`, `combatEvent`), the engine turns them into `GameEffect`s and emits them on the `SharedFlow`. Do not add a field to `GameState` for something that happens once: `CombatEvent` used to live there, and because it carries only three fields, two identical attacks in a row produced an *equal* value — so the second one silently played with no animation, no sound and no notification. `GameEffect` is a sealed class and the UI's `when` over it is exhaustive, so the compiler will point you at every site when you add a variant.
- **End-turn is the one async path.** `EndTurn` runs AI turns sequentially on `Dispatchers.Default`, flipping `isAiThinking` around the work; all other intents reduce synchronously. If you add an intent that must call into `UtilityEvaluator` or other long work, follow the existing `scope.launch { withContext(Dispatchers.Default) { ... } }` shape rather than blocking the reducer.
- **Adding a new intent**: add the `data class`/`object` to the `GameIntent` sealed class, add a `when` branch in `GameEngine.reduce`, and (if it changes visibility/positions) call `updateVision(...)` before returning. `updateVision(state, factions)` takes an optional set of factions to recompute — pass only the faction(s) whose units/vision-tech actually changed (default is all factions; used by init and the AI end-turn loop). The `when` is exhaustive over the sealed class, so the compiler will tell you if you miss a branch.

## Engine systems — where the cross-cutting logic lives

- **Pathfinding** runs against `GameGridMap`, an adapter that wraps the current `GameState` so `HexPathfinder` can ask "is this hex passable?" without depending on domain models. If you add a new impassable terrain type or a new blocking condition, edit `GameGridMap`, not the pathfinder.
- **Fog of war** is recalculated by `VisionSystem.calculateVisibleHexes` after every position/ownership change. `updateVision` lives in `GameEngine`, writes back `visibleHexes` and `exploredHexes` on each `PlayerState`, and recomputes only the factions passed to it (see "Adding a new intent"). The UI dims explored-but-not-visible tiles and hides enemy units in them.
- **Map connectivity is guaranteed.** `MapFactory.generateMap` runs a BFS pass that carves asteroid corridors (`ASTEROIDS` → `EMPTY`) until every spawn point and planet sits in one passable region, so a ship can always move and reach objectives regardless of seed. The two symmetric spawn coords come from `MapFactory.spawnPointsFor(radius)` (used by both the factory and `GameEngine.createInitialState`) and are forced to `PLANET`. Asteroids are the only impassable terrain, so clearing a hex line is enough to connect a region — if you add a new impassable terrain, update the carve logic.
- **Combat** flows through `CombatResolver.resolveCombat(state, attacker, defender)`, which returns a `CombatOutcome` — the new state *and* the `CombatEvent` to animate, kept apart on purpose (see the note on one-off effects). Attacker hits first; survivor retaliates only if the attacker is within *its* range; 0 HP removes the unit.
- **Hero/Tech bonuses are applied at the math sites, not as buffs.** Adding a new hero or tech effect means editing the system that owns the calculation (e.g. `CombatResolver` for damage, `TechRegistry.calculateCost` for cost reduction, `VisionSystem` for sight range, `GameEngine.reduce` end-turn block for healing). Grep for existing hero ids (`hero_nix`, `hero_kael`, `hero_elara`, `hero_vance`) to see the pattern.
- **AI turns** are produced entirely by `UtilityEvaluator.executeAITurn(state, faction)` returning a new state. It is an `object` (singleton); the audit recommends DI but that hasn't been done yet.
- **Themes** straddle two modules. `:core:domain/theme` owns everything pure and tested — `ThemeType`, the `@Serializable` theme model, `ThemeParser`, `HexColor`, and `ThemeResolver` (preference × seasonal calendar). `:app` owns only what needs Android: reading `assets/themes/<type>.json`, building the `ColorScheme` and `MapPalette`, and `SettingsStore` (SharedPreferences). The theme is **not** part of `GameState` — it's one field of `AppSettings`, a user preference exposed by `GameViewModel.settings`, where `theme == null` means "seasonal". Resolve it once in `NovaEmpireTheme`; consumers read `LocalThemeType` / `LocalGraphicsConfig` / `LocalMapPalette` / `LocalDisplaySettings` rather than calling `ThemeManager` themselves — that includes the map draw helpers, which take the palette as a parameter since a `DrawScope` cannot read a CompositionLocal. A theme JSON has three sections: `colors` (Material roles), `graphics` (render knobs), `terrain` (map palette, optional — every field defaults to the historical colour). Adding a theme = one `ThemeType` entry + one JSON; `ShippedThemesTest` in `:core:domain` validates the files CI ships. Faction colours are deliberately *not* themed.

## Save format and migrations

`SaveManager` uses a 3-slot ring buffer (`autosave_1.json` → `_2` → `_3`) in `<filesDir>/saves/`. Encoding/decoding goes through `SavedGameSnapshotCodec` using `kotlinx.serialization`. On a corrupt load the file is moved to `saves/quarantine/` and the next slot is tried. **There is a migration layer** — `save/SaveMigrations.kt`. `decode` reads the raw JSON's `version` first (absent = pre-versioning, treated as the oldest supported schema), runs the ordered `SaveMigration` steps, then deserialises; a save newer than `CURRENT_VERSION` raises `SaveVersionException` rather than being silently mangled. `SaveMigrations.ALL` is still empty, and `SaveMigrationsTest` proves the mechanism with synthetic steps so the first real breaking change has something to lean on. Even so, prefer defaults / `@Transient` for new fields: a defaulted field costs nothing, a migration step is code to be maintained for ever.

## CI behaviour worth knowing

- `concurrency: cancel-in-progress: true` per ref — pushing a new commit kills the previous run.
- The `release-build` job runs on `main` and on manual dispatch with `build_release=true`. Without `KEYSTORE_BASE64` and friends, it auto-generates an ephemeral RSA key with **1-day validity** — fine for smoke-testing, useless for distribution.
- Instrumented tests (`android-test` job) only run on `main`.
- `workflow_dispatch` exposes `skip_tests: true` to bypass lint+unit tests in an emergency.
- Branches matching `claude/**` are wired up to run CI on push.
