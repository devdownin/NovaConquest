package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState

sealed class LoadResult {
    data class Success(val state: GameState) : LoadResult()
    object NoSave : LoadResult()
    data class Failed(val reason: String) : LoadResult()
}

interface SaveRepository {
    fun saveGame(state: GameState)
    fun loadLatestGame(): LoadResult
    fun hasSavedGame(): Boolean
}
