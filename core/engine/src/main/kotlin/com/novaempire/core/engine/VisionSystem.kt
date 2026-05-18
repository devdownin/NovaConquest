package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.abs
import kotlin.math.max

object VisionSystem {

    fun calculateVisibleHexes(state: GameState, faction: Faction): Set<HexCoord> {
        val visible = mutableSetOf<HexCoord>()
        val units = state.units.values.filter { it.faction == faction }

        // Also add planets owned by faction (simplified for now as always seeing capital)
        // For a full implementation, we'd iterate over owned planets as well.

        for (unit in units) {
            var range = when (unit.type) {
                UnitType.SCOUT -> 3
                UnitType.FIGHTER -> 2
                UnitType.CRUISER -> 2
                UnitType.BATTLESHIP -> 2
                UnitType.CARRIER -> 3
            }

            if (state.activeEvent == GalacticEvent.SOLAR_FLARE) {
                range = max(1, range / 2)
            }

            visible.addAll(getVisibleHexesFrom(state, unit.position, range))
        }

        return visible
    }

    private fun getVisibleHexesFrom(state: GameState, center: HexCoord, range: Int): Set<HexCoord> {
        val visible = mutableSetOf<HexCoord>()

        for (q in -range..range) {
            for (r in max(-range, -q - range)..minOf(range, -q + range)) {
                val s = -q - r
                val target = center + HexCoord(q, r, s)

                if (state.map.tiles.containsKey(target)) {
                    // Line of sight check (simple implementation)
                    if (hasLineOfSight(state, center, target)) {
                        visible.add(target)
                    }
                }
            }
        }
        return visible
    }

    // A simplified line algorithm for hex grids
    private fun hasLineOfSight(state: GameState, a: HexCoord, b: HexCoord): Boolean {
        val dist = a.distanceTo(b)
        if (dist <= 1) return true

        val points = mutableListOf<HexCoord>()
        for (i in 0..dist) {
            val t = if (dist == 0) 0.0 else i.toDouble() / dist
            val q = a.q + (b.q - a.q) * t
            val r = a.r + (b.r - a.r) * t
            val s = a.s + (b.s - a.s) * t
            points.add(hexRound(q, r, s))
        }

        // Check if any point on the line (excluding ends) blocks vision
        for (i in 1 until points.size - 1) {
            val tile = state.map.tiles[points[i]]
            if (tile != null && tile.terrain.blocksVision) {
                return false
            }
        }
        return true
    }

    private fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
        var q = Math.round(fracQ).toInt()
        var r = Math.round(fracR).toInt()
        var s = Math.round(fracS).toInt()

        val qDiff = Math.abs(q - fracQ)
        val rDiff = Math.abs(r - fracR)
        val sDiff = Math.abs(s - fracS)

        if (qDiff > rDiff && qDiff > sDiff) {
            q = -r - s
        } else if (rDiff > sDiff) {
            r = -q - s
        } else {
            s = -q - r
        }
        return HexCoord(q, r, s)
    }
}
