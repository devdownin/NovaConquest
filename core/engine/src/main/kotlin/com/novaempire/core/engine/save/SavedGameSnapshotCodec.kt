package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object SavedGameSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String {
        return json.encodeToString(state)
    }

    fun decode(encoded: String): GameState {
        return json.decodeFromString(encoded)
    }
}
