package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.Faction
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
            val ownedPlanets = nextState.map.tiles.values.filter {
                it.terrain == TerrainType.PLANET && it.owner == nextFaction
            }
            var income = 10 + ownedPlanets.sumOf { 5 + it.systemLevel * 2 }

            val incomePct = BonusRegistry.sum(BonusType.INCOME_PERCENT, nextPlayerState, nextState.activeEvent)
            val incomeFlat = BonusRegistry.sum(BonusType.INCOME_FLAT, nextPlayerState, nextState.activeEvent)
            income += (income * incomePct / 100.0).toInt() + incomeFlat

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
                        val spawningPlayer = stateAfterBuilds.playerStates[state.activeFaction]
                        val hpBonus = BonusRegistry.sum(BonusType.UNIT_HP_ON_SPAWN, spawningPlayer, nextState.activeEvent)
                        val newUnit = GameUnit(
                            type = order.unitType,
                            faction = state.activeFaction,
                            position = spawnHex,
                            currentHp = order.unitType.maxHp + hpBonus
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
            val researchTick = 1 + BonusRegistry.sum(BonusType.RESEARCH_SPEED, researchingState, nextState.activeEvent)
            val newTurns = prog.turnsRemaining - researchTick
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
