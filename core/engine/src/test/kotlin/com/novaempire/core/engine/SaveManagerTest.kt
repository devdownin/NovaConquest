package com.novaempire.core.engine

import com.novaempire.core.engine.save.LoadResult
import com.novaempire.core.engine.save.SaveManager
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SaveManagerTest {

    private lateinit var saveDir: File
    private lateinit var manager: SaveManager

    @Before
    fun setUp() {
        saveDir = createTempDir("nova_save_test")
        manager = SaveManager(saveDir)
    }

    @After
    fun tearDown() {
        saveDir.deleteRecursively()
    }

    private fun stateWithCredits(credits: Int) = GameState(
        playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION, credits = credits))
    )

    @Test
    fun hasSavedGameFalseWhenEmpty() {
        assertFalse(manager.hasSavedGame())
    }

    @Test
    fun roundTripSaveAndLoad() {
        val state = stateWithCredits(42)
        manager.saveGame(state)
        assertTrue(manager.hasSavedGame())
        val result = manager.loadLatestGame()
        assertNotNull(result)
        val loaded = result as LoadResult.Success
        assertEquals(42, loaded.state.playerStates[Faction.DOMINION]?.credits)
    }

    @Test
    fun saveRotatesSlots() {
        manager.saveGame(stateWithCredits(1))
        manager.saveGame(stateWithCredits(2))
        manager.saveGame(stateWithCredits(3))

        assertTrue(File(saveDir, "autosave_1.json").exists())
        assertTrue(File(saveDir, "autosave_2.json").exists())
        assertTrue(File(saveDir, "autosave_3.json").exists())

        // Most recent (slot 1) is credits=3
        val loaded = (manager.loadLatestGame() as LoadResult.Success).state
        assertEquals(3, loaded.playerStates[Faction.DOMINION]?.credits)
    }

    @Test
    fun corruptedSlot1FallsBackToSlot2() {
        manager.saveGame(stateWithCredits(10))
        manager.saveGame(stateWithCredits(20))

        // Corrupt slot 1
        File(saveDir, "autosave_1.json").writeText("not valid json {{{{")

        val result = manager.loadLatestGame()
        assertNotNull(result)
        val loaded = (result as LoadResult.Success).state
        assertEquals(10, loaded.playerStates[Faction.DOMINION]?.credits)

        // Slot 1 should be quarantined
        val quarantine = File(saveDir, "quarantine")
        assertTrue(quarantine.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun allCorruptedReturnsFailed() {
        manager.saveGame(stateWithCredits(1))
        listOf("autosave_1.json", "autosave_2.json", "autosave_3.json").forEach {
            File(saveDir, it).writeText("corrupt")
        }
        assertTrue(manager.loadLatestGame() is LoadResult.Failed)
    }

    @Test
    fun loadLatestGameReturnsNoSaveWhenEmpty() {
        assertTrue(manager.loadLatestGame() is LoadResult.NoSave)
    }

    @Test
    fun hasSavedGameStaysTrueAfterSlot1IsQuarantined() {
        // Regression: quarantining a corrupt slot 1 MOVES the file away. hasSavedGame() used to
        // check only that file, so the menu's "resume" entry went dead even though slot 2 still
        // loaded fine — the player lost access to a perfectly recoverable game.
        manager.saveGame(stateWithCredits(10))
        manager.saveGame(stateWithCredits(20))
        File(saveDir, "autosave_1.json").writeText("not valid json {{{{")

        val loaded = manager.loadLatestGame()
        assertTrue(loaded is LoadResult.Success)
        assertFalse("slot 1 was quarantined", File(saveDir, "autosave_1.json").exists())
        assertTrue("a recoverable save still exists in another slot", manager.hasSavedGame())
    }

    @Test
    fun saveGameReportsSuccess() {
        assertTrue(manager.saveGame(stateWithCredits(5)))
    }

    @Test
    fun saveGameReportsFailureWhenDirectoryIsUnusable() {
        // Simulate an unwritable location: a *file* where the save directory should be. The write
        // must report failure rather than silently pretending the turn was auto-saved.
        val blocked = File(saveDir, "blocked")
        blocked.writeText("I am a file, not a directory")
        val brokenManager = SaveManager(File(blocked, "saves"))
        assertFalse(brokenManager.saveGame(stateWithCredits(1)))
    }
}
