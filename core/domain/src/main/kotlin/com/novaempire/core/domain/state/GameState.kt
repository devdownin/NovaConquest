package com.novaempire.core.domain.state
import kotlinx.serialization.Serializable

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.hex.HexCoord

@Serializable
data class CombatEvent(
    val attackerCoord: HexCoord,
    val defenderCoord: HexCoord,
    val targetDestroyed: Boolean
)

@Serializable
data class GameState(
    val version: Int = 1,
    val turn: Int = 1,
    val activeFaction: Faction = Faction.DOMINION,
    val humanFaction: Faction = Faction.DOMINION,
    val playerStates: Map<Faction, PlayerState> = emptyMap(),
    val map: GameMap = GameMap(),
    val units: Map<HexCoord, GameUnit> = emptyMap(),
    val activeEvent: GalacticEvent = GalacticEvent.NONE,
    val eventDurationRemaining: Int = 0,
    val winner: Faction? = null,
    val victoryReason: String? = null,
    val lastCombatEvent: CombatEvent? = null
)

@Serializable
data class ResearchProgress(val techId: String, val turnsRemaining: Int)

@Serializable
data class PlayerState(
    val faction: Faction,
    val credits: Int = 10,
    val techUnlocked: Set<String> = emptySet(),
    val researchInProgress: ResearchProgress? = null,
    val exploredHexes: Set<HexCoord> = emptySet(),
    val visibleHexes: Set<HexCoord> = emptySet(),
    val capitalCoord: HexCoord? = null,
    val recruitedHeroes: Set<String> = emptySet(),
    val relations: Map<Faction, com.novaempire.core.domain.models.DiplomaticRelation> = emptyMap()
)
