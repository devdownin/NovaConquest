package com.novaempire.core.engine

import com.novaempire.core.domain.state.CampaignProgress
import com.novaempire.core.engine.save.CampaignProgressStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CampaignProgressStoreTest {

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var store: CampaignProgressStore

    @Before
    fun setUp() {
        dir = createTempDir("nova_progress_test")
        file = File(dir, "campaign_progress.json")
        store = CampaignProgressStore(file)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun noFileYetReadsAsNoProgress() {
        assertFalse(file.exists())
        assertEquals(CampaignProgress(), store.read())
    }

    @Test
    fun writtenProgressComesBackIntact() {
        val progress = CampaignProgress(completedMissions = setOf("mission_1", "mission_3"), gloryPoints = 5)
        assertTrue(store.write(progress))
        assertEquals(progress, CampaignProgressStore(file).read())
    }

    @Test
    fun rewritingReplacesRatherThanAppends() {
        store.write(CampaignProgress(setOf("mission_1"), 2))
        store.write(CampaignProgress(setOf("mission_1", "mission_2"), 1))
        assertEquals(CampaignProgress(setOf("mission_1", "mission_2"), 1), store.read())
    }

    @Test
    fun corruptRecordReadsAsEmptyInsteadOfThrowing() {
        // Unlike a save, a corrupt progress file offers nothing to recover and no choice to make,
        // so it must degrade to "no progress" rather than take the app down at startup.
        file.writeText("{ this is not json")
        assertEquals(CampaignProgress(), store.read())
        // …and must still be writable afterwards, so a player is not stuck at zero forever.
        assertTrue(store.write(CampaignProgress(setOf("mission_1"), 2)))
        assertEquals(CampaignProgress(setOf("mission_1"), 2), store.read())
    }

    @Test
    fun unknownFieldsFromANewerVersionDoNotBreakTheRead() {
        file.writeText("""{"completedMissions":["mission_1"],"gloryPoints":3,"medals":7}""")
        assertEquals(CampaignProgress(setOf("mission_1"), 3), store.read())
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        file.writeText("""{"gloryPoints":4}""")
        assertEquals(CampaignProgress(emptySet(), 4), store.read())
    }

    @Test
    fun writeCreatesTheDirectoryAndLeavesNoTempBehind() {
        val nested = File(dir, "deeper/still/progress.json")
        assertTrue(CampaignProgressStore(nested).write(CampaignProgress(setOf("mission_2"), 1)))
        assertTrue(nested.exists())
        assertEquals(
            "the temp file must be moved into place, not left next to the record",
            listOf(nested.name),
            nested.parentFile.list()!!.toList()
        )
    }
}
