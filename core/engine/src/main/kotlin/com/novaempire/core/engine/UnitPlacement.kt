package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

/**
 * Where a newly created ship can stand.
 *
 * The build queue and the glory perks both need this, and they must agree: a rule that says "the
 * planet, else a free neighbour" in one place and something subtly different in the other is how a
 * perk ends up spawning a ship inside an asteroid field the shipyard would have refused.
 */
object UnitPlacement {

    /**
     * First free, passable hex at [around] or beside it, or null when everything is occupied.
     *
     * The planet's own hex is tried first so a ship appears where it was built whenever possible.
     */
    fun freeHexNear(state: GameState, around: HexCoord): HexCoord? {
        val gridMap = GameGridMap(state)
        val candidates = listOf(around) + gridMap.getNeighbors(around)
        return candidates.firstOrNull { state.units[it] == null && gridMap.isPassable(it) }
    }
}
