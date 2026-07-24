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
    fun newArchetypesAreFullyConnected() {
        val archetypes = listOf(MapArchetype.NEBULA_EXPANSE, MapArchetype.ASTEROID_BELT)
        for (archetype in archetypes) {
            for (radius in listOf(3, 5, 8, 12)) {
                for (seed in 0L until 25L) {
                    assertFullyConnected(radius, archetype, seed)
                }
            }
        }
    }

    @Test
    fun nebulaExpanseGeneratesVisionBlockingTerrain() {
        // Across a spread of seeds the nebula-heavy archetype must actually place the
        // previously-unused vision-blocking terrains, not just EMPTY/PLANET/ASTEROIDS.
        val terrains = (0L until 20L).flatMap { seed ->
            MapFactory.generateMap(radius = 8, archetype = MapArchetype.NEBULA_EXPANSE, seed = seed)
                .tiles.values.map { it.terrain }
        }.toSet()
        assertTrue("NEBULA_EXPANSE never generated PLASMA_CLOUD", terrains.contains(TerrainType.PLASMA_CLOUD))
        assertTrue("NEBULA_EXPANSE never generated ION_STORM terrain", terrains.contains(TerrainType.ION_STORM))
    }

    @Test
    fun wormholeCountScalesWithRadius() {
        // A wormhole anchor is skipped if it happens to land on a planet, so counts vary by
        // seed — compare the maximum reached across a spread of seeds instead of a single map.
        fun maxWormholes(radius: Int) = (0L until 20L).maxOf { seed ->
            MapFactory.generateMap(radius = radius, seed = seed).tiles.values.count { it.terrain == TerrainType.WORMHOLE }
        }
        // Small maps attempt a single pair (<= 2 wormholes); large maps attempt up to three pairs.
        assertTrue("Small map should have at most one wormhole pair", maxWormholes(3) <= 2)
        assertTrue("Large map should offer more wormholes than a small one", maxWormholes(12) > maxWormholes(3))
    }

    @Test
    fun seedIsStoredOnGeneratedMap() {
        val map = MapFactory.generateMap(radius = 5, seed = 12345L)
        assertEquals(12345L, map.seed)
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
