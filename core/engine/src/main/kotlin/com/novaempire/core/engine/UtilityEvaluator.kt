package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TechRegistry

object UtilityEvaluator : AIStrategy {

    override suspend fun executeAITurn(state: GameState, faction: Faction): GameState {
        kotlinx.coroutines.delay(0) // yield to UI without blocking
        val aiFaction = faction
        var currentState = state

        // 0. Diplomacy Logic
        currentState = evaluateDiplomacy(currentState, aiFaction)

        // 1. Economic / Tech Logic
        currentState = evaluateEconomyAndTech(currentState, aiFaction)

        // 2. Hero Recruitment Logic
        currentState = evaluateHeroes(currentState, aiFaction)

        // 3. Production Logic
        currentState = evaluateProduction(currentState, aiFaction)

        // 4. Tactical Logic (Move, Attack, Siege, Capture)
        val aiPlayerState = currentState.playerStates[aiFaction]
        val myUnits = currentState.units.values.filter { it.faction == aiFaction && (!it.hasMoved || !it.hasAttacked) }

        for (unit in myUnits) {
            val possibleTargets = currentState.units.values.filter {
                it.faction != aiFaction &&
                (aiPlayerState?.relations?.get(it.faction) == com.novaempire.core.domain.models.DiplomaticRelation.WAR || it.faction == Faction.ANCIENT_NPC)
            }
            
            val targetPlanets = currentState.map.tiles.values.filter {
                it.terrain == com.novaempire.core.domain.models.TerrainType.PLANET &&
                it.owner != aiFaction &&
                (it.owner == null || aiPlayerState?.relations?.get(it.owner) != com.novaempire.core.domain.models.DiplomaticRelation.ALLIANCE)
            }

            // Retreat: HP < 30% and enemy within 2 hexes → flee to nearest owned planet
            val hpCritical = unit.currentHp.toFloat() / unit.type.maxHp < 0.30f
            val threatNearby = possibleTargets.any { it.position.distanceTo(unit.position) <= 2 }
            if (hpCritical && !unit.hasMoved && threatNearby && unit.type.movement > 0) {
                val ownedPlanets = currentState.map.tiles.values.filter { it.owner == aiFaction }
                val retreatTarget = ownedPlanets.minByOrNull { it.coord.distanceTo(unit.position) }
                if (retreatTarget != null && retreatTarget.coord != unit.position) {
                    val gridMap = GameGridMap(currentState, aiFaction)
                    val ionPenalty = if (currentState.activeEvent == GalacticEvent.ION_STORM) 1 else 0
                    val totalMovement = (unit.type.movement + unit.faction.bonusMovement - ionPenalty).coerceAtLeast(1)
                    val path = com.novaempire.core.hex.HexPathfinder.findPath(
                        start = unit.position,
                        goal = retreatTarget.coord,
                        gridMap = gridMap,
                        maxCost = totalMovement
                    )
                    if (path != null && path.isNotEmpty()) {
                        val destination = path.take(totalMovement).lastOrNull { currentState.units[it] == null }
                        if (destination != null) {
                            currentState = moveUnit(currentState, unit.position, destination)
                            continue
                        }
                    }
                }
            }

            // A. Check for Capture/Siege if next to a planet
            val adjacentPlanet = targetPlanets.find { it.coord.distanceTo(unit.position) <= 1 }
            if (adjacentPlanet != null && !unit.hasAttacked) {
                if (adjacentPlanet.systemLevel == 0) {
                    currentState = CombatResolver.capturePlanet(currentState, unit.position, adjacentPlanet.coord)
                } else {
                    currentState = CombatResolver.siegePlanet(currentState, unit.position, adjacentPlanet.coord)
                }
                continue
            }

            // B. Check for Combat
            val adjacentTarget = possibleTargets.find { it.position.distanceTo(unit.position) <= unit.type.range }
            if (adjacentTarget != null && !unit.hasAttacked) {
                currentState = CombatResolver.resolveCombat(currentState, unit.position, adjacentTarget.position)
            } 
            
            // C. Move towards closest interest (Unit or Planet)
            else if (!unit.hasMoved && unit.type.movement > 0) {
                val closestUnit = possibleTargets.minByOrNull { it.position.distanceTo(unit.position) }
                val closestPlanet = targetPlanets.minByOrNull { it.coord.distanceTo(unit.position) }
                
                val goal = when {
                    closestPlanet != null && (closestUnit == null || closestPlanet.coord.distanceTo(unit.position) < closestUnit.position.distanceTo(unit.position)) -> closestPlanet.coord
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
                        val ionPenalty = if (currentState.activeEvent == GalacticEvent.ION_STORM) 1 else 0
                        val totalMovement = (unit.type.movement + unit.faction.bonusMovement - ionPenalty).coerceAtLeast(1)
                        val path = com.novaempire.core.hex.HexPathfinder.findPath(
                            start = unit.position,
                            goal = approachGoal,
                            gridMap = gridMap,
                            maxCost = totalMovement
                        )

                        if (path != null && path.isNotEmpty()) {
                            val destination = path.take(totalMovement)
                                .lastOrNull { currentState.units[it] == null }
                            if (destination != null) {
                                currentState = moveUnit(currentState, unit.position, destination)
                            }
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

    private fun evaluateHeroes(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        
        // AI chooses a hero based on current needs
        val availableHeroes = com.novaempire.core.domain.models.HeroRegistry.ALL_HEROES.filter { 
            !playerState.recruitedHeroes.contains(it.id) && playerState.credits >= it.cost 
        }
        
        if (availableHeroes.isEmpty()) return state

        // Priority: Kael (Tech) > Elara (Economy) > Vance (Combat) > Nix (Healing)
        val selectedHero = availableHeroes.find { it.id == HeroRegistry.KAEL }
            ?: availableHeroes.find { it.id == HeroRegistry.ELARA }
            ?: availableHeroes.find { it.id == HeroRegistry.VANCE }
            ?: availableHeroes.find { it.id == HeroRegistry.NIX }

        if (selectedHero != null) {
            val newPlayerState = playerState.copy(
                credits = playerState.credits - selectedHero.cost,
                recruitedHeroes = playerState.recruitedHeroes + selectedHero.id
            )
            val newPlayerStates = state.playerStates.toMutableMap()
            newPlayerStates[faction] = newPlayerState
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
            val turns = aiBuildTurns(affordableUnit)
            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[faction] = pState.copy(
                credits = pState.credits - affordableUnit.cost,
                buildQueue = pState.buildQueue + com.novaempire.core.domain.state.BuildOrder(affordableUnit, planet.coord, turns)
            )
            nextState = nextState.copy(playerStates = newPlayerStates)
        }

        return nextState
    }

    private fun aiBuildTurns(unitType: UnitType): Int = when (unitType) {
        UnitType.SCOUT, UnitType.FIGHTER -> 1
        UnitType.CRUISER, UnitType.BATTLESHIP, UnitType.CARRIER, UnitType.DEFENSE_PLATFORM -> 2
        UnitType.DREADNOUGHT -> 3
    }

    private fun evaluateDiplomacy(state: GameState, faction: Faction): GameState {
        val aiPlayerState = state.playerStates[faction] ?: return state
        var nextState = state

        // Calculate AI power (Credits + Unit HP sum)
        val aiUnits = state.units.values.filter { it.faction == faction }
        val aiPower = aiPlayerState.credits + aiUnits.sumOf { it.currentHp }

        for (otherFaction in Faction.values()) {
            if (otherFaction == faction || otherFaction == Faction.ANCIENT_NPC) continue

            val otherPlayerState = state.playerStates[otherFaction] ?: continue
            val otherUnits = state.units.values.filter { it.faction == otherFaction }
            val otherPower = otherPlayerState.credits + otherUnits.sumOf { it.currentHp }

            val currentRelation = aiPlayerState.relations[otherFaction] ?: com.novaempire.core.domain.models.DiplomaticRelation.NEUTRAL

            // If the other faction is much stronger, propose an ALLIANCE
            if (otherPower > aiPower * 1.5 && currentRelation != com.novaempire.core.domain.models.DiplomaticRelation.ALLIANCE && currentRelation != com.novaempire.core.domain.models.DiplomaticRelation.WAR) {
                nextState = changeRelation(nextState, faction, otherFaction, com.novaempire.core.domain.models.DiplomaticRelation.ALLIANCE)
            }
            // If the AI is much stronger and not already at war, declare WAR
            else if (aiPower > otherPower * 1.5 && currentRelation != com.novaempire.core.domain.models.DiplomaticRelation.WAR && currentRelation != com.novaempire.core.domain.models.DiplomaticRelation.ALLIANCE) {
                nextState = changeRelation(nextState, faction, otherFaction, com.novaempire.core.domain.models.DiplomaticRelation.WAR)
            }
            // If at war, but the enemy became too strong, try to revert to NEUTRAL (Peace treaty)
            else if (currentRelation == com.novaempire.core.domain.models.DiplomaticRelation.WAR && otherPower > aiPower * 2.0) {
                nextState = changeRelation(nextState, faction, otherFaction, com.novaempire.core.domain.models.DiplomaticRelation.NEUTRAL)
            }
        }

        return nextState
    }

    private fun changeRelation(state: GameState, faction1: Faction, faction2: Faction, relation: com.novaempire.core.domain.models.DiplomaticRelation): GameState {
        val p1State = state.playerStates[faction1] ?: return state
        val p2State = state.playerStates[faction2] ?: return state

        val p1Rels = p1State.relations.toMutableMap()
        p1Rels[faction2] = relation

        val p2Rels = p2State.relations.toMutableMap()
        p2Rels[faction1] = relation

        val newPlayerStates = state.playerStates.toMutableMap()
        newPlayerStates[faction1] = p1State.copy(relations = p1Rels)
        newPlayerStates[faction2] = p2State.copy(relations = p2Rels)

        return state.copy(playerStates = newPlayerStates)
    }

    private fun evaluateEconomyAndTech(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state

        // Simple AI: Buy first affordable tech
        val hasKael = playerState.recruitedHeroes.contains(HeroRegistry.KAEL)
        val affordableTech = TechRegistry.ALL_TECHS.find { tech ->
            val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
            val isUnlocked = playerState.techUnlocked.contains(tech.id)
            val cost = CostCalculator.techCost(tech.id, playerState.techUnlocked, hasKael, faction.bonusTechDiscount)
            isAvailable && !isUnlocked && playerState.credits >= cost
        }

        if (affordableTech != null) {
            val cost = CostCalculator.techCost(affordableTech.id, playerState.techUnlocked, hasKael, faction.bonusTechDiscount)
            
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

    private fun moveUnit(state: GameState, from: HexCoord, to: HexCoord): GameState {
        val unit = state.units[from] ?: return state
        if (state.units.containsKey(to)) return state

        val updatedUnits = state.units.toMutableMap()
        updatedUnits.remove(from)
        updatedUnits[to] = unit.copy(position = to, hasMoved = true)
        return state.copy(units = updatedUnits)
    }
}
