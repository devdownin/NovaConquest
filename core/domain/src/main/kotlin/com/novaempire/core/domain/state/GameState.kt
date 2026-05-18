package com.novaempire.core.domain.state

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.hex.HexCoord

data class GameState(
    val turn: Int = 1,
    val activeFaction: Faction = Faction.DOMINION,
    val playerStates: Map<Faction, PlayerState> = emptyMap(),
    val map: GameMap = GameMap(),
    val units: Map<HexCoord, GameUnit> = emptyMap()
)

data class PlayerState(
    val faction: Faction,
    val credits: Int = 10,
    val techUnlocked: Set<String> = emptySet()
)
