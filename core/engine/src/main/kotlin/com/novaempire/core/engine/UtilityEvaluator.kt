package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.hex.HexCoord

object UtilityEvaluator : AIStrategy {

    override suspend fun executeAITurn(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState {
        kotlinx.coroutines.delay(0)
        val aiFaction = faction
        var currentState = state

        currentState = evaluateDiplomacy(currentState, aiFaction, reduce)
        currentState = evaluateEconomyAndTech(currentState, aiFaction)
        currentState = evaluateHeroes(currentState, aiFaction)
        currentState = evaluateProduction(currentState, aiFaction)

        val aiPlayerState = currentState.playerStates[aiFaction]
        val myUnits = currentState.units.values.filter { it.faction == aiFaction && (!it.hasMoved || !it.hasAttacked) }

        for (unit in myUnits) {
            val possibleTargets = currentState.units.values.filter {
                it.faction != aiFaction &&
                (aiPlayerState?.relations?.get(it.faction) == DiplomaticRelation.WAR || it.faction == Faction.ANCIENT_NPC)
            }

            val targetPlanets = currentState.map.tiles.values.filter {
                it.terrain == com.novaempire.core.domain.models.TerrainType.PLANET &&
                it.owner != aiFaction &&
                (it.owner == null || aiPlayerState?.relations?.get(it.owner) != DiplomaticRelation.ALLIANCE)
            }

            val hpCritical = unit.currentHp.toFloat() / unit.type.maxHp < 0.30f
            val threatNearby = possibleTargets.any { it.position.distanceTo(unit.position) <= 2 }
            if (hpCritical && !unit.hasMoved && threatNearby && unit.type.movement > 0) {
                val ownedPlanets = currentState.map.tiles.values.filter { it.owner == aiFaction }
                val retreatTarget = ownedPlanets.minByOrNull { it.coord.distanceTo(unit.position) }
                if (retreatTarget != null && retreatTarget.coord != unit.position) {
                    val gridMap = GameGridMap(currentState, aiFaction)
                    val totalMovement = effectiveMovement(currentState, aiFaction, unit.type.movement)
                    val path = com.novaempire.core.hex.HexPathfinder.findPath(
                        start = unit.position, goal = retreatTarget.coord,
                        gridMap = gridMap, maxCost = totalMovement
                    )
                    if (path != null && path.isNotEmpty()) {
                        val destination = path.take(totalMovement).lastOrNull { currentState.units[it] == null }
                        if (destination != null) {
                            currentState = reduce(currentState, GameIntent.MoveUnit(unit.position, destination))
                            continue
                        }
                    }
                }
            }

            val adjacentPlanet = targetPlanets.find { it.coord.distanceTo(unit.position) <= 1 }
            if (adjacentPlanet != null && !unit.hasAttacked) {
                currentState = if (adjacentPlanet.systemLevel == 0) {
                    reduce(currentState, GameIntent.CapturePlanet(unit.position, adjacentPlanet.coord))
                } else {
                    reduce(currentState, GameIntent.SiegePlanet(unit.position, adjacentPlanet.coord))
                }
                continue
            }

            val adjacentTarget = possibleTargets.find { it.position.distanceTo(unit.position) <= unit.type.range }
            if (adjacentTarget != null && !unit.hasAttacked) {
                currentState = reduce(currentState, GameIntent.AttackUnit(unit.position, adjacentTarget.position))
            } else if (!unit.hasMoved && unit.type.movement > 0) {
                val closestUnit = possibleTargets.minByOrNull { it.position.distanceTo(unit.position) }
                val closestPlanet = targetPlanets.minByOrNull { it.coord.distanceTo(unit.position) }

                val goal = when {
                    closestPlanet != null && (closestUnit == null ||
                        closestPlanet.coord.distanceTo(unit.position) < closestUnit.position.distanceTo(unit.position)) ->
                        closestPlanet.coord
                    closestUnit != null -> closestUnit.position
                    else -> null
                }

                if (goal != null) {
                    val gridMap = GameGridMap(currentState, aiFaction)
                    val approachGoal = HexCoord.directions
                        .map { goal + it }
                        .filter { gridMap.isPassable(it) }
                        .minByOrNull { it.distanceTo(unit.position) }

                    if (approachGoal != null) {
                        val totalMovement = effectiveMovement(currentState, aiFaction, unit.type.movement)
                        val path = com.novaempire.core.hex.HexPathfinder.findPath(
                            start = unit.position, goal = approachGoal,
                            gridMap = gridMap, maxCost = totalMovement
                        )
                        if (path != null && path.isNotEmpty()) {
                            val destination = path.take(totalMovement).lastOrNull { currentState.units[it] == null }
                            if (destination != null) {
                                currentState = reduce(currentState, GameIntent.MoveUnit(unit.position, destination))
                            }
                        }
                    }
                }
            }
        }

        val refreshedUnits = currentState.units.mapValues {
            if (it.value.faction == aiFaction) it.value.copy(hasMoved = false, hasAttacked = false)
            else it.value
        }
        return currentState.copy(units = refreshedUnits)
    }

    private fun effectiveMovement(state: GameState, faction: Faction, baseMovement: Int): Int =
        (baseMovement + BonusRegistry.sum(BonusType.MOVEMENT_MODIFIER, state.playerStates[faction], state.activeEvent))
            .coerceAtLeast(1)

    private fun evaluateHeroes(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        val availableHeroes = HeroRegistry.ALL_HEROES.filter {
            !playerState.recruitedHeroes.contains(it.id) && playerState.credits >= it.cost
        }
        if (availableHeroes.isEmpty()) return state

        val selectedHero = availableHeroes.find { it.id == HeroRegistry.KAEL }
            ?: availableHeroes.find { it.id == HeroRegistry.ELARA }
            ?: availableHeroes.find { it.id == HeroRegistry.VANCE }
            ?: availableHeroes.find { it.id == HeroRegistry.NIX }

        if (selectedHero != null) {
            val newPlayerStates = state.playerStates.toMutableMap()
            newPlayerStates[faction] = playerState.copy(
                credits = playerState.credits - selectedHero.cost,
                recruitedHeroes = playerState.recruitedHeroes + selectedHero.id
            )
            return state.copy(playerStates = newPlayerStates)
        }
        return state
    }

    private fun evaluateProduction(state: GameState, faction: Faction): GameState {
        val myPlanets = state.map.tiles.values.filter { it.owner == faction }
        if (myPlanets.isEmpty()) return state

        val unitOrder = listOf(UnitType.DREADNOUGHT, UnitType.CARRIER, UnitType.BATTLESHIP, UnitType.CRUISER, UnitType.DEFENSE_PLATFORM, UnitType.FIGHTER, UnitType.SCOUT)

        var nextState = state
        for (planet in myPlanets) {
            val pState = nextState.playerStates[faction] ?: continue
            if (pState.buildQueue.any { it.planetCoord == planet.coord }) continue
            val affordableUnit = unitOrder.find { it.cost <= pState.credits } ?: continue
            val turns = buildTurns(affordableUnit)
            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[faction] = pState.copy(
                credits = pState.credits - affordableUnit.cost,
                buildQueue = pState.buildQueue + BuildOrder(affordableUnit, planet.coord, turns)
            )
            nextState = nextState.copy(playerStates = newPlayerStates)
        }
        return nextState
    }

    private fun evaluateDiplomacy(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState {
        val aiPlayerState = state.playerStates[faction] ?: return state
        var nextState = state

        val aiUnits = state.units.values.filter { it.faction == faction }
        val aiPower = aiPlayerState.credits + aiUnits.sumOf { it.currentHp }

        for (otherFaction in Faction.values()) {
            if (otherFaction == faction || otherFaction == Faction.ANCIENT_NPC) continue
            val otherPlayerState = state.playerStates[otherFaction] ?: continue
            val otherUnits = state.units.values.filter { it.faction == otherFaction }
            val otherPower = otherPlayerState.credits + otherUnits.sumOf { it.currentHp }

            val currentRelation = aiPlayerState.relations[otherFaction] ?: DiplomaticRelation.NEUTRAL

            when {
                otherPower > aiPower * 1.5 &&
                currentRelation != DiplomaticRelation.ALLIANCE &&
                currentRelation != DiplomaticRelation.WAR ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.ALLIANCE))

                aiPower > otherPower * 1.5 &&
                currentRelation != DiplomaticRelation.WAR &&
                currentRelation != DiplomaticRelation.ALLIANCE ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.WAR))

                currentRelation == DiplomaticRelation.WAR && otherPower > aiPower * 2.0 ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.NEUTRAL))
            }
        }
        return nextState
    }

    private fun evaluateEconomyAndTech(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        val affordableTech = TechRegistry.ALL_TECHS.find { tech ->
            val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
            val isUnlocked = playerState.techUnlocked.contains(tech.id)
            val cost = CostCalculator.techCost(tech.id, playerState.techUnlocked, playerState, state.activeEvent)
            isAvailable && !isUnlocked && playerState.credits >= cost
        }

        if (affordableTech != null) {
            val cost = CostCalculator.techCost(affordableTech.id, playerState.techUnlocked, playerState, state.activeEvent)
            val newPlayerStates = state.playerStates.toMutableMap()
            newPlayerStates[faction] = playerState.copy(
                credits = playerState.credits - cost,
                techUnlocked = playerState.techUnlocked + affordableTech.id
            )
            return state.copy(playerStates = newPlayerStates)
        }
        return state
    }
}
