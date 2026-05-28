package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFactoryTest {

    private fun reachableFrom(map: GameMap, origin: HexCoord): Set<HexCoord> {
        val seen = mutableSetOf<HexCoord>()
        if (map.tiles[origin]?.terrain?.isPassable != true) return seen
        val queue = ArrayDeque<HexCoord>()
        seen.add(origin)
        queue.add(origin)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dir in HexCoord.directions) {
                val next = current + dir
                if (next !in seen && map.tiles[next]?.terrain?.isPassable == true) {
                    seen.add(next)
                    queue.add(next)
                }
            }
        }
        return seen
    }

    private fun assertFullyConnected(radius: Int, archetype: MapArchetype, seed: Long) {
        val map = MapFactory.generateMap(radius = radius, archetype = archetype, seed = seed)
        val hub = MapFactory.spawnPointsFor(radius).first { map.tiles.containsKey(it) }
        val reachable = reachableFrom(map, hub)

        MapFactory.spawnPointsFor(radius).filter { map.tiles.containsKey(it) }.forEach { spawn ->
            assertTrue(
                "spawn $spawn not reachable (radius=$radius, archetype=$archetype, seed=$seed)",
                spawn in reachable
            )
            // A spawning ship must always have at least one passable neighbour.
            val hasExit = HexCoord.directions.any { map.tiles[spawn + it]?.terrain?.isPassable == true }
            assertTrue("spawn $spawn is boxed in (radius=$radius, seed=$seed)", hasExit)
        }

        map.tiles.values.filter { it.terrain == TerrainType.PLANET }.forEach { planet ->
            assertTrue(
                "planet ${planet.coord} not reachable (radius=$radius, archetype=$archetype, seed=$seed)",
                planet.coord in reachable
            )
        }
    }

    @Test
    fun everyMapIsFullyConnectedAcrossSeedsAndSizes() {
        val radii = listOf(3, 5, 8, 12)
        for (radius in radii) {
            for (seed in 0L until 50L) {
                assertFullyConnected(radius, MapArchetype.STANDARD, seed)
            }
        }
    }

    @Test
    fun zodiacMapsAreFullyConnected() {
        for (seed in 0L until 25L) {
            assertFullyConnected(5, MapArchetype.ZODIAC, seed)
            assertFullyConnected(8, MapArchetype.ZODIAC, seed)
        }
    }

    @Test
    fun spawnPointsAreHabitablePlanets() {
        val map = MapFactory.generateMap(radius = 5, seed = 7)
        MapFactory.spawnPointsFor(5).forEach { coord ->
            assertEquals(TerrainType.PLANET, map.tiles[coord]?.terrain)
        }
    }
}
