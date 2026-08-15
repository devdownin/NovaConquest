package com.novaempire.core.engine.save

import com.novaempire.core.domain.state.CampaignProgress
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface CampaignProgressRepository {
    /** Never throws: an unreadable record reads as "no progress yet". */
    fun read(): CampaignProgress
    /** Returns false if the record could not be written (disk full, permissions…). */
    fun write(progress: CampaignProgress): Boolean
}

/**
 * Durable store for campaign progress, kept **apart from the turn autosave** on purpose.
 *
 * The autosave deliberately skips terminal states, so the single instant that produces progress —
 * winning a mission — was also the single instant nothing reached the disk. Finishing a mission and
 * quitting lost the record. Progress is small, changes rarely, and must outlive the game it came
 * from, so it gets its own file rather than a slot in the ring buffer.
 *
 * A corrupt record is **not** quarantined the way a save is: there is nothing to recover and no
 * choice to offer, so it is overwritten by the next write and read as empty until then. The cost of
 * being wrong is a player's medal count, not a game they were in the middle of.
 *
 * Written to a temp file and moved into place, so a crash mid-write cannot leave a half-written
 * record where a valid one used to be.
 */
class CampaignProgressStore(private val file: File) : CampaignProgressRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun read(): CampaignProgress {
        if (!file.exists()) return CampaignProgress()
        return try {
            json.decodeFromString(CampaignProgress.serializer(), file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            CampaignProgress()
        }
    }

    override fun write(progress: CampaignProgress): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(CampaignProgress.serializer(), progress))
        moveIntoPlace(tmp, file)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun moveIntoPlace(from: File, to: File): Boolean = try {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}
