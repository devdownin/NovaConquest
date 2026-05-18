package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

class GameEngine {
    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private fun createInitialState(): GameState {
        val map = MapFactory.generateMap(radius = 4)
        val spawnPoints = map.tiles.keys.filter { map.tiles[it]?.terrain == com.novaempire.core.domain.models.TerrainType.PLANET }
        val units = mutableMapOf<HexCoord, GameUnit>()

        if (spawnPoints.isNotEmpty()) {
            units[spawnPoints[0]] = GameUnit(
                type = UnitType.CRUISER,
                faction = Faction.DOMINION,
                position = spawnPoints[0],
                currentHp = UnitType.CRUISER.maxHp
            )
        }
        if (spawnPoints.size > 1) {
            units[spawnPoints[1]] = GameUnit(
                type = UnitType.SCOUT,
                faction = Faction.TRADERS,
                position = spawnPoints[1],
                currentHp = UnitType.SCOUT.maxHp
            )
        }

        var initialState = GameState(map = map, units = units)

        // Initialize player states
        val playerStates = Faction.values().associateWith { PlayerState(faction = it) }.toMutableMap()
        initialState = initialState.copy(playerStates = playerStates)

        // Calculate initial vision
        return updateVision(initialState)
    }

    fun processIntent(intent: GameIntent) {
        _state.update { currentState ->
            reduce(currentState, intent)
        }
    }

    private fun reduce(state: GameState, intent: GameIntent): GameState {
        return when (intent) {
            is GameIntent.EndTurn -> {
                val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
                val nextFactionIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
                var nextFaction = allFactions[nextFactionIndex]

                var nextState = state.copy(activeFaction = nextFaction)

                if (nextFactionIndex == 0) {
                    nextState = nextState.copy(turn = state.turn + 1)
                    nextState = triggerGalacticEvent(nextState)
                }

                if (nextFaction != Faction.DOMINION) {
                    nextState = UtilityEvaluator.executeAITurn(nextState, nextFaction)
                    nextState = updateVision(nextState)
                    return reduce(nextState, GameIntent.EndTurn)
                } else {
                    val refreshedUnits = nextState.units.mapValues { it.value.copy(hasMoved = false, hasAttacked = false) }
                    nextState = nextState.copy(units = refreshedUnits)
                    updateVision(nextState)
                }
            }
            is GameIntent.SelectFaction -> {
                state.copy(activeFaction = intent.faction)
            }
            is GameIntent.MoveUnit -> {
                val unit = state.units[intent.from]
                if (unit != null && unit.faction == state.activeFaction && !unit.hasMoved) {
                    val gridMap = GameGridMap(state)
                    val path = com.novaempire.core.hex.HexPathfinder.findPath(intent.from, intent.to, gridMap, unit.type.attack)

                    if (path != null && path.isNotEmpty()) {
                        val updatedUnits = state.units.toMutableMap()
                        updatedUnits.remove(intent.from)
                        updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
                        val nextState = state.copy(units = updatedUnits)
                        updateVision(nextState)
                    } else state
                } else state
            }
        }
    }

    private fun triggerGalacticEvent(state: GameState): GameState {
        // 20% chance to trigger a new event if none is active
        var activeEvent = state.activeEvent
        var duration = state.eventDurationRemaining

        if (activeEvent != GalacticEvent.NONE) {
            duration--
            if (duration <= 0) {
                activeEvent = GalacticEvent.NONE
            }
        } else if (Random.nextDouble() < 0.20) {
            val events = GalacticEvent.values().filter { it != GalacticEvent.NONE }
            activeEvent = events.random()
            duration = Random.nextInt(2, 5) // Lasts 2 to 4 turns
        }

        return state.copy(activeEvent = activeEvent, eventDurationRemaining = duration)
    }

    private fun updateVision(state: GameState): GameState {
        val updatedPlayers = state.playerStates.toMutableMap()

        for (faction in Faction.values()) {
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
}
