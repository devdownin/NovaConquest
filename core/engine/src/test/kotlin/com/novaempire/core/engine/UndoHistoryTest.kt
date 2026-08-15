package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.MapSize
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Undo is the comfort feature a turn-based game needs: without it a mis-tap costs a fleet its
 * whole turn, and the map commits a move the instant you touch a hex.
 */
class UndoHistoryTest {

    private val a = GameState(turn = 1)
    private val b = GameState(turn = 2)
    private val c = GameState(turn = 3)

    @Test
    fun aFreshHistoryHasNothingToTakeBack() {
        val history = UndoHistory()
        assertFalse(history.canUndo)
        assertNull(history.rollback())
    }

    @Test
    fun statesComeBackInReverseOrder() {
        val history = UndoHistory()
        history.record(a)
        history.record(b)
        history.record(c)

        assertSame(c, history.rollback())
        assertSame(b, history.rollback())
        assertSame(a, history.rollback())
        assertFalse(history.canUndo)
    }

    @Test
    fun theOldestStatesAreDroppedBeyondTheDepth() {
        val history = UndoHistory(depth = 2)
        history.record(a)
        history.record(b)
        history.record(c)

        assertEquals(2, history.size)
        assertSame(c, history.rollback())
        assertSame(b, history.rollback())
        assertNull("the oldest was evicted, not kept", history.rollback())
    }

    @Test
    fun clearingDropsEverything() {
        val history = UndoHistory()
        history.record(a)
        history.clear()
        assertFalse(history.canUndo)
    }

    @Test
    fun playerActionsAreUndoable() {
        val coord = HexCoord(0, 0, 0)
        val undoable = listOf(
            GameIntent.MoveUnit(coord, coord),
            GameIntent.AttackUnit(coord, coord),
            GameIntent.SiegePlanet(coord, coord),
            GameIntent.CapturePlanet(coord, coord),
            GameIntent.BuildUnit(UnitType.SCOUT, coord),
            GameIntent.CancelBuild(coord),
            GameIntent.ResearchTech("tech_x"),
            GameIntent.CancelResearch,
            GameIntent.UpgradeSystem(coord),
            GameIntent.RecruitHero("hero_nix"),
            GameIntent.UseHeroAbility("hero_nix"),
            GameIntent.LoadUnit(coord, coord),
            GameIntent.DeployUnit(coord, coord, 0)
        )
        for (intent in undoable) {
            assertTrue("$intent should be undoable", UndoHistory.isUndoable(intent))
        }
    }

    @Test
    fun theTurnBoundaryAndGameLifecycleAreNot() {
        val notUndoable = listOf(
            GameIntent.EndTurn,
            GameIntent.Undo,
            GameIntent.SelectFaction(Faction.DOMINION),
            GameIntent.StartNewGame,
            GameIntent.StartNewGameWithSize(MapSize.SMALL, MapArchetype.STANDARD),
            GameIntent.StartCampaign("m1"),
            GameIntent.LoadGame(GameState())
        )
        for (intent in notUndoable) {
            assertFalse("$intent must not be undoable", UndoHistory.isUndoable(intent))
        }
    }
}
