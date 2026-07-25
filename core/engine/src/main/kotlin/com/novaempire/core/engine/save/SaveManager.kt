package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import java.io.File

class SaveManager(private val saveDirectory: File) : SaveRepository {

    init {
        saveDirectory.mkdirs()
        File(saveDirectory, "quarantine").mkdirs()
    }

    override fun saveGame(state: GameState): Boolean {
        try {
            val encoded = SavedGameSnapshotCodec.encode(state)
            val file1 = File(saveDirectory, "autosave_1.json")
            val file2 = File(saveDirectory, "autosave_2.json")
            val file3 = File(saveDirectory, "autosave_3.json")
            val tmp  = File(saveDirectory, "autosave_1.json.tmp")

            if (file2.exists()) file2.copyTo(file3, overwrite = true)
            if (file1.exists()) file1.copyTo(file2, overwrite = true)

            tmp.writeText(encoded)
            if (!tmp.exists()) return false
            file1.delete()
            return tmp.renameTo(file1)
        } catch (e: Exception) {
            // Report the failure instead of swallowing it: a full disk or a permission error
            // used to leave the player believing the game had been auto-saved.
            e.printStackTrace()
            return false
        }
    }

    override fun loadLatestGame(): LoadResult {
        val slots = listOf(
            File(saveDirectory, "autosave_1.json"),
            File(saveDirectory, "autosave_2.json"),
            File(saveDirectory, "autosave_3.json")
        )

        if (slots.none { it.exists() }) return LoadResult.NoSave

        var versionError: String? = null

        for (file in slots) {
            if (!file.exists()) continue
            try {
                return LoadResult.Success(SavedGameSnapshotCodec.decode(file.readText()))
            } catch (e: SaveVersionException) {
                // The file is valid but was written by a newer app version — do NOT quarantine it,
                // just skip and try older slots. Remember the message in case all slots fail.
                if (versionError == null) versionError = e.message
            } catch (e: Exception) {
                val quarantine = File(saveDirectory, "quarantine/${file.name}_${System.currentTimeMillis()}.bak")
                file.renameTo(quarantine)
            }
        }

        return LoadResult.Failed(
            versionError ?: "Toutes les sauvegardes sont corrompues et ont été mises en quarantaine."
        )
    }

    /**
     * True when ANY slot holds a save. Checking only slot 1 hid recoverable games: a corrupt
     * slot 1 is quarantined (moved away) by [loadLatestGame], after which the menu's "resume"
     * entry went dead even though slots 2/3 still loaded fine.
     */
    override fun hasSavedGame(): Boolean =
        listOf("autosave_1.json", "autosave_2.json", "autosave_3.json")
            .any { File(saveDirectory, it).exists() }
}
