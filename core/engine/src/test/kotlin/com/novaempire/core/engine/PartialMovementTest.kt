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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A move used to consume a fleet's whole allowance whatever the distance: a SCOUT (movement 3)
 * that stepped one hex lost the other two. These pin the spend-what-you-use behaviour.
 */
class PartialMovementTest {

    private val deps = GameEngineDependencies()

    private fun state(unit: GameUnit, terrain: Map<HexCoord, TerrainType> = emptyMap()): GameState {
        // Radius 8, comfortably wider than any unit's allowance (SCOUT moves 5). A board too
        // small to hold a full-budget trip makes "the move was refused" tests pass for the wrong
        // reason — the destination simply isn't on the map.
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -8..8) {
            for (r in maxOf(-8, -q - 8)..minOf(8, -q + 8)) {
                val coord = HexCoord(q, r, -q - r)
                tiles[coord] = HexTile(coord, terrain[coord] ?: TerrainType.EMPTY)
            }
        }
        return GameState(
            map = GameMap(tiles = tiles, radius = 8),
            units = mapOf(unit.position to unit),
            activeFaction = unit.faction,
            humanFaction = unit.faction,
            playerStates = mapOf(unit.faction to PlayerState(faction = unit.faction))
        )
    }

    private fun scoutAt(coord: HexCoord, used: Int = 0) = GameUnit(
        type = UnitType.SCOUT, faction = Faction.KAELEN, position = coord,
        currentHp = UnitType.SCOUT.maxHp, movementUsed = used
    )

    private fun move(s: GameState, from: HexCoord, to: HexCoord) =
        handleMoveUnit(s, GameIntent.MoveUnit(from, to), deps)

    @Test
    fun aShortStepOnlySpendsWhatItCosts() {
        val origin = HexCoord(0, 0, 0)
        val one = HexCoord(1, 0, -1)
        val scout = scoutAt(origin)
        val budget = MovementCalculator.effectiveMovement(state(scout), scout)
        assertTrue("premise: the scout has more than one point", budget > 1)

        val result = move(state(scout), origin, one)
        val moved = result.newState.units[one]

        assertNotNull(moved)
        assertEquals(1, moved!!.movementUsed)
        assertFalse("a fleet with points left has not 'moved' in the blocking sense", moved.hasMoved)
    }

    @Test
    fun aFleetCanMoveTwiceInOneTurnWithinItsBudget() {
        val origin = HexCoord(0, 0, 0)
        val one = HexCoord(1, 0, -1)
        val two = HexCoord(2, 0, -2)

        val first = move(state(scoutAt(origin)), origin, one)
        assertEquals(null, first.error)

        val second = handleMoveUnit(first.newState, GameIntent.MoveUnit(one, two), deps)
        assertEquals(null, second.error)
        assertEquals(two, second.newState.units[two]?.position)
        assertEquals(2, second.newState.units[two]?.movementUsed)
    }

    @Test
    fun exhaustingTheBudgetEndsTheFleetsMovement() {
        val origin = HexCoord(0, 0, 0)
        val scout = scoutAt(origin)
        val budget = MovementCalculator.effectiveMovement(state(scout), scout)
        val far = HexCoord(budget, 0, -budget)

        val result = move(state(scout), origin, far)
        val moved = result.newState.units[far]

        assertNotNull(moved)
        assertEquals(budget, moved!!.movementUsed)
        assertTrue("spending the last point must block further moves", moved.hasMoved)
    }

    @Test
    fun theRemainingBudgetIsWhatBoundsTheNextMove() {
        val origin = HexCoord(0, 0, 0)
        val scout = scoutAt(origin)
        val budget = MovementCalculator.effectiveMovement(state(scout), scout)

        // One point already spent: the fleet may go budget-1 hexes, no further.
        val partly = scoutAt(origin, used = 1)
        assertEquals(budget - 1, MovementCalculator.remainingMovement(state(partly), partly))

        val tooFar = HexCoord(budget, 0, -budget)
        assertTrue("premise: the destination is on the board", state(partly).map.tiles.containsKey(tooFar))
        assertNotNull("a hex needing the full budget is now out of reach", move(state(partly), origin, tooFar).error)
    }

    @Test
    fun difficultTerrainCostsTwoPointsNotOne() {
        val origin = HexCoord(0, 0, 0)
        val storm = HexCoord(1, 0, -1)
        val result = move(
            state(scoutAt(origin), terrain = mapOf(storm to TerrainType.ION_STORM)),
            origin, storm
        )
        assertEquals(2, result.newState.units[storm]?.movementUsed)
    }

    @Test
    fun anImmobileStructureStillCannotMove() {
        val origin = HexCoord(0, 0, 0)
        val platform = GameUnit(
            type = UnitType.DEFENSE_PLATFORM, faction = Faction.KAELEN,
            position = origin, currentHp = UnitType.DEFENSE_PLATFORM.maxHp
        )
        assertEquals(0, MovementCalculator.remainingMovement(state(platform), platform))
        assertNotNull(move(state(platform), origin, HexCoord(1, 0, -1)).error)
    }
}
