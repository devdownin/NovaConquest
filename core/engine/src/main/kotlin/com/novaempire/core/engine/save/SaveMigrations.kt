package com.novaempire.core.engine.save

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One schema step: rewrites a save's raw JSON from [fromVersion] to `fromVersion + 1`.
 *
 * Migrations deliberately operate on the JSON tree rather than on `GameState`, because an old save
 * may not fit the current data classes at all (renamed or removed fields, changed shapes) — by the
 * time deserialisation runs it is already too late to repair it.
 */
class SaveMigration(val fromVersion: Int, val apply: (JsonObject) -> JsonObject)

/**
 * Upgrade path for saves written by older builds.
 *
 * Adding a field with a default needs **no** migration — kotlinx.serialization fills it in, which is
 * what keeps existing saves loading today (see `SavedGameSnapshotCodecTest`). A migration is
 * required only for a genuinely breaking change: renaming or removing a field, or changing its
 * shape/meaning. To ship one:
 *
 *  1. bump [SavedGameSnapshotCodec.CURRENT_VERSION];
 *  2. append a [SaveMigration] here whose `fromVersion` is the *previous* version;
 *  3. cover it with a test that feeds real old-format JSON through [migrate].
 *
 * [ALL] is empty while `CURRENT_VERSION` is 1: no older schema exists in the wild yet.
 */
object SaveMigrations {

    val ALL: List<SaveMigration> = emptyList()

    /** Oldest schema we can read. Saves written before versioning existed count as this version. */
    const val OLDEST_SUPPORTED_VERSION = 1

    /**
     * Applies every step needed to bring [root] from [from] up to [to], then stamps the resulting
     * version so the decoded state reports the schema it actually holds.
     *
     * [steps] is injectable so the pipeline itself can be tested while [ALL] is still empty.
     * Throws [SaveVersionException] when a step is missing — the save is intact but unreadable by
     * this build, so [SaveManager] skips it instead of quarantining it.
     */
    fun migrate(
        root: JsonObject,
        from: Int,
        to: Int,
        steps: List<SaveMigration> = ALL
    ): JsonObject {
        if (from >= to) return root
        var current = root
        var version = from
        while (version < to) {
            val step = steps.firstOrNull { it.fromVersion == version }
                ?: throw SaveVersionException(
                    "Sauvegarde v$version illisible : aucune migration disponible vers v${version + 1}."
                )
            current = step.apply(current)
            version++
        }
        return JsonObject(current + ("version" to JsonPrimitive(to)))
    }
}
