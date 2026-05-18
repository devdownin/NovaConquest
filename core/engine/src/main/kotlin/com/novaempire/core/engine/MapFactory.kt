package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.hex.HexCoord
import kotlin.random.Random

class MapFactory {
    companion object {
        fun generateMap(radius: Int = 3, seed: Long = 42): GameMap {
            val random = Random(seed)
            val tiles = mutableMapOf<HexCoord, HexTile>()

            for (q in -radius..radius) {
                val r1 = maxOf(-radius, -q - radius)
                val r2 = minOf(radius, -q + radius)
                for (r in r1..r2) {
                    val s = -q - r
                    val coord = HexCoord(q, r, s)

                    // Simple procedural generation
                    val terrain = when {
                        q == 0 && r == 0 -> TerrainType.BLACK_HOLE // Center is a black hole
                        random.nextDouble() < 0.1 -> TerrainType.PLANET
                        random.nextDouble() < 0.15 -> TerrainType.ASTEROIDS
                        random.nextDouble() < 0.1 -> TerrainType.NEBULA
                        else -> TerrainType.EMPTY
                    }

                    val systemLevel = if (terrain == TerrainType.PLANET) random.nextInt(1, 6) else 0

                    tiles[coord] = HexTile(coord, terrain, systemLevel)
                }
            }

            // Ensure player spawn points have planets
            // Faction DOMINION at top left, TRADERS at bottom right
            val spawnPoints = listOf(
                HexCoord(0, -radius, radius),
                HexCoord(0, radius, -radius)
            )

            spawnPoints.forEach { coord ->
                if (tiles.containsKey(coord)) {
                    tiles[coord] = HexTile(coord, TerrainType.PLANET, 2)
                }
            }

            return GameMap(tiles, radius)
        }
    }
}
