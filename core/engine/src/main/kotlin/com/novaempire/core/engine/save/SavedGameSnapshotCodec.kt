package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        // Inspect the raw JSON first: an older save may not fit the current data classes, so its
        // version has to be read — and any migration applied — before deserialising.
        val root = json.parseToJsonElement(encoded).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: SaveMigrations.OLDEST_SUPPORTED_VERSION  // pre-versioning save = oldest schema

        if (version > CURRENT_VERSION) {
            throw SaveVersionException(
                "Cette sauvegarde (v$version) requiert une version plus récente de l'app (v$CURRENT_VERSION installée). Mise à jour nécessaire."
            )
        }

        val upgraded = SaveMigrations.migrate(root, version, CURRENT_VERSION)
        return json.decodeFromJsonElement(upgraded)
    }
}
