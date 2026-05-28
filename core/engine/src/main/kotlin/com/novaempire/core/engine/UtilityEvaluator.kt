package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.TechRegistry

object UtilityEvaluator {

    suspend fun executeAITurn(state: GameState, aiFaction: Faction): GameState {
        kotlinx.coroutines.delay(500) // Simulate complex calculation
        var currentState = state

        // 0. Diplomacy Logic
        currentState = evaluateDiplomacy(currentState, aiFaction)

        // 1. Economic / Tech Logic
        currentState = evaluateEconomyAndTech(currentState, aiFaction)

        // 2. Production Logic
        currentState = evaluateProduction(currentState, aiFaction)

        // 3. Tactical Logic (Move and Attack)
        val aiPlayerState = currentState.playerStates[aiFaction]
        val myUnits = currentState.units.values.filter { it.faction == aiFaction && (!it.hasMoved || !it.hasAttacked) }

        for (unit in myUnits) {
            val possibleTargets = currentState.units.values.filter {
                it.faction != aiFaction &&
                (aiPlayerState?.relations?.get(it.faction) == com.novaempire.core.domain.models.DiplomaticRelation.WAR || it.faction == Faction.ANCIENT_NPC)
            }
            val adjacentTarget = possibleTargets.find { it.position.distanceTo(unit.position) <= unit.type.range }

            if (adjacentTarget != null && !unit.hasAttacked) {
                // Attack if in range
                currentState = CombatResolver.resolveCombat(currentState, unit.position, adjacentTarget.position)
            } else if (!unit.hasMoved) {
                // Move towards closest target if not adjacent.
                val closestTarget = possibleTargets.minByOrNull { it.position.distanceTo(unit.position) }
                if (closestTarget != null) {
                    val gridMap = GameGridMap(currentState)
                    // The enemy's own hex is impassable, so aim for the passable hex next to
                    // it that is closest to us, then walk as far as our movement allows.
                    val approachGoal = HexCoord.directions
                        .map { closestTarget.position + it }
                        .filter { gridMap.isPassable(it) }
                        .minByOrNull { it.distanceTo(unit.position) }

                    if (approachGoal != null) {
                        val path = com.novaempire.core.hex.HexPathfinder.findPath(
                            start = unit.position,
                            goal = approachGoal,
                            gridMap = gridMap
                        )

                        if (path != null && path.isNotEmpty()) {
                            val destination = path.take(unit.type.movement)
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
        val affordableTech = TechRegistry.ALL_TECHS.find { tech ->
            val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
            val isUnlocked = playerState.techUnlocked.contains(tech.id)
            val hasKael = playerState.recruitedHeroes.contains("hero_kael")
            val cost = TechRegistry.calculateCost(tech.id, playerState.techUnlocked, hasKael)
            isAvailable && !isUnlocked && playerState.credits >= cost
        }

        if (affordableTech != null) {
            val hasKael = playerState.recruitedHeroes.contains("hero_kael")
            val cost = TechRegistry.calculateCost(affordableTech.id, playerState.techUnlocked, hasKael)
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
