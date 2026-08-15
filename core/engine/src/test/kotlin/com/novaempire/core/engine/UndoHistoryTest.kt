package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.MapSize
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Undo saves the mis-tap that would otherwise cost a fleet its whole turn — but it stops at the
 * fog of war, so it cannot be turned into free reconnaissance.
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
    fun closingForExplorationIsDistinguishableFromAnEmptyHistory() {
        val history = UndoHistory()
        assertFalse("un historique neuf n'a pas été fermé par l'exploration", history.closedByExploration)

        history.record(a)
        history.closeForExploration()
        assertFalse(history.canUndo)
        assertTrue(history.closedByExploration)

        // Une action ordinaire rouvre l'historique et efface la raison.
        history.record(b)
        assertTrue(history.canUndo)
        assertFalse(history.closedByExploration)
    }

    @Test
    fun anOrdinaryClearIsNotAnExplorationClose() {
        // Fin de tour, chargement : rien à expliquer au joueur, le bouton est simplement vide.
        val history = UndoHistory()
        history.record(a)
        history.closeForExploration()
        history.clear()
        assertFalse(history.closedByExploration)
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

    // ── the fog-of-war rule ─────────────────────────────────────────────────

    private fun stateExploring(vararg hexes: HexCoord) = GameState(
        activeFaction = Faction.DOMINION,
        playerStates = mapOf(
            Faction.DOMINION to PlayerState(faction = Faction.DOMINION, exploredHexes = hexes.toSet())
        )
    )

    @Test
    fun anActionThatUncoversNewGroundIsNotUndoable() {
        val before = stateExploring(HexCoord(0, 0, 0))
        val after = stateExploring(HexCoord(0, 0, 0), HexCoord(1, 0, -1))
        assertTrue(UndoHistory.revealsNewTerritory(before, after))
    }

    @Test
    fun anActionThatUncoversNothingStaysUndoable() {
        val seen = arrayOf(HexCoord(0, 0, 0), HexCoord(1, 0, -1))
        assertFalse(UndoHistory.revealsNewTerritory(stateExploring(*seen), stateExploring(*seen)))
    }

    @Test
    fun onlyTheActingFactionsFogCounts() {
        // The AI uncovering ground during its own turn must not close the player's history.
        val before = GameState(
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, exploredHexes = setOf(HexCoord(0, 0, 0))),
                Faction.XYLAR to PlayerState(Faction.XYLAR, exploredHexes = emptySet())
            )
        )
        val after = before.copy(
            playerStates = before.playerStates + (
                Faction.XYLAR to PlayerState(Faction.XYLAR, exploredHexes = setOf(HexCoord(5, 0, -5)))
                )
        )
        assertFalse(UndoHistory.revealsNewTerritory(before, after))
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
