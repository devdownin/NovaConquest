package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.PlanetSpecialty
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import kotlin.random.Random

object TurnManager {

    /** Structural damage a ship takes at the end of its turn while sitting on a black hole. */
    const val BLACK_HOLE_DAMAGE = 3

    /** Magnitude of the anomaly pulse (heal or damage) applied at end of turn. */
    const val ANOMALY_SURGE = 2

    fun advanceTurn(state: GameState, rng: Random = Random.Default): GameState {
        val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        val nextIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
        val nextFaction = allFactions[nextIndex]

        var nextState = state.copy(activeFaction = nextFaction)

        if (nextIndex == 0) {
            nextState = nextState.copy(turn = state.turn + 1)
            nextState = EventSystem.tick(nextState, rng)
            // Domination tracking: count planets per faction in a single pass; update dominationTurns.
            val planetsByOwner = nextState.map.tiles.values
                .filter { it.terrain == TerrainType.PLANET }
                .groupingBy { it.owner }
                .eachCount()
            val totalPlanets = planetsByOwner.values.sum()
            if (totalPlanets > 0) {
                val newDomTurns = nextState.dominationTurns.toMutableMap()
                for (f in allFactions) {
                    val owned = planetsByOwner[f] ?: 0
                    newDomTurns[f] = if (owned * 100 / totalPlanets >= 60) (newDomTurns[f] ?: 0) + 1 else 0
                }
                nextState = nextState.copy(dominationTurns = newDomTurns)
            }
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

        // Black hole hazard: units of the faction that just ended its turn take structural
        // damage while sitting on a black hole; destroyed ships are removed. Gives the
        // "danger extrême" terrain a real cost and a reason not to camp the -25% ATK tile.
        val hasBlackHoleUnit = nextState.units.values.any { unit ->
            unit.faction == state.activeFaction &&
                nextState.map.tiles[unit.position]?.terrain == TerrainType.BLACK_HOLE
        }
        if (hasBlackHoleUnit) {
            val survivors = nextState.units
                .mapValues { (_, unit) ->
                    if (unit.faction == state.activeFaction &&
                        nextState.map.tiles[unit.position]?.terrain == TerrainType.BLACK_HOLE) {
                        unit.copy(currentHp = unit.currentHp - BLACK_HOLE_DAMAGE)
                    } else unit
                }
                .filterValues { it.currentHp > 0 }
            nextState = nextState.copy(units = survivors)
        }

        // Galactic anomaly: a unit of the faction ending its turn on an anomaly is hit by an
        // unpredictable pulse — exotic energy (heal), radiation (damage), or nothing. Uses the
        // injected rng so it stays deterministic under test; a unit reduced to 0 HP is removed.
        val hasAnomalyUnit = nextState.units.values.any { unit ->
            unit.faction == state.activeFaction &&
                nextState.map.tiles[unit.position]?.terrain == TerrainType.ANOMALY
        }
        if (hasAnomalyUnit) {
            val pulsed = nextState.units
                .mapValues { (_, unit) ->
                    if (unit.faction == state.activeFaction &&
                        nextState.map.tiles[unit.position]?.terrain == TerrainType.ANOMALY) {
                        when (rng.nextInt(3)) {
                            0 -> unit.copy(currentHp = minOf(unit.type.maxHp, unit.currentHp + ANOMALY_SURGE))
                            1 -> unit.copy(currentHp = unit.currentHp - ANOMALY_SURGE)
                            else -> unit
                        }
                    } else unit
                }
                .filterValues { it.currentHp > 0 }
            nextState = nextState.copy(units = pulsed)
        }

        // Income for the faction starting its turn — via the shared IncomeCalculator so the HUD
        // preview and the credits actually granted here can never drift apart.
        val nextPlayerState = nextState.playerStates[nextFaction]
        if (nextPlayerState != null) {
            val income = IncomeCalculator.perTurn(nextState, nextFaction)

            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[nextFaction] = nextPlayerState.copy(credits = nextPlayerState.credits + income)
            nextState = nextState.copy(playerStates = newPlayerStates)
        }

        // Tick build queue for the faction ending its turn
        val buildingFactionState = nextState.playerStates[state.activeFaction]
        if (buildingFactionState != null && buildingFactionState.buildQueue.isNotEmpty()) {
            val remainingOrders = mutableListOf<com.novaempire.core.domain.state.BuildOrder>()
            var stateAfterBuilds = nextState

            val forgeWorldCount = nextState.map.tiles.values.count {
                it.terrain == TerrainType.PLANET && it.owner == state.activeFaction &&
                it.specialty == PlanetSpecialty.FORGE_WORLD
            }
            val productionBonus = BonusRegistry.sum(BonusType.PRODUCTION_SPEED, buildingFactionState, nextState.activeEvent)
            for (order in buildingFactionState.buildQueue) {
                val forgeTick = if (forgeWorldCount > 0 && nextState.map.tiles[order.planetCoord]?.specialty == PlanetSpecialty.FORGE_WORLD) 2 else 1
                val buildTick = forgeTick + productionBonus
                val newTurns = order.turnsRemaining - buildTick
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
                    } else {
                        // Every candidate hex is taken — retry next turn, and flag it so the
                        // shipyard can say why the order never completes (P5).
                        remainingOrders.add(order.copy(turnsRemaining = 1, blocked = true))
                    }
                } else {
                    remainingOrders.add(order.copy(turnsRemaining = newTurns, blocked = false))
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
            val researchHubCount = nextState.map.tiles.values.count {
                it.terrain == TerrainType.PLANET && it.owner == state.activeFaction &&
                it.specialty == PlanetSpecialty.RESEARCH_HUB
            }
            val researchTick = 1 + researchHubCount + BonusRegistry.sum(BonusType.RESEARCH_SPEED, researchingState, nextState.activeEvent, nextState.eventTargetFaction)
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
