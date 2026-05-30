package com.novaempire.core.engine

import com.novaempire.core.engine.save.SaveManager
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val loaded = manager.loadLatestGame()
        assertNotNull(loaded)
        assertEquals(42, loaded!!.playerStates[Faction.DOMINION]?.credits)
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
        val loaded = manager.loadLatestGame()
        assertEquals(3, loaded!!.playerStates[Faction.DOMINION]?.credits)
    }

    @Test
    fun corruptedSlot1FallsBackToSlot2() {
        manager.saveGame(stateWithCredits(10))
        manager.saveGame(stateWithCredits(20))

        // Corrupt slot 1
        File(saveDir, "autosave_1.json").writeText("not valid json {{{{")

        val loaded = manager.loadLatestGame()
        assertNotNull(loaded)
        assertEquals(10, loaded!!.playerStates[Faction.DOMINION]?.credits)

        // Slot 1 should be quarantined
        val quarantine = File(saveDir, "quarantine")
        assertTrue(quarantine.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun allCorruptedReturnsNull() {
        manager.saveGame(stateWithCredits(1))
        listOf("autosave_1.json", "autosave_2.json", "autosave_3.json").forEach {
            File(saveDir, it).writeText("corrupt")
        }
        assertNull(manager.loadLatestGame())
    }

    @Test
    fun loadLatestGameReturnsNullWhenNoSaves() {
        assertNull(manager.loadLatestGame())
    }
}
