package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import kotlin.random.Random

object TurnManager {

    fun advanceTurn(state: GameState, rng: Random = Random.Default): GameState {
        val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        val nextIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
        val nextFaction = allFactions[nextIndex]

        var nextState = state.copy(activeFaction = nextFaction)

        if (nextIndex == 0) {
            nextState = nextState.copy(turn = state.turn + 1)
            nextState = EventSystem.tick(nextState, rng)
        }

        // Nix hero: heal all units of the faction that just ended its turn
        val activePlayerState = state.playerStates[state.activeFaction]
        if (activePlayerState?.recruitedHeroes?.contains(HeroRegistry.NIX) == true) {
            nextState = nextState.copy(
                units = nextState.units.mapValues { (_, unit) ->
                    if (unit.faction == state.activeFaction && unit.currentHp < unit.type.maxHp)
                        unit.copy(currentHp = minOf(unit.type.maxHp, unit.currentHp + 1))
                    else unit
                }
            )
        }

        // Income for the faction starting its turn
        val nextPlayerState = nextState.playerStates[nextFaction]
        if (nextPlayerState != null) {
            var income = 10
            // Planet income: each owned planet yields credits based on its development level
            val ownedPlanets = nextState.map.tiles.values.filter {
                it.terrain == TerrainType.PLANET && it.owner == nextFaction
            }
            income += ownedPlanets.sumOf { 5 + it.systemLevel * 2 }
            if (nextPlayerState.recruitedHeroes.contains(HeroRegistry.ELARA)) {
                income += (income * 0.10).toInt() + 2
            }
            if (nextState.activeEvent == GalacticEvent.ECONOMIC_BOOM) income += 3
            income += nextFaction.bonusCredits

            // Unit upkeep: deducted from income each turn
            val upkeep = nextState.units.values.filter { it.faction == nextFaction }.sumOf { it.type.upkeepCost }
            income -= upkeep

            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[nextFaction] = nextPlayerState.copy(credits = nextPlayerState.credits + income)
            nextState = nextState.copy(playerStates = newPlayerStates)
        }

        // Tick build queue for the faction ending its turn
        val buildingFactionState = nextState.playerStates[state.activeFaction]
        if (buildingFactionState != null && buildingFactionState.buildQueue.isNotEmpty()) {
            val remainingOrders = mutableListOf<com.novaempire.core.domain.state.BuildOrder>()
            var stateAfterBuilds = nextState

            for (order in buildingFactionState.buildQueue) {
                val newTurns = order.turnsRemaining - 1
                if (newTurns <= 0) {
                    val gridMap = GameGridMap(stateAfterBuilds)
                    val candidates = listOf(order.planetCoord) + gridMap.getNeighbors(order.planetCoord)
                    val spawnHex = candidates.firstOrNull { stateAfterBuilds.units[it] == null && gridMap.isPassable(it) }
                    if (spawnHex != null) {
                        val hasHullPlating = stateAfterBuilds.playerStates[state.activeFaction]?.techUnlocked?.contains("tech_hull_plating") == true
                    val newUnit = GameUnit(
                            type = order.unitType,
                            faction = state.activeFaction,
                            position = spawnHex,
                            currentHp = order.unitType.maxHp + if (hasHullPlating) 3 else 0
                        )
                        val updatedUnits = stateAfterBuilds.units.toMutableMap()
                        updatedUnits[spawnHex] = newUnit
                        stateAfterBuilds = stateAfterBuilds.copy(units = updatedUnits)
                    }
                } else {
                    remainingOrders.add(order.copy(turnsRemaining = newTurns))
                }
            }

            val newPlayerStates = stateAfterBuilds.playerStates.toMutableMap()
            newPlayerStates[state.activeFaction] = stateAfterBuilds.playerStates[state.activeFaction]!!
                .copy(buildQueue = remainingOrders)
            nextState = stateAfterBuilds.copy(playerStates = newPlayerStates)
        }

        // Tick research for the faction that just ended its turn
        val researchingState = nextState.playerStates[state.activeFaction]
        researchingState?.researchInProgress?.let { prog ->
            val newTurns = prog.turnsRemaining - 1
            val updatedResearcher = if (newTurns <= 0) {
                researchingState.copy(
                    techUnlocked = researchingState.techUnlocked + prog.techId,
                    researchInProgress = null
                )
            } else {
                researchingState.copy(researchInProgress = prog.copy(turnsRemaining = newTurns))
            }
            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[state.activeFaction] = updatedResearcher
            nextState = nextState.copy(playerStates = newPlayerStates)
        }

        return nextState
    }
}
