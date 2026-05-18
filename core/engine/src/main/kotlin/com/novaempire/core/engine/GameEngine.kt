package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameEngine {
    private val _state = MutableStateFlow(GameState(map = MapFactory.generateMap(radius = 4)))
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun processIntent(intent: GameIntent) {
        _state.update { currentState ->
            reduce(currentState, intent)
        }
    }

    private fun reduce(state: GameState, intent: GameIntent): GameState {
        return when (intent) {
            is GameIntent.EndTurn -> {
                // TODO: Trigger AI turn resolution here in a real scenario
                state.copy(turn = state.turn + 1)
            }
            is GameIntent.SelectFaction -> {
                state.copy(activeFaction = intent.faction)
            }
            // Add more intent reducers here
        }
    }
}

sealed class GameIntent {
    object EndTurn : GameIntent()
    data class SelectFaction(val faction: com.novaempire.core.domain.models.Faction) : GameIntent()
}
