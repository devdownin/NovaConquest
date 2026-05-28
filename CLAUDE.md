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

Lint gates in `.github/workflows/ci.yml` call `gradle spotlessCheck` and `gradle detekt` but both are wrapped with `|| echo "... not configured, skipping"` — neither is actually configured in the Gradle build. Don't add a "spotless/detekt failure" diagnosis without first checking whether the plugin is applied.

## Module graph and dependency direction

Four Gradle modules with a strict one-way dependency chain — keep edges going downward, never upward:

```
:app  ──►  :core:engine  ──►  :core:domain  ──►  :core:hex
            │                      │
            └──────────────────────┴──►  :core:hex
```

- **`:core:hex`** — pure Kotlin. Cube-coordinate hex math (`HexCoord`) and A* (`HexPathfinder`). No dependencies on other project modules. Anything Android-free and game-agnostic lives here.
- **`:core:domain`** — pure Kotlin (`java-library`). Immutable data models (`Faction`, `UnitType`, `TerrainType`, `Hero`, `MapArchetype`, `TechNode`, `GalacticEvent`, `HexTile`, `GameMap`) and the immutable `GameState` / `PlayerState` shape. Also registries: `TechRegistry`, `HeroRegistry`. Uses `kotlinx.serialization`.
- **`:core:engine`** — pure Kotlin. The reducer + side-effect machinery. Houses `GameEngine` (StateFlow holder), `MapFactory`, `CombatResolver`, `VisionSystem`, `UtilityEvaluator` (AI), `GameGridMap` (adapter so `HexPathfinder` can query the live `GameState`), and `save/` (`SaveManager`, `SavedGameSnapshotCodec`). **No Android imports.** Tests against this module run on the JVM and are what CI actually exercises.
- **`:app`** — the only Android module. Single-activity Compose UI (`MainActivity`), per-screen Composables under `ui/screens/`, shared widgets under `ui/components/`, theming under `ui/theme/`, `audio/AudioManager`, and `ui/viewmodels/GameViewModel` (`AndroidViewModel`) which owns a `GameEngine` and a `SaveManager`. Auto-saves on every `EndTurn`.

JVM target is 17 across every module. Compose compiler 1.5.11, Kotlin 1.9.23, AGP 8.4.1, `compileSdk/targetSdk` 34, `minSdk` 26. The Compose BOM is `2024.05.00`.

## Unidirectional data flow — the single most important pattern

Every state change goes through the same pipeline:

1. UI dispatches a `GameIntent` (sealed class at the bottom of `core/engine/GameEngine.kt`).
2. `GameEngine.processIntent` calls `reduce(currentState, intent)` — a **pure** function returning a new `GameState`.
3. The new state replaces the `MutableStateFlow`, Compose observes it.

Implications for any change you make:
- **Never mutate `GameState` or anything reachable from it.** Always `.copy(...)`. The maps inside (`units`, `tiles`, `playerStates`) are read-only — go through `toMutableMap()` → mutate → assign back.
- **One-off effects are currently stored in state** (e.g. `CombatEvent`) and cleared by the UI after consumption — see the audit note in `suggestions.md`. Don't be surprised by this; `SharedFlow` is the intended future direction but isn't wired up.
- **End-turn is the one async path.** `EndTurn` runs AI turns sequentially on `Dispatchers.Default`, flipping `isAiThinking` around the work; all other intents reduce synchronously. If you add an intent that must call into `UtilityEvaluator` or other long work, follow the existing `scope.launch { withContext(Dispatchers.Default) { ... } }` shape rather than blocking the reducer.
- **Adding a new intent**: add the `data class`/`object` to the `GameIntent` sealed class, add a `when` branch in `GameEngine.reduce`, and (if it changes visibility/positions) call `updateVision(...)` before returning. The `when` is exhaustive over the sealed class, so the compiler will tell you if you miss a branch.

## Engine systems — where the cross-cutting logic lives

- **Pathfinding** runs against `GameGridMap`, an adapter that wraps the current `GameState` so `HexPathfinder` can ask "is this hex passable?" without depending on domain models. If you add a new impassable terrain type or a new blocking condition, edit `GameGridMap`, not the pathfinder.
- **Fog of war** is recalculated by `VisionSystem.calculateVisibleHexes` after **every** position/ownership change. `updateVision` lives in `GameEngine` and writes back `visibleHexes` and `exploredHexes` on each `PlayerState`. The UI dims explored-but-not-visible tiles and hides enemy units in them.
- **Combat** flows through `CombatResolver.resolveCombat(state, attacker, defender)` and returns a new state with a `CombatEvent` set for the UI to animate. Attacker hits first; survivor retaliates; 0 HP removes the unit.
- **Hero/Tech bonuses are applied at the math sites, not as buffs.** Adding a new hero or tech effect means editing the system that owns the calculation (e.g. `CombatResolver` for damage, `TechRegistry.calculateCost` for cost reduction, `VisionSystem` for sight range, `GameEngine.reduce` end-turn block for healing). Grep for existing hero ids (`hero_nix`, `hero_kael`, `hero_elara`, `hero_vance`) to see the pattern.
- **AI turns** are produced entirely by `UtilityEvaluator.executeAITurn(state, faction)` returning a new state. It is an `object` (singleton); the audit recommends DI but that hasn't been done yet.

## Save format and migrations

`SaveManager` uses a 3-slot ring buffer (`autosave_1.json` → `_2` → `_3`) in `<filesDir>/saves/`. Encoding/decoding goes through `SavedGameSnapshotCodec` using `kotlinx.serialization`. On a corrupt load the file is moved to `saves/quarantine/` and the next slot is tried. **There is no schema migration layer** — adding a non-defaulted field to any `@Serializable` model in `:core:domain` will break every existing save. Use defaults / `@Transient` for new fields unless you intentionally want to invalidate saves.

## CI behaviour worth knowing

- `concurrency: cancel-in-progress: true` per ref — pushing a new commit kills the previous run.
- The `release-build` job runs on `main` and on manual dispatch with `build_release=true`. Without `KEYSTORE_BASE64` and friends, it auto-generates an ephemeral RSA key with **1-day validity** — fine for smoke-testing, useless for distribution.
- Instrumented tests (`android-test` job) only run on `main`.
- `workflow_dispatch` exposes `skip_tests: true` to bypass lint+unit tests in an emergency.
- Branches matching `claude/**` are wired up to run CI on push.
