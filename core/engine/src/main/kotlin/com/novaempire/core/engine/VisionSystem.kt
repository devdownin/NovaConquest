package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.abs
import kotlin.math.max

object VisionSystem {

    fun calculateVisibleHexes(
        state: GameState,
        faction: Faction,
        bonusProvider: VisionBonusProvider = GameStateVisionBonus(state)
    ): Set<HexCoord> {
        val visible = mutableSetOf<HexCoord>()
        val units = state.units.values.filter { it.faction == faction }
        val visionBonus = bonusProvider.rangeBonus(faction)
        val mult = bonusProvider.rangeMult()

        for (unit in units) {
            val baseRange = when (unit.type) {
                UnitType.SCOUT -> 3
                UnitType.FIGHTER -> 2
                UnitType.CRUISER -> 2
                UnitType.BATTLESHIP -> 2
                UnitType.CARRIER -> 3
                UnitType.DREADNOUGHT -> 3
                UnitType.DEFENSE_PLATFORM -> 2
            }
            val scoutBonus = if (unit.type == UnitType.SCOUT) bonusProvider.scoutRangeBonus(faction) else 0
            val range = max(1, ((baseRange + visionBonus + scoutBonus) * mult).toInt())
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

    private fun hasLineOfSight(state: GameState, a: HexCoord, b: HexCoord): Boolean {
        val dist = a.distanceTo(b)
        if (dist <= 1) return true

        val points = mutableListOf<HexCoord>()
        for (i in 0..dist) {
            val t = if (dist == 0) 0.0 else i.toDouble() / dist
            val q = a.q + (b.q - a.q) * t
            val r = a.r + (b.r - a.r) * t
            val s = a.s + (b.s - a.s) * t
            points.add(HexCoord.round(q, r, s))
        }

        for (i in 1 until points.size - 1) {
            val tile = state.map.tiles[points[i]]
            if (tile != null && tile.terrain.blocksVision) {
                return false
            }
        }
        return true
    }
}
