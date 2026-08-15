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

    /** Plasma and ion fields are turbulent: entering one costs 2 movement points instead of 1. */
    override fun enterCost(coord: HexCoord): Int = when (state.map.tiles[coord]?.terrain) {
        TerrainType.PLASMA_CLOUD, TerrainType.ION_STORM -> 2
        else -> 1
    }

    /**
     * Wormhole exits, resolved once per grid instead of once per expanded node.
     * [getNeighbors] is called for every hex A* or the reachability flood-fill touches, so
     * re-scanning the whole tile map in there made pathfinding quadratic in map size for any
     * faction holding `tech_wormhole_nav` (~469 tiles on GIGANTIC → ~220k tile visits per path).
     */
    private val wormholeCoords: List<HexCoord> by lazy(LazyThreadSafetyMode.NONE) {
        if (!hasWormholeNav) emptyList()
        else state.map.tiles.values.filter { it.terrain == TerrainType.WORMHOLE }.map { it.coord }
    }

    override fun getNeighbors(coord: HexCoord): List<HexCoord> {
        val standard = HexCoord.directions.mapNotNull { dir ->
            (coord + dir).takeIf { state.map.tiles.containsKey(it) }
        }
        if (hasWormholeNav && state.map.tiles[coord]?.terrain == TerrainType.WORMHOLE) {
            return standard + wormholeCoords.filter { it != coord }
        }
        return standard
    }
}
