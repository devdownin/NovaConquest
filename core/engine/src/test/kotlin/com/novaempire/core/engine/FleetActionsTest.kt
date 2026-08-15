package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetActionsTest {

    private val origin = HexCoord(0, 0, 0)

    private fun stateWith(vararg units: GameUnit): GameState {
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -3..3) {
            for (r in maxOf(-3, -q - 3)..minOf(3, -q + 3)) {
                val c = HexCoord(q, r, -q - r)
                tiles[c] = HexTile(c, TerrainType.EMPTY)
            }
        }
        return GameState(
            map = GameMap(tiles = tiles, radius = 3),
            units = units.associateBy { it.position },
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION))
        )
    }

    private fun scout(
        at: HexCoord = origin,
        used: Int = 0,
        hasMoved: Boolean = false,
        hasAttacked: Boolean = false,
        faction: Faction = Faction.DOMINION
    ) = GameUnit(
        type = UnitType.SCOUT, faction = faction, position = at,
        currentHp = UnitType.SCOUT.maxHp,
        hasMoved = hasMoved, hasAttacked = hasAttacked, movementUsed = used
    )

    @Test
    fun aFreshFleetHasActionsLeft() {
        val unit = scout()
        assertTrue(FleetActions.hasActionsLeft(stateWith(unit), unit))
    }

    @Test
    fun aPartlyMovedFleetStillHasActionsLeft() {
        // Le cas que le mouvement partiel a créé : elle a bougé, il lui reste des points.
        val unit = scout(used = 1)
        assertTrue(FleetActions.canMove(stateWith(unit), unit))
        assertTrue(FleetActions.hasActionsLeft(stateWith(unit), unit))
    }

    @Test
    fun aFleetOutOfMovementCanStillFire() {
        // L'ancien prédicat la disait finie : c'était faux, elle peut encore engager.
        val unit = scout(hasMoved = true, used = UnitType.SCOUT.movement)
        assertFalse(FleetActions.canMove(stateWith(unit), unit))
        assertTrue(FleetActions.hasActionsLeft(stateWith(unit), unit))
    }

    @Test
    fun aFleetThatFiredKeepsNoMovementEvenWithPointsOnTheCounter() {
        // Le combat pose hasMoved sans toucher movementUsed : le compteur ment, hasMoved fait foi.
        val unit = scout(hasMoved = true, hasAttacked = true, used = 0)
        assertFalse(FleetActions.canMove(stateWith(unit), unit))
        assertFalse(FleetActions.hasActionsLeft(stateWith(unit), unit))
    }

    @Test
    fun anImmobileStructureThatHasNotFiredStillCounts() {
        val platform = GameUnit(
            type = UnitType.DEFENSE_PLATFORM, faction = Faction.DOMINION,
            position = origin, currentHp = UnitType.DEFENSE_PLATFORM.maxHp
        )
        assertFalse(FleetActions.canMove(stateWith(platform), platform))
        assertTrue(FleetActions.hasActionsLeft(stateWith(platform), platform))
    }

    @Test
    fun idleFleetsCoverOnlyTheAskedFactionAndKeepAStableOrder() {
        val mine = scout(at = origin)
        val theirs = scout(at = HexCoord(1, 0, -1), faction = Faction.XYLAR)
        val spent = scout(at = HexCoord(2, 0, -2), hasMoved = true, hasAttacked = true)
        val state = stateWith(mine, theirs, spent)

        val idle = FleetActions.idleFleets(state, Faction.DOMINION)
        assertEquals(listOf(mine.id), idle.map { it.id })
        assertEquals(idle.map { it.id }, FleetActions.idleFleets(state, Faction.DOMINION).map { it.id })
    }
}
