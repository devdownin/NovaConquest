package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState

sealed class LoadResult {
    data class Success(val state: GameState) : LoadResult()
    object NoSave : LoadResult()
    data class Failed(val reason: String) : LoadResult()
}

interface SaveRepository {
    /** Persists [state]; returns false if the save could not be written (disk full, permissions…). */
    fun saveGame(state: GameState): Boolean
    fun loadLatestGame(): LoadResult
    fun hasSavedGame(): Boolean
}
