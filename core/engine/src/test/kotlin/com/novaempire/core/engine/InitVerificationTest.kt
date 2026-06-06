package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.MapSize
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.engine.save.LoadResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InitVerificationTest {

    @Test
    fun allFactionsHavePlayerState() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            assertNotNull("Missing PlayerState for $faction", state.playerStates[faction])
            assertEquals(faction, state.playerStates[faction]!!.faction)
        }
    }

    @Test
    fun spawnUnitsPlacedForEachFaction() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        val unitFactions = state.units.values.map { it.faction }.toSet()
        for (faction in activeFactions) {
            assertTrue("No unit for $faction at start", unitFactions.contains(faction))
        }
        // Units sit on valid map tiles
        for ((coord, unit) in state.units) {
            assertNotNull("Unit ${unit.faction} placed off-map at $coord", state.map.tiles[coord])
        }
    }

    @Test
    fun visionComputedForEachFaction() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            val ps: PlayerState = state.playerStates[faction]
                ?: fail("No PlayerState for $faction") as PlayerState
            assertTrue("visibleHexes empty for $faction", ps.visibleHexes.isNotEmpty())
            assertTrue("exploredHexes empty for $faction", ps.exploredHexes.isNotEmpty())
            assertTrue(ps.exploredHexes.containsAll(ps.visibleHexes))
        }
    }

    @Test
    fun startNewGameIntentNoCrash() = runBlocking {
        val engine = GameEngine()
        val original = engine.state.value
        engine.processIntent(GameIntent.StartNewGame)
        delay(200)
        val newState = engine.state.value
        assertNotNull(newState)
        assertEquals(1, newState.turn)
        assertEquals(Faction.DOMINION, newState.activeFaction)
        // Should have units again
        assertTrue(newState.units.isNotEmpty())
    }

    @Test
    fun startNewGameWithSizeSmallAndRiftNoCrash() = runBlocking {
        val engine = GameEngine()
        engine.processIntent(GameIntent.StartNewGameWithSize(MapSize.SMALL, MapArchetype.ZODIAC))
        delay(200)
        val state = engine.state.value
        assertNotNull(state)
        assertTrue(state.units.isNotEmpty())
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            assertNotNull("Missing PlayerState for $faction on SMALL/RIFT", state.playerStates[faction])
        }
    }

    @Test
    fun startingCreditsNonZero() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            val credits = state.playerStates[faction]!!.credits
            assertTrue("$faction starts with no credits", credits > 0)
        }
    }

    @Test
    fun dominionStartsWithCruiser() {
        val engine = GameEngine()
        val state = engine.state.value
        val dominionUnit = state.units.values.find { it.faction == Faction.DOMINION }
        assertNotNull("No DOMINION unit", dominionUnit)
        assertEquals(com.novaempire.core.domain.models.UnitType.CRUISER, dominionUnit!!.type)
    }

    @Test
    fun nonDominionFactionRetainsControlAfterEndTurn() = runBlocking {
        // Regression: if the human player picks TRADERS, control must return to TRADERS
        // after the AI loop, not to DOMINION.
        val engine = GameEngine()
        engine.processIntent(GameIntent.StartNewGameWithSize(MapSize.MEDIUM, MapArchetype.STANDARD))
        delay(200)
        engine.processIntent(GameIntent.SelectFaction(Faction.TRADERS))
        delay(50)
        assertEquals(Faction.TRADERS, engine.state.value.humanFaction)
        engine.processIntent(GameIntent.EndTurn)
        delay(3000) // AI loop: up to 5 factions × ~500ms
        val stateAfter = engine.state.value
        assertEquals(
            "After EndTurn, activeFaction must be the human faction (TRADERS), not DOMINION",
            Faction.TRADERS, stateAfter.activeFaction
        )
    }

    @Test
    fun allFactionsHaveUnitOnStart() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            val unit = state.units.values.find { it.faction == faction }
            assertNotNull("No starting unit for $faction", unit)
            assertFalse("$faction unit already moved at start", unit!!.hasMoved)
            assertFalse("$faction unit already attacked at start", unit.hasAttacked)
        }
    }

    @Test
    fun allFactionsHavePositiveCredits() {
        val engine = GameEngine()
        val state = engine.state.value
        val activeFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        for (faction in activeFactions) {
            val credits = state.playerStates[faction]!!.credits
            assertTrue("$faction starts with non-positive credits: $credits", credits > 0)
        }
    }

    @Test
    fun saveManagerInitCreatesDir() {
        val tmpDir = createTempDir("nova_init_test")
        val saveDir = java.io.File(tmpDir, "saves")
        assertFalse(saveDir.exists())
        val manager = com.novaempire.core.engine.save.SaveManager(saveDir)
        assertTrue("SaveManager did not create save dir", saveDir.exists())
        assertTrue("SaveManager did not create quarantine dir", java.io.File(saveDir, "quarantine").exists())
        assertFalse(manager.hasSavedGame())
        tmpDir.deleteRecursively()
    }

    @Test
    fun saveAndLoadFullEngineState() {
        // Regression: full GameEngine state (map + units + playerStates) must survive
        // a save/load round-trip without error — covers the encode/decode path that runs
        // off-main-thread in GameViewModel.dispatch.
        val engine = GameEngine()
        val before = engine.state.value

        val tmpDir = createTempDir("nova_fullstate_test")
        val manager = com.novaempire.core.engine.save.SaveManager(java.io.File(tmpDir, "saves"))
        manager.saveGame(before)

        val result = manager.loadLatestGame()
        assertTrue("Full engine state failed to load after save", result is LoadResult.Success)
        val loaded = (result as LoadResult.Success).state
        assertEquals(before.turn, loaded.turn)
        assertEquals(before.activeFaction, loaded.activeFaction)
        assertEquals(before.units.size, loaded.units.size)
        assertEquals(before.playerStates.keys, loaded.playerStates.keys)
        tmpDir.deleteRecursively()
    }
}
