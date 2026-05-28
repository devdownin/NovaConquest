package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object SavedGameSnapshotCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String {
        return json.encodeToString(state.copy(version = CURRENT_VERSION))
    }

    fun decode(encoded: String): GameState {
        val state = json.decodeFromString<GameState>(encoded)
        if (state.version > CURRENT_VERSION) {
            throw Exception("Save file version (${state.version}) is newer than the current engine version ($CURRENT_VERSION). Please update the app.")
        }
        return state
    }
}
