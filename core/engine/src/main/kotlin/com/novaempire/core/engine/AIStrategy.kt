package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState

fun interface AIStrategy {
    suspend fun executeAITurn(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState
}
