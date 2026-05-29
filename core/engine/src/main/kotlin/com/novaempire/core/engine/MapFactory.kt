package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.hex.HexCoord
import kotlin.random.Random

class MapFactory {
    companion object {
        /** The six symmetric starting systems, distributed around the map edges. */
        fun spawnPointsFor(radius: Int): List<HexCoord> = listOf(
            HexCoord(0, -radius, radius),
            HexCoord(radius, -radius, 0),
            HexCoord(radius, 0, -radius),
            HexCoord(0, radius, -radius),
            HexCoord(-radius, radius, 0),
            HexCoord(-radius, 0, radius)
        )

        fun generateMap(radius: Int = 3, archetype: MapArchetype = MapArchetype.STANDARD, seed: Long = 42): GameMap {
            val random = Random(seed)
            val tiles = mutableMapOf<HexCoord, HexTile>()
            val zodiacNodes = mutableSetOf<HexCoord>()

            // If Zodiac, pre-calculate specific constellation points
            if (archetype == MapArchetype.ZODIAC) {
                // Example constellation: A cross or a star shape
                val points = listOf(
                    HexCoord(0, 0, 0),
                    HexCoord(radius - 1, -(radius - 1), 0),
                    HexCoord(-(radius - 1), radius - 1, 0),
                    HexCoord(0, radius - 1, -(radius - 1)),
                    HexCoord(0, -(radius - 1), radius - 1)
                )
                zodiacNodes.addAll(points)
            }

            for (q in -radius..radius) {
                val r1 = maxOf(-radius, -q - radius)
                val r2 = minOf(radius, -q + radius)
                for (r in r1..r2) {
                    val s = -q - r
                    val coord = HexCoord(q, r, s)

                    var terrain = TerrainType.EMPTY
                    var systemLevel = 0

                    if (archetype == MapArchetype.ZODIAC && zodiacNodes.contains(coord)) {
                        terrain = TerrainType.PLANET
                        systemLevel = 5 // Zodiac nodes are high level
                    } else {
                        // Procedural generation from a single draw so the buckets are
                        // independent of each other (chaining nextDouble() skewed them).
                        terrain = when {
                            q == 0 && r == 0 && archetype != MapArchetype.ZODIAC -> TerrainType.BLACK_HOLE
                            else -> {
                                val roll = random.nextDouble()
                                when {
                                    roll < 0.10 -> TerrainType.PLANET
                                    roll < 0.25 -> TerrainType.ASTEROIDS
                                    roll < 0.35 -> TerrainType.NEBULA
                                    else -> TerrainType.EMPTY
                                }
                            }
                        }
                        if (terrain == TerrainType.PLANET) {
                            systemLevel = random.nextInt(1, 5)
                        }
                    }

                    tiles[coord] = HexTile(coord, terrain, systemLevel)
                }
            }

            // Ensure player spawn points are habitable planets.
            val spawnPoints = spawnPointsFor(radius)
            spawnPoints.forEach { coord ->
                if (tiles.containsKey(coord) && !zodiacNodes.contains(coord)) {
                    tiles[coord] = HexTile(coord, TerrainType.PLANET, 2)
                }
            }

            // Guarantee that every spawn and every planet sits in one passable region,
            // so a ship can always move and reach objectives regardless of the seed.
            ensureConnectivity(tiles, spawnPoints)

            return GameMap(tiles, radius, archetype, zodiacNodes)
        }

        private fun isPassable(tiles: Map<HexCoord, HexTile>, coord: HexCoord): Boolean =
            tiles[coord]?.terrain?.isPassable == true

        private fun reachablePassable(tiles: Map<HexCoord, HexTile>, origin: HexCoord): MutableSet<HexCoord> {
            val seen = mutableSetOf<HexCoord>()
            if (!isPassable(tiles, origin)) return seen
            val queue = ArrayDeque<HexCoord>()
            seen.add(origin)
            queue.add(origin)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (dir in HexCoord.directions) {
                    val next = current + dir
                    if (next !in seen && isPassable(tiles, next)) {
                        seen.add(next)
                        queue.add(next)
                    }
                }
            }
            return seen
        }

        /**
         * Carves asteroid corridors until the first spawn point can reach every other
         * spawn and every planet over passable terrain. Asteroids are the only impassable
         * terrain, so clearing the cells along a hex line is enough to connect a region.
         */
        private fun ensureConnectivity(tiles: MutableMap<HexCoord, HexTile>, spawnPoints: List<HexCoord>) {
            val hub = spawnPoints.firstOrNull { tiles.containsKey(it) } ?: return
            val targets = LinkedHashSet<HexCoord>().apply {
                addAll(spawnPoints.filter { tiles.containsKey(it) })
                addAll(tiles.values.filter { it.terrain == TerrainType.PLANET }.map { it.coord })
            }
            targets.remove(hub)

            val reachable = reachablePassable(tiles, hub)
            for (target in targets) {
                if (target in reachable) continue
                carveLine(tiles, target, hub)
                reachable.addAll(reachablePassable(tiles, hub))
            }
        }

        private fun carveLine(tiles: MutableMap<HexCoord, HexTile>, from: HexCoord, to: HexCoord) {
            val dist = from.distanceTo(to)
            for (i in 0..dist) {
                val t = if (dist == 0) 0.0 else i.toDouble() / dist
                val q = from.q + (to.q - from.q) * t
                val r = from.r + (to.r - from.r) * t
                val s = from.s + (to.s - from.s) * t
                val coord = HexCoord.round(q, r, s)
                val tile = tiles[coord] ?: continue
                if (!tile.terrain.isPassable) {
                    tiles[coord] = tile.copy(terrain = TerrainType.EMPTY)
                }
            }
        }
    }
}
