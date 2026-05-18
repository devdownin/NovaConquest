package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.hex.HexCoord
import kotlin.random.Random

class MapFactory {
    companion object {
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
                        // Standard procedural generation
                        terrain = when {
                            q == 0 && r == 0 && archetype != MapArchetype.ZODIAC -> TerrainType.BLACK_HOLE
                            random.nextDouble() < 0.1 -> TerrainType.PLANET
                            random.nextDouble() < 0.15 -> TerrainType.ASTEROIDS
                            random.nextDouble() < 0.1 -> TerrainType.NEBULA
                            else -> TerrainType.EMPTY
                        }
                        if (terrain == TerrainType.PLANET) {
                            systemLevel = random.nextInt(1, 5)
                        }
                    }

                    tiles[coord] = HexTile(coord, terrain, systemLevel)
                }
            }

            // Ensure player spawn points have planets
            val spawnPoints = listOf(
                HexCoord(0, -radius, radius),
                HexCoord(0, radius, -radius)
            )

            spawnPoints.forEach { coord ->
                if (tiles.containsKey(coord) && !zodiacNodes.contains(coord)) {
                    tiles[coord] = HexTile(coord, TerrainType.PLANET, 2)
                }
            }

            return GameMap(tiles, radius, archetype, zodiacNodes)
        }
    }
}
