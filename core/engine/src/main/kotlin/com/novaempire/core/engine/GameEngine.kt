package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class GameEngine {
    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private fun createInitialState(): GameState {
        val map = MapFactory.generateMap(radius = 4)
        // Spawn some initial units for testing
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

        return GameState(map = map, units = units)
    }

    fun processIntent(intent: GameIntent) {
        _state.update { currentState ->
            reduce(currentState, intent)
        }
    }

    private fun reduce(state: GameState, intent: GameIntent): GameState {
        return when (intent) {
            is GameIntent.EndTurn -> {
                // Determine next faction
                val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
                val nextFactionIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
                var nextFaction = allFactions[nextFactionIndex]
                var nextTurn = state.turn

                if (nextFactionIndex == 0) {
                    nextTurn++
                }

                var nextState = state.copy(activeFaction = nextFaction, turn = nextTurn)

                // If it's AI turn, process it
                // For demonstration, let's assume any faction that is NOT DOMINION is AI
                if (nextFaction != Faction.DOMINION) {
                    nextState = UtilityEvaluator.executeAITurn(nextState, nextFaction)
                    // Skip to next turn for simplicity in this demo until human turn is reached
                    return reduce(nextState, GameIntent.EndTurn)
                } else {
                    // Refresh human units
                    val refreshedUnits = nextState.units.mapValues { it.value.copy(hasMoved = false, hasAttacked = false) }
                    nextState.copy(units = refreshedUnits)
                }
            }
            is GameIntent.SelectFaction -> {
                state.copy(activeFaction = intent.faction)
            }
            is GameIntent.MoveUnit -> {
                val unit = state.units[intent.from]
                if (unit != null && unit.faction == state.activeFaction && !unit.hasMoved) {
                    // Check if path is valid
                    val gridMap = GameGridMap(state)
                    val path = com.novaempire.core.hex.HexPathfinder.findPath(intent.from, intent.to, gridMap, unit.type.attack) // Using attack as range temporarily

                    if (path != null && path.isNotEmpty()) {
                        val updatedUnits = state.units.toMutableMap()
                        updatedUnits.remove(intent.from)
                        updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
                        state.copy(units = updatedUnits)
                    } else state
                } else state
            }
        }
    }
}

sealed class GameIntent {
    object EndTurn : GameIntent()
    data class SelectFaction(val faction: Faction) : GameIntent()
    data class MoveUnit(val from: HexCoord, val to: HexCoord) : GameIntent()
}
