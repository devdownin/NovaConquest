package com.novaempire.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.novaempire.core.engine.GameEngine
import com.novaempire.core.engine.GameIntent
import kotlinx.coroutines.flow.StateFlow

class GameViewModel : ViewModel() {
    private val engine = GameEngine()

    val gameState: StateFlow<com.novaempire.core.domain.state.GameState> = engine.state

    fun dispatch(intent: GameIntent) {
        engine.processIntent(intent)
    }
}
