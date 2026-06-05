package com.novaempire.core.engine

import com.novaempire.core.domain.models.*
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameResult(val newState: GameState, val error: String? = null)

sealed class GameEffect {
    data class PlaySound(val soundId: String) : GameEffect()
    data class ShowNotification(val message: String, val color: String = "CYAN") : GameEffect()
    object ShakeCamera : GameEffect()
}

class GameEngine(private val aiStrategy: AIStrategy = UtilityEvaluator) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val intentChannel = Channel<GameIntent>(Channel.UNLIMITED)

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _state = MutableStateFlow(createInitialState(com.novaempire.core.domain.models.MapSize.MEDIUM, com.novaempire.core.domain.models.MapArchetype.STANDARD))
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

    private fun createInitialState(mapSize: com.novaempire.core.domain.models.MapSize, archetype: com.novaempire.core.domain.models.MapArchetype): GameState {
        val map = MapFactory.generateMap(radius = mapSize.radius, archetype = archetype)
        val spawnPoints = MapFactory.spawnPointsFor(mapSize.radius).filter { map.tiles.containsKey(it) }
        val units = mutableMapOf<HexCoord, GameUnit>()
        val playerStates = mutableMapOf<Faction, PlayerState>()

        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        
        activeFactions.forEachIndexed { index, faction ->
            val spawnPoint = if (index < spawnPoints.size) spawnPoints[index] else null
            
            if (spawnPoint != null) {
                // Initial unit for each faction
                units[spawnPoint] = GameUnit(
                    type = if (faction == Faction.DOMINION) UnitType.CRUISER else UnitType.SCOUT,
                    faction = faction,
                    position = spawnPoint,
                    currentHp = if (faction == Faction.DOMINION) UnitType.CRUISER.maxHp else UnitType.SCOUT.maxHp
                )
            }
            
            playerStates[faction] = PlayerState(
                faction = faction,
                capitalCoord = spawnPoint,
                credits = 20 // Starting boost
            )
        }

        val initialState = GameState(map = map, units = units, playerStates = playerStates)
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

    private suspend fun handleIntent(intent: GameIntent) {
        // Prevent player actions while AI is thinking, except for initialization/loading
        if (_isAiThinking.value && intent !is GameIntent.LoadGame && intent !is GameIntent.StartNewGame && intent !is GameIntent.StartNewGameWithSize) {
            _errors.emit("AI is thinking, please wait.")
            return
        }

        if (intent is GameIntent.EndTurn) {
            _isAiThinking.value = true
            var currentState = _state.value
            currentState = reduce(currentState, intent).newState

            // AI Turn Loop — runs until it's the human player's turn again
            val humanFaction = currentState.humanFaction
            while (currentState.activeFaction != humanFaction) {
                currentState = withContext(Dispatchers.Default) {
                    aiStrategy.executeAITurn(currentState, currentState.activeFaction)
                }
                currentState = updateVision(currentState)
                // Trigger EndTurn to move to next faction
                currentState = reduce(currentState, GameIntent.EndTurn).newState
            }

            // Global refresh after full round
            val refreshedUnits = currentState.units.mapValues { it.value.copy(hasMoved = false, hasAttacked = false) }
            currentState = currentState.copy(units = refreshedUnits)

            val finalState = checkVictoryConditions(currentState)
            _state.value = finalState
            _isAiThinking.value = false
        } else {
            val currentState = _state.value
            val result = reduce(currentState, intent)
            if (result.error != null) {
                _errors.emit(result.error)
                _effects.emit(GameEffect.PlaySound("UI_CLICK"))
            }
            
            val nextState = result.newState
            
            // Side-effect: Camera shake and sound on combat
            val combat = nextState.lastCombatEvent
            if (combat != null && (currentState.lastCombatEvent == null || combat != currentState.lastCombatEvent)) {
                _effects.emit(GameEffect.ShakeCamera)
                if (combat.targetDestroyed) {
                    _effects.emit(GameEffect.PlaySound("COMBAT_EXPLOSION"))
                } else {
                    _effects.emit(GameEffect.PlaySound("COMBAT_LASER"))
                }
            }

            _state.value = checkVictoryConditions(nextState)
        }
    }

    private fun reduce(state: GameState, intent: GameIntent): GameResult {
        return when (intent) {

            is GameIntent.StartNewGame -> {
                GameResult(createInitialState(com.novaempire.core.domain.models.MapSize.MEDIUM, com.novaempire.core.domain.models.MapArchetype.STANDARD))
            }
            is GameIntent.StartNewGameWithSize -> {
                GameResult(createInitialState(intent.mapSize, intent.archetype))
            }
            is GameIntent.LoadGame -> {
                GameResult(intent.loadedState)
            }
            is GameIntent.EndTurn -> {
                GameResult(TurnManager.advanceTurn(state))
            }
            is GameIntent.SelectFaction -> {
                GameResult(state.copy(activeFaction = intent.faction, humanFaction = intent.faction))
            }
            is GameIntent.MoveUnit -> {
                val unit = state.units[intent.from] ?: return GameResult(state, "No unit at selected position.")
                IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
                IntentValidator.notMoved(unit)?.let { return GameResult(state, it) }

                val gridMap = GameGridMap(state)
                val ionPenalty = if (state.activeEvent == com.novaempire.core.domain.models.GalacticEvent.ION_STORM) 1 else 0
                val effectiveMovement = (unit.type.movement + unit.faction.bonusMovement - ionPenalty).coerceAtLeast(1)
                val path = com.novaempire.core.hex.HexPathfinder.findPath(
                    intent.from, intent.to, gridMap,
                    effectiveMovement
                )
                if (path != null && path.isNotEmpty()) {
                    val updatedUnits = state.units.toMutableMap()
                    updatedUnits.remove(intent.from)
                    updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
                    GameResult(updateVision(state.copy(units = updatedUnits), setOf(unit.faction)))
                } else {
                    GameResult(state, "Target position is unreachable or too far.")
                }
            }
            is GameIntent.AttackUnit -> {
                val unit = state.units[intent.attacker] ?: return GameResult(state, "Attacker not found.")
                IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
                IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
                val defender = state.units[intent.defender] ?: return GameResult(state, "Target not found.")
                if (intent.attacker.distanceTo(intent.defender) > unit.type.range)
                    return GameResult(state, "Target is out of range.")
                GameResult(updateVision(
                    CombatResolver.resolveCombat(state, intent.attacker, intent.defender),
                    setOfNotNull(unit.faction, defender.faction)
                ))
            }
            is GameIntent.ResearchTech -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val tech = com.novaempire.core.domain.models.TechRegistry.getTech(intent.techId)
                    ?: return GameResult(state, "Technology not found.")
                if (tech.requiresTechId != null && !playerState.techUnlocked.contains(tech.requiresTechId))
                    return GameResult(state, "Prerequisite technology not researched.")
                if (playerState.techUnlocked.contains(intent.techId))
                    return GameResult(state, "Technology already researched.")
                val eventDiscount = if (state.activeEvent == com.novaempire.core.domain.models.GalacticEvent.ANCIENT_SIGNAL) 0.25f else 0f
                val cost = CostCalculator.techCost(
                    intent.techId, playerState.techUnlocked,
                    playerState.recruitedHeroes.contains(com.novaempire.core.domain.models.HeroRegistry.KAEL),
                    (state.activeFaction.bonusTechDiscount + eventDiscount).coerceAtMost(0.9f)
                )
                IntentValidator.canAfford(playerState, cost)?.let { return GameResult(state, it) }
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = playerState.copy(
                    credits = playerState.credits - cost,
                    techUnlocked = playerState.techUnlocked + intent.techId
                )
                GameResult(updateVision(state.copy(playerStates = newPlayerStates), setOf(state.activeFaction)))
            }
            is GameIntent.BuildUnit -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val spawnCenter = intent.location ?: playerState.capitalCoord ?: return GameResult(state, "No valid spawn location.")
                IntentValidator.canAfford(playerState, intent.unitType.cost)?.let { return GameResult(state, it) }
                val gridMap = GameGridMap(state)
                val spawnHex = (listOf(spawnCenter) + gridMap.getNeighbors(spawnCenter))
                    .firstOrNull { state.units[it] == null && gridMap.isPassable(it) }
                    ?: return GameResult(state, "No available space to build unit.")
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = playerState.copy(credits = playerState.credits - intent.unitType.cost)
                val updatedUnits = state.units.toMutableMap()
                updatedUnits[spawnHex] = GameUnit(
                    type = intent.unitType, faction = state.activeFaction, position = spawnHex,
                    currentHp = intent.unitType.maxHp, hasMoved = true, hasAttacked = true
                )
                GameResult(updateVision(state.copy(playerStates = newPlayerStates, units = updatedUnits), setOf(state.activeFaction)))
            }
            is GameIntent.RecruitHero -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val hero = com.novaempire.core.domain.models.HeroRegistry.getHero(intent.heroId) ?: return GameResult(state, "Hero not found.")
                IntentValidator.canAfford(playerState, hero.cost)?.let { return GameResult(state, it) }
                if (playerState.recruitedHeroes.contains(hero.id)) return GameResult(state, "Hero already recruited.")
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = playerState.copy(
                    credits = playerState.credits - hero.cost,
                    recruitedHeroes = playerState.recruitedHeroes + hero.id
                )
                GameResult(state.copy(playerStates = newPlayerStates))
            }
            is GameIntent.ChangeRelation -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = playerState.copy(
                    relations = playerState.relations.toMutableMap().also { it[intent.targetFaction] = intent.newRelation }
                )
                val targetState = newPlayerStates[intent.targetFaction]
                if (targetState != null) {
                    newPlayerStates[intent.targetFaction] = targetState.copy(
                        relations = targetState.relations.toMutableMap().also { it[state.activeFaction] = intent.newRelation }
                    )
                }
                GameResult(state.copy(playerStates = newPlayerStates))
            }
            is GameIntent.SiegePlanet -> {
                val unit = state.units[intent.attackerCoord] ?: return GameResult(state, "Attacker not found.")
                IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
                IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
                IntentValidator.isPlanet(state, intent.planetCoord)?.let { return GameResult(state, it) }
                if (state.map.tiles[intent.planetCoord]?.owner == state.activeFaction)
                    return GameResult(state, "You cannot siege your own planet.")
                GameResult(CombatResolver.siegePlanet(state, intent.attackerCoord, intent.planetCoord))
            }
            is GameIntent.CapturePlanet -> {
                val unit = state.units[intent.unitCoord] ?: return GameResult(state, "Unit not found.")
                IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
                IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
                IntentValidator.isPlanet(state, intent.planetCoord)?.let { return GameResult(state, it) }
                val tile = state.map.tiles[intent.planetCoord]!!
                if (tile.systemLevel > 0) return GameResult(state, "Planet must be at level 0 to be captured.")
                if (tile.owner == state.activeFaction) return GameResult(state, "You already own this planet.")
                GameResult(CombatResolver.capturePlanet(state, intent.unitCoord, intent.planetCoord))
            }
        }
    }

    private fun updateVision(
        state: GameState,
        factions: Collection<Faction> = Faction.values().asList()
    ): GameState {
        val updatedPlayers = state.playerStates.toMutableMap()

        for (faction in factions) {
            val playerState = updatedPlayers[faction] ?: PlayerState(faction)
            val visibleNow = VisionSystem.calculateVisibleHexes(state, faction)
            val newlyExplored = playerState.exploredHexes + visibleNow
            updatedPlayers[faction] = playerState.copy(
                visibleHexes = visibleNow,
                exploredHexes = newlyExplored
            )
        }

        return state.copy(playerStates = updatedPlayers)
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
        val mapSize: com.novaempire.core.domain.models.MapSize = com.novaempire.core.domain.models.MapSize.MEDIUM,
        val archetype: com.novaempire.core.domain.models.MapArchetype = com.novaempire.core.domain.models.MapArchetype.STANDARD
    ) : GameIntent()
    data class LoadGame(val loadedState: GameState) : GameIntent()
    data class SiegePlanet(val attackerCoord: HexCoord, val planetCoord: HexCoord) : GameIntent()
    data class CapturePlanet(val unitCoord: HexCoord, val planetCoord: HexCoord) : GameIntent()
}
