package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState

/** No-op AI for unit tests: returns state unchanged, no delay. */
class NoOpAI : AIStrategy {
    override suspend fun executeAITurn(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState = state
}
