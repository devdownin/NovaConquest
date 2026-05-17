package com.novaempire.core.domain.state

import com.novaempire.core.domain.models.Faction

data class GameState(
    val turn: Int = 1,
    val activeFaction: Faction = Faction.DOMINION,
    val playerStates: Map<Faction, PlayerState> = emptyMap(),
    // val map: GameMap = GameMap() // Will be added when GameMap is ready
)

data class PlayerState(
    val faction: Faction,
    val credits: Int = 10,
    val techUnlocked: Set<String> = emptySet()
)
