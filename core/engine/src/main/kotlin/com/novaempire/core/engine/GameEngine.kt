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
        val playerStates = Faction.values().associateWith { faction ->
            val capital = if (faction == Faction.DOMINION && spawnPoints.isNotEmpty()) spawnPoints[0]
                          else if (faction == Faction.TRADERS && spawnPoints.size > 1) spawnPoints[1]
                          else null
            PlayerState(faction = faction, capitalCoord = capital)
        }.toMutableMap()
        initialState = initialState.copy(playerStates = playerStates)

        // Calculate initial vision
        return updateVision(initialState)
    }

    private fun checkVictoryConditions(state: GameState): GameState {
        if (state.winner != null) return state

        // Tech Victory: 6 techs unlocked
        val techWinner = state.playerStates.values.find { it.techUnlocked.size >= 6 }
        if (techWinner != null) {
            return state.copy(winner = techWinner.faction, victoryReason = "Technological Dominance")
        }

        // Domination Victory: (Simplified for demo) Owns 5 planets
        // Alternatively, since planets don't strictly have ownership yet, let's just use Turn Limit
        if (state.turn >= 60) {
            // Highest credits wins
            val scoreWinner = state.playerStates.values.maxByOrNull { it.credits }
            if (scoreWinner != null) {
                return state.copy(winner = scoreWinner.faction, victoryReason = "Time Limit Reached - Score Victory")
            }
        }

        return state
    }

    fun processIntent(intent: GameIntent) {
        _state.update { currentState ->
            val nextState = reduce(currentState, intent)
            checkVictoryConditions(nextState)
        }
    }

    private fun reduce(state: GameState, intent: GameIntent): GameState {
        return when (intent) {

            is GameIntent.StartNewGame -> {
                createInitialState()
            }
            is GameIntent.LoadGame -> {
                intent.loadedState
            }
            is GameIntent.EndTurn -> {
                val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
                val nextFactionIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
                var nextFaction = allFactions[nextFactionIndex]

                var nextState = state.copy(activeFaction = nextFaction)

                if (nextFactionIndex == 0) {
                    nextState = nextState.copy(turn = state.turn + 1)
                    nextState = triggerGalacticEvent(nextState)
                }

                // Apply End of Turn Bonuses for the active faction (the one that just finished)
                val activePlayerState = state.playerStates[state.activeFaction]
                val hasNix = activePlayerState?.recruitedHeroes?.contains("hero_nix") == true

                var unitsAfterTurn = nextState.units
                if (hasNix) {
                    unitsAfterTurn = unitsAfterTurn.mapValues { (coord, unit) ->
                        if (unit.faction == state.activeFaction && unit.currentHp < unit.type.maxHp) {
                            unit.copy(currentHp = Math.min(unit.type.maxHp, unit.currentHp + 1))
                        } else {
                            unit
                        }
                    }
                }

                // Add credits for the next faction starting their turn
                val nextPlayerState = nextState.playerStates[nextFaction]
                val hasElara = nextPlayerState?.recruitedHeroes?.contains("hero_elara") == true

                var systemIncome = 10 // Base income
                if (hasElara) {
                    systemIncome += (systemIncome * 0.10).toInt() + 2 // +10% and flat +2 for early game impact
                }

                // Event modifiers
                if (nextState.activeEvent == GalacticEvent.ECONOMIC_BOOM) {
                    systemIncome += 3
                }

                val nextCredits = (nextPlayerState?.credits ?: 0) + systemIncome
                val newPlayerStates = nextState.playerStates.toMutableMap()
                if (nextPlayerState != null) {
                    newPlayerStates[nextFaction] = nextPlayerState.copy(credits = nextCredits)
                }
                nextState = nextState.copy(units = unitsAfterTurn, playerStates = newPlayerStates)


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
            is GameIntent.AttackUnit -> {
                val unit = state.units[intent.attacker]
                if (unit != null && unit.faction == state.activeFaction && !unit.hasAttacked) {
                    val nextState = CombatResolver.resolveCombat(state, intent.attacker, intent.defender)
                    updateVision(nextState)
                } else state
            }
            is GameIntent.ResearchTech -> {
                val playerState = state.playerStates[state.activeFaction] ?: return state
                val hasKael = playerState.recruitedHeroes.contains("hero_kael")
                val cost = com.novaempire.core.domain.models.TechRegistry.calculateCost(intent.techId, playerState.techUnlocked, hasKael)

                val tech = com.novaempire.core.domain.models.TechRegistry.getTech(intent.techId) ?: return state
                val isAvailable = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
                val isAlreadyUnlocked = playerState.techUnlocked.contains(intent.techId)

                if (isAvailable && !isAlreadyUnlocked && playerState.credits >= cost) {
                    val newPlayerState = playerState.copy(
                        credits = playerState.credits - cost,
                        techUnlocked = playerState.techUnlocked + intent.techId
                    )
                    val newPlayerStates = state.playerStates.toMutableMap()
                    newPlayerStates[state.activeFaction] = newPlayerState

                    val nextState = state.copy(playerStates = newPlayerStates)
                    updateVision(nextState)
                } else {
                    state
                }
            }
            is GameIntent.BuildUnit -> {
                val playerState = state.playerStates[state.activeFaction] ?: return state
                val cost = intent.unitType.cost
                val spawnCenter = intent.location ?: playerState.capitalCoord ?: return state

                if (playerState.credits >= cost) {
                    val gridMap = GameGridMap(state)
                    val spawnCandidates = listOf(spawnCenter) + gridMap.getNeighbors(spawnCenter)
                    val spawnHex = spawnCandidates.firstOrNull { state.units[it] == null && gridMap.isPassable(it) }

                    if (spawnHex != null) {
                        val newPlayerState = playerState.copy(credits = playerState.credits - cost)
                        val newPlayerStates = state.playerStates.toMutableMap()
                        newPlayerStates[state.activeFaction] = newPlayerState

                        val newUnit = GameUnit(
                            type = intent.unitType,
                            faction = state.activeFaction,
                            position = spawnHex,
                            currentHp = intent.unitType.maxHp,
                            hasMoved = true,
                            hasAttacked = true
                        )
                        val updatedUnits = state.units.toMutableMap()
                        updatedUnits[spawnHex] = newUnit

                        val nextState = state.copy(playerStates = newPlayerStates, units = updatedUnits)
                        updateVision(nextState)
                    } else state
                } else state
            }
            is GameIntent.RecruitHero -> {
                val playerState = state.playerStates[state.activeFaction] ?: return state
                val hero = com.novaempire.core.domain.models.HeroRegistry.getHero(intent.heroId) ?: return state

                if (playerState.credits >= hero.cost && !playerState.recruitedHeroes.contains(hero.id)) {
                    val newPlayerState = playerState.copy(
                        credits = playerState.credits - hero.cost,
                        recruitedHeroes = playerState.recruitedHeroes + hero.id
                    )
                    val newPlayerStates = state.playerStates.toMutableMap()
                    newPlayerStates[state.activeFaction] = newPlayerState
                    state.copy(playerStates = newPlayerStates)
                } else state
            }
            is GameIntent.ChangeRelation -> {
                val playerState = state.playerStates[state.activeFaction] ?: return state
                val newRelations = playerState.relations.toMutableMap()
                newRelations[intent.targetFaction] = intent.newRelation

                val newPlayerState = playerState.copy(relations = newRelations)
                val newPlayerStates = state.playerStates.toMutableMap()
                newPlayerStates[state.activeFaction] = newPlayerState

                // Also update the target's relation to us for symmetry
                val targetState = newPlayerStates[intent.targetFaction]
                if (targetState != null) {
                    val targetRelations = targetState.relations.toMutableMap()
                    targetRelations[state.activeFaction] = intent.newRelation
                    newPlayerStates[intent.targetFaction] = targetState.copy(relations = targetRelations)
                }

                state.copy(playerStates = newPlayerStates)
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
    data class AttackUnit(val attacker: HexCoord, val defender: HexCoord) : GameIntent()
    data class ResearchTech(val techId: String) : GameIntent()
    data class BuildUnit(val unitType: UnitType, val location: HexCoord? = null) : GameIntent()
    data class RecruitHero(val heroId: String) : GameIntent()
    data class ChangeRelation(val targetFaction: Faction, val newRelation: com.novaempire.core.domain.models.DiplomaticRelation) : GameIntent()
    object StartNewGame : GameIntent()
    data class LoadGame(val loadedState: GameState) : GameIntent()
}
