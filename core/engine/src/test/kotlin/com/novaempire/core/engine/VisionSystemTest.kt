package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VisionSystemTest {

    private fun emptyMap(radius: Int = 3) =
        com.novaempire.core.domain.models.GameMap(
            tiles = buildMap {
                for (q in -radius..radius) {
                    val r1 = maxOf(-radius, -q - radius)
                    val r2 = minOf(radius, -q + radius)
                    for (r in r1..r2) {
                        val coord = HexCoord(q, r, -q - r)
                        put(coord, HexTile(coord, TerrainType.EMPTY))
                    }
                }
            }
        )

    private fun unitAt(coord: HexCoord, faction: Faction = Faction.DOMINION, type: UnitType = UnitType.SCOUT) =
        GameUnit(type = type, faction = faction, position = coord, currentHp = type.maxHp)

    @Test
    fun scoutSeesAdjacentHexes() {
        val center = HexCoord(0, 0, 0)
        val state = GameState(
            map = emptyMap(),
            units = mapOf(center to unitAt(center))
        )
        val visible = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION)
        assertTrue(visible.contains(center))
        HexCoord.directions.forEach { dir ->
            assertTrue("Scout should see $dir", visible.contains(center + dir))
        }
    }

    @Test
    fun bonusProviderRangeBoostApplied() {
        val center = HexCoord(0, 0, 0)
        val state = GameState(
            map = emptyMap(radius = 5),
            units = mapOf(center to unitAt(center, type = UnitType.FIGHTER)) // base range 2
        )
        val baseVisible = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION,
            object : VisionBonusProvider {
                override fun rangeBonus(faction: Faction) = 0
                override fun rangeMult() = 1f
            })
        val boostedVisible = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION,
            object : VisionBonusProvider {
                override fun rangeBonus(faction: Faction) = 2
                override fun rangeMult() = 1f
            })
        assertTrue(boostedVisible.size > baseVisible.size)
    }

    @Test
    fun solarFlareMultHalvesRange() {
        val center = HexCoord(0, 0, 0)
        val state = GameState(
            map = emptyMap(radius = 5),
            units = mapOf(center to unitAt(center, type = UnitType.SCOUT)) // base range 3
        )
        val normal = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION,
            object : VisionBonusProvider {
                override fun rangeBonus(faction: Faction) = 0
                override fun rangeMult() = 1f
            })
        val flare = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION,
            object : VisionBonusProvider {
                override fun rangeBonus(faction: Faction) = 0
                override fun rangeMult() = 0.5f
            })
        assertTrue(flare.size < normal.size)
    }

    @Test
    fun asteroidBlocksLineOfSight() {
        val center = HexCoord(0, 0, 0)
        val blocker = HexCoord(1, -1, 0)
        val behind = HexCoord(2, -2, 0)
        val map = emptyMap(radius = 5)
        val tiles = map.tiles.toMutableMap()
        tiles[blocker] = HexTile(blocker, TerrainType.ASTEROIDS)
        val state = GameState(
            map = map.copy(tiles = tiles),
            units = mapOf(center to unitAt(center))
        )
        val visible = VisionSystem.calculateVisibleHexes(state, Faction.DOMINION)
        assertFalse("Hex behind asteroid should be invisible", visible.contains(behind))
    }
}
