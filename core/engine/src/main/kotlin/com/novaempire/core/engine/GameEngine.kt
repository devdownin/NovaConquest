package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.MapSize
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.domain.models.GameUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class GameResult(val newState: GameState, val error: String? = null, val notification: String? = null)

sealed class GameEffect {
    data class PlaySound(val soundId: String) : GameEffect()
    data class ShowNotification(val message: String, val color: String = "CYAN") : GameEffect()
    object ShakeCamera : GameEffect()
}

class GameEngine(private val deps: GameEngineDependencies = GameEngineDependencies()) {

    constructor(aiStrategy: AIStrategy) : this(GameEngineDependencies(aiStrategy = aiStrategy))

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val intentChannel = Channel<GameIntent>(Channel.UNLIMITED)

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    /** Only touched from the single coroutine draining [intentChannel], so it needs no locking. */
    private val undoHistory = UndoHistory()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _state = MutableStateFlow(createInitialState(MapSize.MEDIUM, MapArchetype.STANDARD))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _effects = MutableSharedFlow<GameEffect>()
    val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    init {
        scope.launch {
            intentChannel.receiveAsFlow().collect { intent ->
                handleIntent(intent)
            }
        }
    }

    private fun createInitialState(mapSize: MapSize, archetype: MapArchetype): GameState {
        // Draw a fresh seed from the injected RNG so every new game produces a different
        // galaxy. Without this the factory falls back to its default fixed seed and every
        // party — STANDARD or ZODIAC — would generate the exact same map. Tests inject a
        // deterministic Random, keeping map generation reproducible where it matters.
        val map = MapFactory.generateMap(radius = mapSize.radius, archetype = archetype, seed = deps.rng.nextLong())
        val spawnPoints = MapFactory.spawnPointsFor(mapSize.radius).filter { map.tiles.containsKey(it) }
        val units = mutableMapOf<HexCoord, GameUnit>()
        val playerStates = mutableMapOf<Faction, PlayerState>()
        val spawnOwners = mutableMapOf<HexCoord, Faction>()

        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        activeFactions.forEachIndexed { index, faction ->
            val spawnPoint = spawnPoints.getOrNull(index)
            if (spawnPoint != null) {
                units[spawnPoint] = GameUnit(
                    type = if (faction == Faction.DOMINION) UnitType.CRUISER else UnitType.SCOUT,
                    faction = faction,
                    position = spawnPoint,
                    currentHp = if (faction == Faction.DOMINION) UnitType.CRUISER.maxHp else UnitType.SCOUT.maxHp
                )
                spawnOwners[spawnPoint] = faction
            }
            playerStates[faction] = PlayerState(faction = faction, capitalCoord = spawnPoint, credits = 100)
        }

        val updatedTiles = map.tiles.toMutableMap()
        spawnOwners.forEach { (coord, faction) ->
            updatedTiles[coord]?.let { tile ->
                if (tile.terrain == TerrainType.PLANET)
                    updatedTiles[coord] = tile.copy(owner = faction)
            }
        }

        val initialState = GameState(map = map.copy(tiles = updatedTiles), units = units, playerStates = playerStates)
        return updateVision(initialState)
    }

    private suspend fun checkVictoryConditions(state: GameState): GameState {
        val result = VictoryChecker.check(state) ?: return state
        // Record a completed campaign mission. `completedMissions` existed on CampaignState but was
        // never written, so finishing a mission left no trace: nothing to unlock, nothing to show.
        val missionId = state.campaignState.activeMissionId
        val mission = missionId?.let { id ->
            com.novaempire.core.domain.models.CampaignRegistry.MISSIONS.find { it.id == id }
        }
        val campaign = if (mission != null && result.winner == mission.playerFaction)
            state.campaignState.copy(completedMissions = state.campaignState.completedMissions + mission.id)
        else state.campaignState
        val finalState = state.copy(
            winner = result.winner,
            victoryReason = result.reason,
            campaignState = campaign
        )
        val banner = result.winner?.let { "VICTORY: ${it.displayName} — ${result.reason}" }
            ?: "MATCH NUL — ${result.reason}"
        _effects.emit(GameEffect.ShowNotification(banner, "GOLD"))
        return finalState
    }

    fun processIntent(intent: GameIntent) {
        intentChannel.trySend(intent)
    }

    private fun pushUndo(state: GameState) {
        undoHistory.record(state)
        _canUndo.value = undoHistory.canUndo
    }

    private fun clearUndo() {
        undoHistory.clear()
        _canUndo.value = false
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun handleIntent(intent: GameIntent) {
        if (_isAiThinking.value &&
            intent !is GameIntent.LoadGame &&
            intent !is GameIntent.StartNewGame &&
            intent !is GameIntent.StartNewGameWithSize &&
            intent !is GameIntent.StartCampaign &&
            intent !is GameIntent.SelectFaction
        ) {
            _errors.emit("AI is thinking, please wait.")
            return
        }

        // Placed *after* the AI guard on purpose: rolling back mid-round would hand the player a
        // state from before the AI moved. Today `EndTurn` also empties the history on entry, so
        // the stack would be empty anyway — but relying on that would make the safety of this
        // branch depend on where an unrelated call happens to sit.
        if (intent is GameIntent.Undo) {
            val previous = undoHistory.rollback()
            if (previous == null) {
                _errors.emit("Nothing to undo.")
            } else {
                _state.value = previous
                _canUndo.value = undoHistory.canUndo
            }
            return
        }

        if (intent is GameIntent.EndTurn) {
            // The turn is the commit point: once the AI has answered, there is nothing coherent
            // to roll back to.
            clearUndo()
            _isAiThinking.value = true
            val prevState = _state.value
            var currentState = prevState
            currentState = reduce(currentState, intent).newState

            val humanFaction = currentState.humanFaction
            val humanPrev = prevState.playerStates[humanFaction]
            val humanNext = currentState.playerStates[humanFaction]
            val prevResearch = humanPrev?.researchInProgress
            if (prevResearch != null && humanNext?.researchInProgress == null) {
                val name = TechRegistry.getTech(prevResearch.techId)?.name ?: prevResearch.techId
                _effects.emit(GameEffect.ShowNotification("RESEARCH COMPLETE: $name", "CYAN"))
            }

            val prevBuildQueue = prevState.playerStates[humanFaction]?.buildQueue ?: emptyList()
            val nextBuildQueue = currentState.playerStates[humanFaction]?.buildQueue ?: emptyList()
            if (prevBuildQueue.size > nextBuildQueue.size) {
                val count = prevBuildQueue.size - nextBuildQueue.size
                _effects.emit(GameEffect.ShowNotification("$count UNIT${if (count > 1) "S" else ""} READY FOR DEPLOYMENT", "CYAN"))
            }

            if (prevState.activeEvent != currentState.activeEvent &&
                currentState.activeEvent != com.novaempire.core.domain.models.GalacticEvent.NONE) {
                _effects.emit(GameEffect.ShowNotification(eventBanner(currentState), "ORANGE"))
            }

            while (currentState.activeFaction != humanFaction) {
                currentState = try {
                    withContext(Dispatchers.Default) {
                        withTimeout(10_000L) {
                            deps.aiStrategy.executeAITurn(currentState, currentState.activeFaction) { s, i ->
                                reduce(s, i).newState
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    _effects.emit(GameEffect.ShowNotification("IA : tour forcé (délai dépassé)", "ORANGE"))
                    currentState
                }
                // No explicit updateVision here: the reduce(EndTurn) below recomputes vision for
                // every faction via advanceTurn, so an extra full recompute would be pure waste.
                val prevForAI = currentState
                currentState = reduce(currentState, GameIntent.EndTurn).newState
                if (prevForAI.activeEvent != currentState.activeEvent &&
                    currentState.activeEvent != com.novaempire.core.domain.models.GalacticEvent.NONE) {
                    _effects.emit(GameEffect.ShowNotification(
                        "${currentState.activeEvent.displayName}: ${currentState.activeEvent.description}", "ORANGE"
                    ))
                }
            }

            val refreshedUnits = currentState.units.mapValues {
                it.value.copy(hasMoved = false, hasAttacked = false, movementUsed = 0)
            }
            currentState = currentState.copy(units = refreshedUnits)
            _state.value = checkVictoryConditions(currentState)
            _isAiThinking.value = false
        } else {
            val currentState = _state.value
            val result = reduce(currentState, intent)
            if (result.error == null) {
                // An action that uncovered fog forfeits the *entire* history, not just its own
                // step: rolling back to any earlier state would re-hide the same ground and hand
                // the player free reconnaissance one action later.
                val undoable = UndoHistory.isUndoable(intent) &&
                    !UndoHistory.revealsNewTerritory(currentState, result.newState)
                if (undoable) pushUndo(currentState) else clearUndo()
            }
            if (result.error != null) {
                _errors.emit(result.error)
                _effects.emit(GameEffect.PlaySound("UI_CLICK"))
            }
            if (result.notification != null) {
                _effects.emit(GameEffect.ShowNotification(result.notification, "CYAN"))
            }

            val nextState = result.newState
            val combat = nextState.lastCombatEvent
            if (combat != null && (currentState.lastCombatEvent == null || combat != currentState.lastCombatEvent)) {
                _effects.emit(GameEffect.ShakeCamera)
                val attackerName = currentState.units[combat.attackerCoord]?.type?.name ?: "UNIT"
                val defenderName = currentState.units[combat.defenderCoord]?.type?.name ?: "UNIT"
                val outcome = if (combat.targetDestroyed) "$attackerName DESTROYED $defenderName"
                              else "$attackerName HIT $defenderName"
                _effects.emit(GameEffect.ShowNotification(outcome, "RED"))
                _effects.emit(if (combat.targetDestroyed) GameEffect.PlaySound("COMBAT_EXPLOSION")
                              else GameEffect.PlaySound("COMBAT_LASER"))
            }

            _state.value = checkVictoryConditions(nextState)
        }
    }

    /**
     * A fresh board must not erase campaign progress. `createInitialState` builds a brand-new
     * [GameState], whose default `campaignState` is empty — so launching the next mission wiped the
     * record of the previous one. Completed missions (and glory) carry over; the active mission is
     * deliberately cleared, since the new game has not started one yet.
     */
    private fun GameState.keepingCampaignProgress(previous: GameState): GameState = copy(
        campaignState = com.novaempire.core.domain.state.CampaignState(
            activeMissionId = null,
            completedMissions = previous.campaignState.completedMissions,
            gloryPoints = previous.campaignState.gloryPoints
        )
    )

    private fun eventBanner(state: GameState): String {
        val e = state.activeEvent
        val target = state.eventTargetFaction
        return if (target != null) "${e.displayName} → ${target.displayName}: ${e.description}"
               else "${e.displayName}: ${e.description}"
    }

    // ── Reducer dispatcher ────────────────────────────────────────────────────

    internal fun reduce(state: GameState, intent: GameIntent): GameResult = when (intent) {
        is GameIntent.StartNewGame ->
            GameResult(createInitialState(MapSize.MEDIUM, MapArchetype.STANDARD).keepingCampaignProgress(state))
        is GameIntent.StartNewGameWithSize ->
            GameResult(createInitialState(intent.mapSize, intent.archetype).keepingCampaignProgress(state))
        is GameIntent.LoadGame ->
            GameResult(updateVision(intent.loadedState))
        is GameIntent.StartCampaign -> handleStartCampaign(state, intent)
        is GameIntent.EndTurn ->
            GameResult(updateVision(TurnManager.advanceTurn(state)))
        // Handled before the reducer — it replaces the state wholesale rather than deriving one.
        is GameIntent.Undo -> GameResult(state)
        is GameIntent.SelectFaction ->
            GameResult(state.copy(activeFaction = intent.faction, humanFaction = intent.faction))
        is GameIntent.MoveUnit     -> handleMoveUnit(state, intent, deps)
        is GameIntent.AttackUnit   -> handleAttackUnit(state, intent, deps)
        is GameIntent.ResearchTech -> handleResearchTech(state, intent)
        is GameIntent.CancelResearch -> handleCancelResearch(state)
        is GameIntent.BuildUnit    -> handleBuildUnit(state, intent)
        is GameIntent.RecruitHero  -> handleRecruitHero(state, intent)
        is GameIntent.ChangeRelation -> handleChangeRelation(state, intent)
        is GameIntent.SiegePlanet  -> handleSiegePlanet(state, intent, deps)
        is GameIntent.CapturePlanet -> handleCapturePlanet(state, intent, deps)
        is GameIntent.UpgradeSystem -> handleUpgradeSystem(state, intent)
        is GameIntent.CancelBuild  -> handleCancelBuild(state, intent)
        is GameIntent.LoadUnit     -> handleLoadUnit(state, intent)
        is GameIntent.DeployUnit   -> handleDeployUnit(state, intent)
        is GameIntent.UseHeroAbility -> handleUseHeroAbility(state, intent)
    }
}

sealed class GameIntent {
    object EndTurn : GameIntent()

    /** Roll the last player action of this turn back. See `GameEngine.isUndoable`. */
    object Undo : GameIntent()
    data class SelectFaction(val faction: Faction) : GameIntent()
    data class MoveUnit(val from: HexCoord, val to: HexCoord) : GameIntent()
    data class AttackUnit(val attacker: HexCoord, val defender: HexCoord) : GameIntent()
    data class ResearchTech(val techId: String) : GameIntent()
    object CancelResearch : GameIntent()
    data class BuildUnit(val unitType: UnitType, val location: HexCoord? = null) : GameIntent()
    data class RecruitHero(val heroId: String) : GameIntent()
    data class ChangeRelation(val targetFaction: Faction, val newRelation: com.novaempire.core.domain.models.DiplomaticRelation) : GameIntent()
    object StartNewGame : GameIntent()
    data class StartNewGameWithSize(
        val mapSize: MapSize = MapSize.MEDIUM,
        val archetype: MapArchetype = MapArchetype.STANDARD
    ) : GameIntent()
    data class LoadGame(val loadedState: GameState) : GameIntent()
    data class SiegePlanet(val attackerCoord: HexCoord, val planetCoord: HexCoord) : GameIntent()
    data class CapturePlanet(val unitCoord: HexCoord, val planetCoord: HexCoord) : GameIntent()
    data class UpgradeSystem(val coord: HexCoord) : GameIntent()
    data class CancelBuild(val planetCoord: HexCoord) : GameIntent()
    data class LoadUnit(val carrierCoord: HexCoord, val unitCoord: HexCoord) : GameIntent()
    data class DeployUnit(val carrierCoord: HexCoord, val deployCoord: HexCoord, val unitIndex: Int = 0) : GameIntent()
    data class UseHeroAbility(val heroId: String) : GameIntent()
    data class StartCampaign(val missionId: String) : GameIntent()
}
