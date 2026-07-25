package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState

/** [winner] is null for a draw (mutual annihilation) — the game still ends, nobody claims it. */
data class VictoryResult(val winner: Faction?, val reason: String)

object VictoryChecker {

    fun check(state: GameState): VictoryResult? {
        // 0. Campaign Objectives (if active)
        val activeMissionId = state.campaignState.activeMissionId
        if (activeMissionId != null) {
            val mission = com.novaempire.core.domain.models.CampaignRegistry.MISSIONS.find { it.id == activeMissionId }
            if (mission != null) {
                // Check if the player has lost (no units or planets left)
                val playerHasUnits = state.units.values.any { it.faction == mission.playerFaction }
                val playerHasPlanets = state.map.tiles.values.any { it.owner == mission.playerFaction }
                if (!playerHasUnits && !playerHasPlanets) {
                    return VictoryResult(mission.enemyFaction, "Defeat! Your forces have been annihilated.")
                }

                val isVictorious = when (mission.objective.type) {
                    com.novaempire.core.domain.models.CampaignObjectiveType.SURVIVE_TURNS -> state.turn >= mission.objective.targetValue
                    com.novaempire.core.domain.models.CampaignObjectiveType.ACCUMULATE_CREDITS -> (state.playerStates[mission.playerFaction]?.credits ?: 0) >= mission.objective.targetValue
                    com.novaempire.core.domain.models.CampaignObjectiveType.DEFEAT_FACTION -> !state.units.values.any { it.faction == mission.enemyFaction } && !state.map.tiles.values.any { it.owner == mission.enemyFaction }
                    else -> false
                }
                if (isVictorious) {
                    return VictoryResult(mission.playerFaction, "Campaign Mission Complete: ${mission.name}")
                }
                return null // If in a campaign, standard victory conditions are ignored
            }
        }
        // Key the pass-through on the reason, not the winner: a draw ends the game with a reason
        // but no winner, and must stay settled rather than being re-evaluated every turn.
        val settled = state.victoryReason
        if (settled != null) return VictoryResult(state.winner, settled)

        // 1. Tech Victory: unlock all technologies. Check the actual ids rather than the count —
        // counting declares a winner as soon as the player holds *as many* entries as the registry
        // has, so a save carrying an id that was later renamed or dropped from ALL_TECHS would
        // trigger a bogus victory without the player ever finishing the tree.
        val allTechIds = TechRegistry.ALL_TECHS.map { it.id }
        state.playerStates.values.find { player -> allTechIds.all { it in player.techUnlocked } }?.let {
            return VictoryResult(it.faction, "Technological Dominance")
        }

        // 2. Economic Victory: 2500 Credits
        state.playerStates.values.find { it.credits >= 2500 }?.let {
            return VictoryResult(it.faction, "Economic Supremacy")
        }

        // 3. Territorial Victory: all Zodiac nodes
        if (state.map.archetype == MapArchetype.ZODIAC) {
            val zodiacCoords = state.map.zodiacPlanets
            // Playable factions only (V4): the sweep used to include ANCIENT_NPC, which could
            // "win" the game despite having no PlayerState and never taking a turn.
            Faction.values().filter { it != Faction.ANCIENT_NPC }.find { faction ->
                zodiacCoords.isNotEmpty() && zodiacCoords.all { state.map.tiles[it]?.owner == faction }
            }?.let {
                return VictoryResult(it, "Celestial Alignment")
            }
        }

        // 4. Galactic Domination: hold 60 %+ of planets for 6 consecutive global turns
        state.dominationTurns.entries.find { it.value >= 6 }?.let {
            return VictoryResult(it.key, "Galactic Domination")
        }

        // 5. Military Conquest: only one faction still has units or planets
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        val survivors = activeFactions.filter { faction ->
            state.units.values.any { it.faction == faction } ||
            state.map.tiles.values.any { it.terrain == TerrainType.PLANET && it.owner == faction }
        }
        if (survivors.size == 1) {
            return VictoryResult(survivors.first(), "Military Conquest")
        }
        // Mutual annihilation: the last sides wiped each other out. Nobody can ever act again, so a
        // draw beats grinding on to turn 100 on an empty board. Requiring a non-empty map keeps this
        // out of the way of skeleton states that legitimately have no territory yet.
        if (survivors.isEmpty() && state.map.tiles.isNotEmpty()) {
            return VictoryResult(null, "Mutual Annihilation — no empire survives")
        }

        // 6. Time Limit: 100 turns — highest empire score wins
        if (state.turn >= 100) {
            state.playerStates.values.maxByOrNull { empireScore(state, it) }?.let {
                return VictoryResult(it.faction, "Time Limit Reached - Score Victory")
            }
        }

        return null
    }

    /**
     * End-of-game score (V3). Credits alone used to decide the turn-100 winner, so a hoarder who
     * never left home beat the empire that actually conquered the galaxy. Territory, fleet and
     * research now count too.
     */
    fun empireScore(state: GameState, player: com.novaempire.core.domain.state.PlayerState): Int {
        val planets = state.map.tiles.values.filter { it.terrain == TerrainType.PLANET && it.owner == player.faction }
        val territory = planets.sumOf { 40 + it.systemLevel * 10 }
        val fleet = state.units.values.filter { it.faction == player.faction }.sumOf { it.type.cost }
        val research = player.techUnlocked.size * 20
        return player.credits + territory + fleet + research
    }
}
