package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import java.io.File

class SaveManager(private val saveDirectory: File) : SaveRepository {

    init {
        saveDirectory.mkdirs()
        File(saveDirectory, "quarantine").mkdirs()
    }

    override fun saveGame(state: GameState) {
        try {
            val encoded = SavedGameSnapshotCodec.encode(state)
            val file1 = File(saveDirectory, "autosave_1.json")
            val file2 = File(saveDirectory, "autosave_2.json")
            val file3 = File(saveDirectory, "autosave_3.json")
            val tmp  = File(saveDirectory, "autosave_1.json.tmp")

            if (file2.exists()) file2.copyTo(file3, overwrite = true)
            if (file1.exists()) file1.copyTo(file2, overwrite = true)

            tmp.writeText(encoded)
            if (tmp.exists()) {
                file1.delete()
                tmp.renameTo(file1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun loadLatestGame(): GameState? {
        val slots = listOf(
            File(saveDirectory, "autosave_1.json"),
            File(saveDirectory, "autosave_2.json"),
            File(saveDirectory, "autosave_3.json")
        )
        for (file in slots) {
            if (!file.exists()) continue
            try {
                return SavedGameSnapshotCodec.decode(file.readText())
            } catch (e: Exception) {
                val quarantine = File(saveDirectory, "quarantine/${file.name}_${System.currentTimeMillis()}.bak")
                file.renameTo(quarantine)
                println("Corrupted save quarantined: ${file.name}")
            }
        }
        return null
    }

    override fun hasSavedGame(): Boolean =
        File(saveDirectory, "autosave_1.json").exists()
}
