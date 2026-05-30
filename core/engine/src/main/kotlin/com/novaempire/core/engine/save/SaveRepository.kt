package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState

interface SaveRepository {
    fun saveGame(state: GameState)
    fun loadLatestGame(): GameState?
    fun hasSavedGame(): Boolean
}
