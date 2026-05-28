package com.novaempire.core.hex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexPathfinderTest {

    /** A radius-N hex board where the given coords are impassable obstacles. */
    private class TestGrid(private val radius: Int, private val blocked: Set<HexCoord>) : GridMap {
        override fun isPassable(coord: HexCoord): Boolean {
            if (coord in blocked) return false
            return kotlin.math.abs(coord.q) <= radius &&
                kotlin.math.abs(coord.r) <= radius &&
                kotlin.math.abs(coord.s) <= radius
        }

        override fun getNeighbors(coord: HexCoord): List<HexCoord> =
            HexCoord.directions.map { coord + it }
                .filter { kotlin.math.abs(it.q) <= radius && kotlin.math.abs(it.r) <= radius && kotlin.math.abs(it.s) <= radius }
    }

    @Test
    fun findsShortestPathOnEmptyGrid() {
        val grid = TestGrid(radius = 4, blocked = emptySet())
        val start = HexCoord(0, 0, 0)
        val goal = HexCoord(3, -3, 0)

        val path = HexPathfinder.findPath(start, goal, grid)

        assertNotNull(path)
        assertEquals(start.distanceTo(goal), path!!.size)
        assertEquals(goal, path.last())
    }

    @Test
    fun routesAroundObstacles() {
        // Wall along q = 0 blocking the direct line between the two sides.
        val wall = setOf(
            HexCoord(0, -1, 1), HexCoord(0, 0, 0), HexCoord(0, 1, -1)
        )
        val grid = TestGrid(radius = 3, blocked = wall)
        val start = HexCoord(-2, 0, 2)
        val goal = HexCoord(2, 0, -2)

        val path = HexPathfinder.findPath(start, goal, grid)

        assertNotNull("expected a detour path around the wall", path)
        assertTrue("path must not cross the wall", path!!.none { it in wall })
        assertEquals(goal, path.last())
    }

    @Test
    fun returnsNullWhenGoalUnreachableWithinMaxCost() {
        val grid = TestGrid(radius = 5, blocked = emptySet())
        val start = HexCoord(0, 0, 0)
        val goal = HexCoord(5, -5, 0)

        val path = HexPathfinder.findPath(start, goal, grid, maxCost = 2)

        assertNull(path)
    }

    @Test
    fun returnsNullWhenStartIsEnclosed() {
        val start = HexCoord(0, 0, 0)
        val grid = TestGrid(radius = 3, blocked = HexCoord.directions.map { start + it }.toSet())
        val goal = HexCoord(2, -2, 0)

        val path = HexPathfinder.findPath(start, goal, grid)

        assertNull(path)
    }
}
