package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class SaveVersionException(message: String) : Exception(message)

object SavedGameSnapshotCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    fun encode(state: GameState): String {
        return json.encodeToString(state.copy(version = CURRENT_VERSION))
    }

    fun decode(encoded: String): GameState {
        val state = json.decodeFromString<GameState>(encoded)
        if (state.version > CURRENT_VERSION) {
            throw SaveVersionException(
                "Cette sauvegarde (v${state.version}) requiert une version plus récente de l'app (v$CURRENT_VERSION installée). Mise à jour nécessaire."
            )
        }
        return state
    }
}
