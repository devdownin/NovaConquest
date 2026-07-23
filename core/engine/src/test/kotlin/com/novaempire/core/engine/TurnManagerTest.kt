package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnManagerTest {

    private fun baseState(vararg factions: Faction = arrayOf(Faction.DOMINION, Faction.TRADERS)) =
        GameState(
            activeFaction = factions.first(),
            playerStates = factions.associateWith { PlayerState(it, credits = 10) }
        )

    @Test
    fun advanceTurnMovesToNextFaction() {
        val state = baseState(Faction.DOMINION, Faction.TRADERS)
        val next = TurnManager.advanceTurn(state)
        assertEquals(Faction.TRADERS, next.activeFaction)
    }

    @Test
    fun turnCounterIncrementsAfterFullRound() {
        val allActive = Faction.values().filter { it != Faction.ANCIENT_NPC }
        var state = baseState(Faction.DOMINION, Faction.TRADERS)
        // Advance through all factions back to DOMINION (index 0)
        repeat(allActive.size - 1) { state = TurnManager.advanceTurn(state) }
        assertEquals(1, state.turn)
        state = TurnManager.advanceTurn(state) // final advance wraps back to index 0
        assertEquals(Faction.DOMINION, state.activeFaction)
        assertEquals(2, state.turn)
    }

    @Test
    fun incomeAddedForFactionStartingTurn() {
        val state = baseState(Faction.DOMINION, Faction.TRADERS)
        val next = TurnManager.advanceTurn(state)
        // TRADERS gets base 10 income + TRADERS bonusCredits=5
        assertTrue(next.playerStates[Faction.TRADERS]!!.credits > 10)
    }

    @Test
    fun seededRngProducesDeterministicEvent() {
        // Find a seed that triggers an event (rng.nextDouble() < 0.20)
        // Seed 0 produces consistent output; run to verify event triggered
        val state = baseState(Faction.TRADERS, Faction.DOMINION).let {
            // wrap around to trigger new-turn logic (nextIndex == 0)
            TurnManager.advanceTurn(it) // TRADERS → DOMINION (index 0, turns to 2)
        }
        val withEvent = TurnManager.advanceTurn(
            state.copy(turn = 1, activeFaction = Faction.TRADERS),
            rng = Random(seed = 1L) // deterministic
        )
        // Same seed must always produce the same result
        val withEventAgain = TurnManager.advanceTurn(
            state.copy(turn = 1, activeFaction = Faction.TRADERS),
            rng = Random(seed = 1L)
        )
        assertEquals(withEvent.activeEvent, withEventAgain.activeEvent)
        assertEquals(withEvent.eventDurationRemaining, withEventAgain.eventDurationRemaining)
    }

    @Test
    fun suppressEventWithSeedThatNeverTriggers() {
        // Seed that always returns nextDouble() >= 0.20 for first call
        // We want to verify the seam works; check that NONE stays NONE across multiple seeds
        var noneCount = 0
        for (seed in 0L..20L) {
            val state = baseState(Faction.TRADERS, Faction.DOMINION).let {
                TurnManager.advanceTurn(it)
            }.copy(turn = 1, activeFaction = Faction.TRADERS, activeEvent = GalacticEvent.NONE)
            val next = TurnManager.advanceTurn(state, rng = Random(seed))
            if (next.activeEvent == GalacticEvent.NONE) noneCount++
        }
        // At 20% chance per seed, most seeds should not trigger; at least half should be NONE
        assertTrue("Expected most seeds to leave event NONE", noneCount > 10)
    }

    @Test
    fun researchTickDecrementsTurnsRemaining() {
        // Research with 2 turns left — after DOMINION ends its turn it should be 1
        val state = GameState(
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(
                    Faction.DOMINION,
                    researchInProgress = ResearchProgress("tech_hull_plating", 2)
                ),
                Faction.TRADERS to PlayerState(Faction.TRADERS)
            )
        )
        val after = TurnManager.advanceTurn(state)
        val prog = after.playerStates[Faction.DOMINION]!!.researchInProgress
        assertNotNull("Research should still be in progress", prog)
        assertEquals(1, prog!!.turnsRemaining)
    }

    @Test
    fun researchCompletesWhenTurnsReachZero() {
        // Research with 1 turn left — DOMINION ends its turn → tech unlocked, queue cleared
        val state = GameState(
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(
                    Faction.DOMINION,
                    researchInProgress = ResearchProgress("tech_hull_plating", 1)
                ),
                Faction.TRADERS to PlayerState(Faction.TRADERS)
            )
        )
        val after = TurnManager.advanceTurn(state)
        val dominion = after.playerStates[Faction.DOMINION]!!
        assertNull("Research queue should be cleared on completion", dominion.researchInProgress)
        assertTrue("Tech should be in techUnlocked", dominion.techUnlocked.contains("tech_hull_plating"))
    }

    @Test
    fun xylarProductionSpeedFinishesTwoTurnOrderInOne() {
        // XYLAR has +1 PRODUCTION_SPEED, so buildTick = 1 (base) + 1 = 2: a 2-turn order completes now.
        val planetCoord = HexCoord(0, 0, 0)
        val spawnCoord = HexCoord(1, -1, 0)
        val map = GameMap(tiles = mapOf(
            planetCoord to HexTile(planetCoord, TerrainType.PLANET, systemLevel = 1, owner = Faction.XYLAR),
            spawnCoord to HexTile(spawnCoord, TerrainType.EMPTY)
        ))
        val state = GameState(
            activeFaction = Faction.XYLAR,
            map = map,
            units = mapOf(planetCoord to GameUnit(type = UnitType.CRUISER, faction = Faction.XYLAR, position = planetCoord, currentHp = UnitType.CRUISER.maxHp)),
            playerStates = mapOf(
                Faction.XYLAR to PlayerState(Faction.XYLAR, buildQueue = listOf(BuildOrder(UnitType.SCOUT, planetCoord, turnsRemaining = 2)))
            )
        )
        val next = TurnManager.advanceTurn(state)
        assertTrue("Order should complete this turn", next.playerStates[Faction.XYLAR]!!.buildQueue.isEmpty())
        assertTrue("Scout should have spawned", next.units.values.any { it.type == UnitType.SCOUT && it.faction == Faction.XYLAR })
    }

    @Test
    fun nomadsPayLessUpkeepThanFactionsWithoutTheBonus() {
        // Same 2-cruiser fleet, no planets: NOMADS (-1 upkeep/unit) must end richer than a faction
        // with no upkeep bonus. Choose the 'active' faction so the tested one is next to play.
        fun creditsAfterStartingTurn(active: Faction, tested: Faction): Int {
            val u1 = HexCoord(0, 0, 0)
            val u2 = HexCoord(1, -1, 0)
            val state = GameState(
                activeFaction = active,
                playerStates = mapOf(tested to PlayerState(tested, credits = 100)),
                units = mapOf(
                    u1 to GameUnit(type = UnitType.CRUISER, faction = tested, position = u1, currentHp = 25),
                    u2 to GameUnit(type = UnitType.CRUISER, faction = tested, position = u2, currentHp = 25)
                )
            )
            return TurnManager.advanceTurn(state).playerStates[tested]!!.credits
        }
        val nomads = creditsAfterStartingTurn(Faction.SYNTH, Faction.NOMADS)   // next faction = NOMADS
        val synth = creditsAfterStartingTurn(Faction.TRADERS, Faction.SYNTH)   // next faction = SYNTH (no upkeep bonus)
        assertTrue("NOMADS keep more credits thanks to reduced upkeep", nomads > synth)
    }

    @Test
    fun buildOrderSpawnsUnitAfterOneTurn() {
        // Scout has turnsRemaining=1; after DOMINION ends its turn a Scout should spawn
        val planetCoord = HexCoord(0, 0, 0)
        val spawnCoord = HexCoord(1, -1, 0)
        val tile = HexTile(planetCoord, TerrainType.PLANET, systemLevel = 1, owner = Faction.DOMINION)
        val emptyTile = HexTile(spawnCoord, TerrainType.EMPTY)
        val map = GameMap(tiles = mapOf(planetCoord to tile, spawnCoord to emptyTile))
        val state = GameState(
            activeFaction = Faction.DOMINION,
            map = map,
            units = mapOf(planetCoord to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = planetCoord, currentHp = UnitType.CRUISER.maxHp)),
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(
                    Faction.DOMINION,
                    buildQueue = listOf(BuildOrder(UnitType.SCOUT, planetCoord, turnsRemaining = 1))
                ),
                Faction.TRADERS to PlayerState(Faction.TRADERS)
            )
        )
        val next = TurnManager.advanceTurn(state)
        assertTrue("Build queue should be empty after spawn", next.playerStates[Faction.DOMINION]!!.buildQueue.isEmpty())
        assertTrue("Scout should have spawned", next.units.values.any { it.type == UnitType.SCOUT && it.faction == Faction.DOMINION })
    }
}
