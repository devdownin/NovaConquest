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

data class GameResult(val newState: GameState, val error: String? = null)

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
        val map = MapFactory.generateMap(radius = mapSize.radius, archetype = archetype)
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
        val finalState = state.copy(winner = result.winner, victoryReason = result.reason)
        _effects.emit(GameEffect.ShowNotification("VICTORY: ${result.winner.displayName} — ${result.reason}", "GOLD"))
        return finalState
    }

    fun processIntent(intent: GameIntent) {
        intentChannel.trySend(intent)
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun handleIntent(intent: GameIntent) {
        if (_isAiThinking.value &&
            intent !is GameIntent.LoadGame &&
            intent !is GameIntent.StartNewGame &&
            intent !is GameIntent.StartNewGameWithSize
        ) {
            _errors.emit("AI is thinking, please wait.")
            return
        }

        if (intent is GameIntent.EndTurn) {
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
                _effects.emit(GameEffect.ShowNotification(
                    "${currentState.activeEvent.displayName}: ${currentState.activeEvent.description}", "ORANGE"
                ))
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
                currentState = updateVision(currentState)
                val prevForAI = currentState
                currentState = reduce(currentState, GameIntent.EndTurn).newState
                if (prevForAI.activeEvent != currentState.activeEvent &&
                    currentState.activeEvent != com.novaempire.core.domain.models.GalacticEvent.NONE) {
                    _effects.emit(GameEffect.ShowNotification(
                        "${currentState.activeEvent.displayName}: ${currentState.activeEvent.description}", "ORANGE"
                    ))
                }
            }

            val refreshedUnits = currentState.units.mapValues { it.value.copy(hasMoved = false, hasAttacked = false) }
            currentState = currentState.copy(units = refreshedUnits)
            _state.value = checkVictoryConditions(currentState)
            _isAiThinking.value = false
        } else {
            val currentState = _state.value
            val result = reduce(currentState, intent)
            if (result.error != null) {
                _errors.emit(result.error)
                _effects.emit(GameEffect.PlaySound("UI_CLICK"))
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

    // ── Reducer dispatcher ────────────────────────────────────────────────────

    internal fun reduce(state: GameState, intent: GameIntent): GameResult = when (intent) {
        is GameIntent.StartNewGame ->
            GameResult(createInitialState(MapSize.MEDIUM, MapArchetype.STANDARD))
        is GameIntent.StartNewGameWithSize ->
            GameResult(createInitialState(intent.mapSize, intent.archetype))
        is GameIntent.LoadGame ->
            GameResult(updateVision(intent.loadedState))
        is GameIntent.EndTurn ->
            GameResult(updateVision(TurnManager.advanceTurn(state)))
        is GameIntent.SelectFaction ->
            GameResult(state.copy(activeFaction = intent.faction, humanFaction = intent.faction))
        is GameIntent.MoveUnit     -> handleMoveUnit(state, intent)
        is GameIntent.AttackUnit   -> handleAttackUnit(state, intent, deps)
        is GameIntent.ResearchTech -> handleResearchTech(state, intent)
        is GameIntent.BuildUnit    -> handleBuildUnit(state, intent)
        is GameIntent.RecruitHero  -> handleRecruitHero(state, intent)
        is GameIntent.ChangeRelation -> handleChangeRelation(state, intent)
        is GameIntent.SiegePlanet  -> handleSiegePlanet(state, intent, deps)
        is GameIntent.CapturePlanet -> handleCapturePlanet(state, intent, deps)
        is GameIntent.UpgradeSystem -> handleUpgradeSystem(state, intent)
        is GameIntent.CancelBuild  -> handleCancelBuild(state, intent)
    }
}

sealed class GameIntent {
    object EndTurn : GameIntent()
    data class SelectFaction(val faction: Faction) : GameIntent()
    data class MoveUnit(val from: HexCoord, val to: HexCoord) : GameIntent()
    data class AttackUnit(val attacker: HexCoord, val defender: HexCoord) : GameIntent()
    data class ResearchTech(val techId: String) : GameIntent()
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
}
