package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.state.GameState

data class VictoryResult(val winner: Faction, val reason: String)

object VictoryChecker {

    fun check(state: GameState): VictoryResult? {
        if (state.winner != null) return VictoryResult(state.winner, state.victoryReason ?: "")

        // 1. Tech Victory: 6 techs unlocked
        state.playerStates.values.find { it.techUnlocked.size >= 6 }?.let {
            return VictoryResult(it.faction, "Technological Dominance")
        }

        // 2. Economic Victory: 500 Credits
        state.playerStates.values.find { it.credits >= 500 }?.let {
            return VictoryResult(it.faction, "Economic Supremacy")
        }

        // 3. Territorial Victory: all Zodiac nodes
        if (state.map.archetype == MapArchetype.ZODIAC) {
            val zodiacCoords = state.map.zodiacPlanets
            Faction.values().find { faction ->
                zodiacCoords.isNotEmpty() && zodiacCoords.all { state.map.tiles[it]?.owner == faction }
            }?.let {
                return VictoryResult(it, "Celestial Alignment")
            }
        }

        // 4. Time Limit: 60 turns — highest credits wins
        if (state.turn >= 60) {
            state.playerStates.values.maxByOrNull { it.credits }?.let {
                return VictoryResult(it.faction, "Time Limit Reached - Score Victory")
            }
        }

        return null
    }
}
