package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameGridMapTest {

    private fun stateWith(vararg tiles: Pair<HexCoord, TerrainType>): GameState {
        val map = tiles.associate { (c, t) -> c to HexTile(c, t) }
        return GameState(map = GameMap(tiles = map))
    }

    @Test
    fun difficultTerrainCostsTwoToEnter() {
        val open = HexCoord(0, 0, 0)
        val plasma = HexCoord(1, -1, 0)
        val ion = HexCoord(1, 0, -1)
        val nebula = HexCoord(0, 1, -1)
        val grid = GameGridMap(
            stateWith(
                open to TerrainType.EMPTY,
                plasma to TerrainType.PLASMA_CLOUD,
                ion to TerrainType.ION_STORM,
                nebula to TerrainType.NEBULA
            )
        )
        assertEquals(1, grid.enterCost(open))
        assertEquals(2, grid.enterCost(plasma))
        assertEquals(2, grid.enterCost(ion))
        // Nebula blocks vision but is freely traversable — normal cost.
        assertEquals(1, grid.enterCost(nebula))
    }

    private fun stateWithWormholes(unlocked: Boolean, vararg tiles: Pair<HexCoord, TerrainType>): GameState {
        val map = tiles.associate { (c, t) -> c to HexTile(c, t) }
        val player = PlayerState(
            faction = Faction.DOMINION,
            techUnlocked = if (unlocked) setOf("tech_wormhole_nav") else emptySet()
        )
        return GameState(
            map = GameMap(tiles = map),
            playerStates = mapOf(Faction.DOMINION to player),
            activeFaction = Faction.DOMINION
        )
    }

    @Test
    fun wormholeExitsAreNeighboursOnlyWithTheTech() {
        val a = HexCoord(0, 0, 0)
        val b = HexCoord(4, -4, 0)
        val open = HexCoord(1, 0, -1)
        val tiles = arrayOf(
            a to TerrainType.WORMHOLE, b to TerrainType.WORMHOLE, open to TerrainType.EMPTY
        )

        val withTech = GameGridMap(stateWithWormholes(true, *tiles), Faction.DOMINION)
        assertTrue(withTech.getNeighbors(a).contains(b))
        // The hex itself is never its own neighbour, and plain hexes gain nothing.
        assertFalse(withTech.getNeighbors(a).contains(a))
        assertFalse(withTech.getNeighbors(open).contains(b))

        val withoutTech = GameGridMap(stateWithWormholes(false, *tiles), Faction.DOMINION)
        assertFalse(withoutTech.getNeighbors(a).contains(b))
    }

    @Test
    fun repeatedNeighbourQueriesReturnTheSameWormholeExits() {
        // The exit list is now resolved once per grid instead of by re-scanning every tile on
        // each expansion; this pins the cached result to the uncached behaviour.
        val a = HexCoord(0, 0, 0)
        val b = HexCoord(4, -4, 0)
        val c = HexCoord(-3, 3, 0)
        val grid = GameGridMap(
            stateWithWormholes(
                true,
                a to TerrainType.WORMHOLE, b to TerrainType.WORMHOLE, c to TerrainType.WORMHOLE
            ),
            Faction.DOMINION
        )
        assertEquals(grid.getNeighbors(a), grid.getNeighbors(a))
        assertEquals(setOf(b, c), grid.getNeighbors(a).filter { it != a }.toSet())
        assertEquals(setOf(a, c), grid.getNeighbors(b).filter { it != b }.toSet())
    }

    @Test
    fun neighboursAreLimitedToTilesThatExist() {
        val center = HexCoord(0, 0, 0)
        val east = HexCoord(1, 0, -1)
        val grid = GameGridMap(stateWith(center to TerrainType.EMPTY, east to TerrainType.EMPTY))
        assertEquals(listOf(east), grid.getNeighbors(center))
    }
}
