package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
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
}
