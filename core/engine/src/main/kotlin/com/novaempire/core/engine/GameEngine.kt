package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState

class GameEngine {
    var state: GameState = GameState()
        private set

    fun processIntent(intent: GameIntent) {
        state = reduce(state, intent)
    }

    private fun reduce(state: GameState, intent: GameIntent): GameState {
        return when (intent) {
            is GameIntent.EndTurn -> {
                state.copy(turn = state.turn + 1)
            }
            // Add more intent reducers here
        }
    }
}

sealed class GameIntent {
    object EndTurn : GameIntent()
}
