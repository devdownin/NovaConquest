package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.models.GameUnit
import java.util.UUID

object UtilityEvaluator {

    fun executeAITurn(state: GameState, aiFaction: Faction): GameState {
        // Basic AI: Move towards closest enemy unit or planet
        var currentState = state
        val myUnits = currentState.units.values.filter { it.faction == aiFaction && !it.hasMoved }

        for (unit in myUnits) {
            val possibleTargets = currentState.units.values.filter { it.faction != aiFaction }
            val closestTarget = possibleTargets.minByOrNull { it.position.distanceTo(unit.position) }

            if (closestTarget != null) {
                // Determine move path
                val gridMap = GameGridMap(currentState)
                val path = com.novaempire.core.hex.HexPathfinder.findPath(
                    start = unit.position,
                    goal = closestTarget.position,
                    gridMap = gridMap,
                    maxCost = unit.type.attack // Assuming attack represents mobility for now as a placeholder
                )

                if (path != null && path.isNotEmpty()) {
                    // Move to the closest point towards the target (not on top of it)
                    val destination = path.lastOrNull { currentState.units[it] == null }
                    if (destination != null) {
                        currentState = moveUnit(currentState, unit.position, destination)
                    }
                }
            }
        }

        // Refresh units for next turn
        val refreshedUnits = currentState.units.mapValues { it.value.copy(hasMoved = false, hasAttacked = false) }
        return currentState.copy(units = refreshedUnits)
    }

    private fun moveUnit(state: GameState, from: HexCoord, to: HexCoord): GameState {
        val unit = state.units[from] ?: return state
        if (state.units.containsKey(to)) return state // Target occupied

        val updatedUnits = state.units.toMutableMap()
        updatedUnits.remove(from)
        updatedUnits[to] = unit.copy(position = to, hasMoved = true)
        return state.copy(units = updatedUnits)
    }
}
