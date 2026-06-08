package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.GridMap
import com.novaempire.core.hex.HexCoord

class GameGridMap(private val state: GameState, private val faction: Faction? = null) : GridMap {

    private val hasWormholeNav = faction != null &&
        state.playerStates[faction]?.techUnlocked?.contains("tech_wormhole_nav") == true

    override fun isPassable(coord: HexCoord): Boolean {
        val tile = state.map.tiles[coord] ?: return false
        val unit = state.units[coord]
        return tile.terrain.isPassable && unit == null
    }

    override fun getNeighbors(coord: HexCoord): List<HexCoord> {
        val standard = HexCoord.directions.map { coord + it }.filter { state.map.tiles.containsKey(it) }
        if (hasWormholeNav && state.map.tiles[coord]?.terrain == TerrainType.WORMHOLE) {
            val otherWormholes = state.map.tiles.values
                .filter { it.terrain == TerrainType.WORMHOLE && it.coord != coord }
                .map { it.coord }
            return standard + otherWormholes
        }
        return standard
    }
}
