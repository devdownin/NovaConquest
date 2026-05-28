package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.GameState
import java.io.File

class SaveManager(private val saveDirectory: File) {

    init {
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs()
        }
        val quarantineDir = File(saveDirectory, "quarantine")
        if (!quarantineDir.exists()) {
            quarantineDir.mkdirs()
        }
    }

    fun saveGame(state: GameState) {
        try {
            val encoded = SavedGameSnapshotCodec.encode(state)

            val file1 = File(saveDirectory, "autosave_1.json")
            val file2 = File(saveDirectory, "autosave_2.json")
            val file3 = File(saveDirectory, "autosave_3.json")
            val tempFile = File(saveDirectory, "autosave_1.json.tmp")

            // 1. Shift existing saves
            if (file2.exists()) file2.copyTo(file3, overwrite = true)
            if (file1.exists()) file1.copyTo(file2, overwrite = true)

            // 2. Atomic write: Write to temp file then rename
            tempFile.writeText(encoded)
            if (tempFile.exists()) {
                if (file1.exists()) file1.delete()
                tempFile.renameTo(file1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadLatestGame(): GameState? {
        val filesToTry = listOf(
            File(saveDirectory, "autosave_1.json"),
            File(saveDirectory, "autosave_2.json"),
            File(saveDirectory, "autosave_3.json")
        )

        for (file in filesToTry) {
            if (file.exists()) {
                try {
                    val encoded = file.readText()
                    return SavedGameSnapshotCodec.decode(encoded)
                } catch (e: Exception) {
                    // Corruption detected, move to quarantine
                    val quarantineFile = File(saveDirectory, "quarantine/${file.name}_${System.currentTimeMillis()}.bak")
                    file.renameTo(quarantineFile)
                    println("Corrupted save detected and quarantined: ${file.name}")
                }
            }
        }
        return null
    }
}
