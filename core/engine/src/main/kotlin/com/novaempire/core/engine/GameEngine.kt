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

            // AI Turn Loop
            while (currentState.activeFaction != Faction.DOMINION) {
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
                GameResult(state.copy(activeFaction = intent.faction))
            }
            is GameIntent.MoveUnit -> {
                val unit = state.units[intent.from]
                if (unit == null) return GameResult(state, "No unit at selected position.")
                if (unit.faction != state.activeFaction) return GameResult(state, "You cannot move another faction's unit.")
                if (unit.hasMoved) return GameResult(state, "This unit has already moved this turn.")

                val gridMap = GameGridMap(state)
                val totalMovement = unit.type.movement + unit.faction.bonusMovement
                val path = com.novaempire.core.hex.HexPathfinder.findPath(intent.from, intent.to, gridMap, totalMovement)

                if (path != null && path.isNotEmpty()) {
                    val updatedUnits = state.units.toMutableMap()
                    updatedUnits.remove(intent.from)
                    updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
                    val nextState = state.copy(units = updatedUnits)
                    GameResult(updateVision(nextState, setOf(unit.faction)))
                } else {
                    GameResult(state, "Target position is unreachable or too far.")
                }
            }
            is GameIntent.AttackUnit -> {
                val unit = state.units[intent.attacker]
                if (unit == null) return GameResult(state, "Attacker not found.")
                if (unit.faction != state.activeFaction) return GameResult(state, "You cannot attack with another faction's unit.")
                if (unit.hasAttacked) return GameResult(state, "This unit has already attacked this turn.")

                val defender = state.units[intent.defender]
                if (defender == null) return GameResult(state, "Target not found.")

                val distance = intent.attacker.distanceTo(intent.defender)
                if (distance > unit.type.range) return GameResult(state, "Target is out of range.")

                val nextState = CombatResolver.resolveCombat(state, intent.attacker, intent.defender)
                GameResult(updateVision(nextState, setOfNotNull(unit.faction, defender.faction)))
            }
            is GameIntent.ResearchTech -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val cost = CostCalculator.techCost(
                    intent.techId,
                    playerState.techUnlocked,
                    playerState.recruitedHeroes.contains("hero_kael"),
                    state.activeFaction.bonusTechDiscount
                )

                val tech = com.novaempire.core.domain.models.TechRegistry.getTech(intent.techId) ?: return GameResult(state, "Technology not found.")
                val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
                val isAlreadyUnlocked = playerState.techUnlocked.contains(intent.techId)

                if (!isAvailable) return GameResult(state, "Prerequisite technology not researched.")
                if (isAlreadyUnlocked) return GameResult(state, "Technology already researched.")
                if (playerState.credits < cost) return GameResult(state, "Not enough credits.")

                val newPlayerState = playerState.copy(
                    credits = playerState.credits - cost,
                    techUnlocked = playerState.techUnlocked + intent.techId
                )
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = newPlayerState

                val nextState = state.copy(playerStates = newPlayerStates)
                GameResult(updateVision(nextState, setOf(state.activeFaction)))
            }
            is GameIntent.BuildUnit -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val cost = intent.unitType.cost
                val spawnCenter = intent.location ?: playerState.capitalCoord ?: return GameResult(state, "No valid spawn location.")

                if (playerState.credits < cost) return GameResult(state, "Not enough credits.")

                val gridMap = GameGridMap(state)
                val spawnCandidates = listOf(spawnCenter) + gridMap.getNeighbors(spawnCenter)
                val spawnHex = spawnCandidates.firstOrNull { state.units[it] == null && gridMap.isPassable(it) }

                if (spawnHex != null) {
                    val newPlayerState = playerState.copy(credits = playerState.credits - cost)
                    val newPlayerStates = state.playerStates.toMutableMap()
                    newPlayerStates[state.activeFaction] = newPlayerState

                    val newUnit = GameUnit(
                        type = intent.unitType,
                        faction = state.activeFaction,
                        position = spawnHex,
                        currentHp = intent.unitType.maxHp,
                        hasMoved = true,
                        hasAttacked = true
                    )
                    val updatedUnits = state.units.toMutableMap()
                    updatedUnits[spawnHex] = newUnit

                    val nextState = state.copy(playerStates = newPlayerStates, units = updatedUnits)
                    GameResult(updateVision(nextState, setOf(state.activeFaction)))
                } else {
                    GameResult(state, "No available space to build unit.")
                }
            }
            is GameIntent.RecruitHero -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val hero = com.novaempire.core.domain.models.HeroRegistry.getHero(intent.heroId) ?: return GameResult(state, "Hero not found.")

                if (playerState.credits < hero.cost) return GameResult(state, "Not enough credits.")
                if (playerState.recruitedHeroes.contains(hero.id)) return GameResult(state, "Hero already recruited.")

                val newPlayerState = playerState.copy(
                    credits = playerState.credits - hero.cost,
                    recruitedHeroes = playerState.recruitedHeroes + hero.id
                )
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = newPlayerState
                GameResult(state.copy(playerStates = newPlayerStates))
            }
            is GameIntent.ChangeRelation -> {
                val playerState = state.playerStates[state.activeFaction] ?: return GameResult(state, "Player state not found.")
                val newRelations = playerState.relations.toMutableMap()
                newRelations[intent.targetFaction] = intent.newRelation

                val newPlayerState = playerState.copy(relations = newRelations)
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = newPlayerState

                // Also update the target's relation to us for symmetry
                val targetState = newPlayerStates[intent.targetFaction]
                if (targetState != null) {
                    val targetRelations = targetState.relations.toMutableMap()
                    targetRelations[state.activeFaction] = intent.newRelation
                    newPlayerStates[intent.targetFaction] = targetState.copy(relations = targetRelations)
                }

                GameResult(state.copy(playerStates = newPlayerStates))
            }
            is GameIntent.SiegePlanet -> {
                val unit = state.units[intent.attackerCoord]
                if (unit == null) return GameResult(state, "Attacker not found.")
                if (unit.faction != state.activeFaction) return GameResult(state, "You cannot use this unit.")
                if (unit.hasAttacked) return GameResult(state, "Unit already used its action.")

                val tile = state.map.tiles[intent.planetCoord]
                if (tile == null || tile.terrain != com.novaempire.core.domain.models.TerrainType.PLANET) {
                    return GameResult(state, "Target is not a planet.")
                }
                if (tile.owner == state.activeFaction) return GameResult(state, "You cannot siege your own planet.")

                val siegeDamage = if (unit.type == UnitType.BATTLESHIP) 2 else 1
                val newLevel = Math.max(0, tile.systemLevel - siegeDamage)

                val updatedUnits = state.units.toMutableMap()
                updatedUnits[intent.attackerCoord] = unit.copy(hasAttacked = true)

                val newTiles = state.map.tiles.toMutableMap()
                newTiles[intent.planetCoord] = tile.copy(systemLevel = newLevel)
                val newMap = state.map.copy(tiles = newTiles)

                GameResult(state.copy(units = updatedUnits, map = newMap))
            }
            is GameIntent.CapturePlanet -> {
                val unit = state.units[intent.unitCoord]
                if (unit == null) return GameResult(state, "Unit not found.")
                if (unit.faction != state.activeFaction) return GameResult(state, "You cannot use this unit.")
                if (unit.hasAttacked) return GameResult(state, "Unit already used its action.")

                val tile = state.map.tiles[intent.planetCoord]
                if (tile == null || tile.terrain != com.novaempire.core.domain.models.TerrainType.PLANET) {
                    return GameResult(state, "Target is not a planet.")
                }
                if (tile.systemLevel > 0) return GameResult(state, "Planet must be at level 0 to be captured.")
                if (tile.owner == state.activeFaction) return GameResult(state, "You already own this planet.")

                val updatedUnits = state.units.toMutableMap()
                updatedUnits[intent.unitCoord] = unit.copy(hasAttacked = true)

                val newTiles = state.map.tiles.toMutableMap()
                newTiles[intent.planetCoord] = tile.copy(owner = state.activeFaction, systemLevel = 1) // Rebuild at level 1
                val newMap = state.map.copy(tiles = newTiles)

                GameResult(state.copy(units = updatedUnits, map = newMap))
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
