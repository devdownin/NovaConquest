package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState

data class VictoryResult(val winner: Faction, val reason: String)

object VictoryChecker {

    fun check(state: GameState): VictoryResult? {
        val existing = state.winner
        if (existing != null) return VictoryResult(existing, state.victoryReason ?: "")

        // 1. Tech Victory: unlock all technologies
        state.playerStates.values.find { it.techUnlocked.size >= TechRegistry.ALL_TECHS.size }?.let {
            return VictoryResult(it.faction, "Technological Dominance")
        }

        // 2. Economic Victory: 1000 Credits
        state.playerStates.values.find { it.credits >= 1000 }?.let {
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

        // 4. Galactic Domination: hold 60 %+ of planets for 3 consecutive global turns
        state.dominationTurns.entries.find { it.value >= 3 }?.let {
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

        // 6. Time Limit: 100 turns — highest credits wins
        if (state.turn >= 100) {
            state.playerStates.values.maxByOrNull { it.credits }?.let {
                return VictoryResult(it.faction, "Time Limit Reached - Score Victory")
            }
        }

        return null
    }
}
