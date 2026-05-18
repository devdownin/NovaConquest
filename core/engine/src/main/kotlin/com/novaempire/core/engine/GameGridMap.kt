package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.GridMap
import com.novaempire.core.hex.HexCoord

class GameGridMap(private val state: GameState) : GridMap {
    override fun isPassable(coord: HexCoord): Boolean {
        val tile = state.map.tiles[coord] ?: return false
        val unit = state.units[coord]
        return tile.terrain.isPassable && unit == null
    }

    override fun getNeighbors(coord: HexCoord): List<HexCoord> {
        return HexCoord.directions.map { coord + it }.filter { state.map.tiles.containsKey(it) }
    }
}
