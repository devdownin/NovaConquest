package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.TechRegistry

object UtilityEvaluator {

    fun executeAITurn(state: GameState, aiFaction: Faction): GameState {
        var currentState = state

        // 1. Economic / Tech Logic
        currentState = evaluateEconomyAndTech(currentState, aiFaction)

        // 2. Production Logic
        currentState = evaluateProduction(currentState, aiFaction)

        // 3. Tactical Logic (Move and Attack)
        val myUnits = currentState.units.values.filter { it.faction == aiFaction && (!it.hasMoved || !it.hasAttacked) }

        for (unit in myUnits) {
            val possibleTargets = currentState.units.values.filter { it.faction != aiFaction }
            val adjacentTarget = possibleTargets.find { it.position.distanceTo(unit.position) <= unit.type.range }

            if (adjacentTarget != null && !unit.hasAttacked) {
                // Attack if in range
                currentState = CombatResolver.resolveCombat(currentState, unit.position, adjacentTarget.position)
            } else if (!unit.hasMoved) {
                // Move towards closest target if not adjacent
                val closestTarget = possibleTargets.minByOrNull { it.position.distanceTo(unit.position) }
                if (closestTarget != null) {
                    val gridMap = GameGridMap(currentState)
                    val path = com.novaempire.core.hex.HexPathfinder.findPath(
                        start = unit.position,
                        goal = closestTarget.position,
                        gridMap = gridMap,
                        maxCost = unit.type.attack // Using attack as mobility proxy for demo
                    )

                    if (path != null && path.isNotEmpty()) {
                        val destination = path.lastOrNull { currentState.units[it] == null }
                        if (destination != null) {
                            currentState = moveUnit(currentState, unit.position, destination)
                        }
                    }
                }
            }
        }

        // Refresh units for next turn
        val refreshedUnits = currentState.units.mapValues {
            if (it.value.faction == aiFaction) it.value.copy(hasMoved = false, hasAttacked = false)
            else it.value
        }
        return currentState.copy(units = refreshedUnits)
    }

    private fun evaluateEconomyAndTech(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state

        // Simple AI: Buy first affordable tech
        val affordableTech = TechRegistry.ALL_TECHS.find { tech ->
            val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
            val isUnlocked = playerState.techUnlocked.contains(tech.id)
            val cost = TechRegistry.calculateCost(tech.id, playerState.techUnlocked)
            isAvailable && !isUnlocked && playerState.credits >= cost
        }

        if (affordableTech != null) {
            val cost = TechRegistry.calculateCost(affordableTech.id, playerState.techUnlocked)
            val newPlayerState = playerState.copy(
                credits = playerState.credits - cost,
                techUnlocked = playerState.techUnlocked + affordableTech.id
            )
            val newPlayerStates = state.playerStates.toMutableMap()
            newPlayerStates[faction] = newPlayerState
            return state.copy(playerStates = newPlayerStates)
        }

        return state
    }

    private fun evaluateProduction(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        val capital = playerState.capitalCoord ?: return state

        // Simple AI: Always try to build a Cruiser, then Fighter, then Scout
        val desiredUnits = listOf(UnitType.CRUISER, UnitType.FIGHTER, UnitType.SCOUT)

        for (unitType in desiredUnits) {
            if (playerState.credits >= unitType.cost) {
                val gridMap = GameGridMap(state)
                val spawnCandidates = listOf(capital) + gridMap.getNeighbors(capital)
                val spawnHex = spawnCandidates.firstOrNull { state.units[it] == null && gridMap.isPassable(it) }

                if (spawnHex != null) {
                    val newPlayerState = playerState.copy(credits = playerState.credits - unitType.cost)
                    val newPlayerStates = state.playerStates.toMutableMap()
                    newPlayerStates[faction] = newPlayerState

                    val newUnit = GameUnit(
                        type = unitType,
                        faction = faction,
                        position = spawnHex,
                        currentHp = unitType.maxHp,
                        hasMoved = true,
                        hasAttacked = true
                    )
                    val updatedUnits = state.units.toMutableMap()
                    updatedUnits[spawnHex] = newUnit

                    return state.copy(playerStates = newPlayerStates, units = updatedUnits)
                }
            }
        }

        return state
    }

    private fun moveUnit(state: GameState, from: HexCoord, to: HexCoord): GameState {
        val unit = state.units[from] ?: return state
        if (state.units.containsKey(to)) return state

        val updatedUnits = state.units.toMutableMap()
        updatedUnits.remove(from)
        updatedUnits[to] = unit.copy(position = to, hasMoved = true)
        return state.copy(units = updatedUnits)
    }
}
