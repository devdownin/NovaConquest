package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.PlanetSpecialty
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

        /**
         * Cumulative terrain thresholds per archetype: `(upperBound, terrain)` pairs consumed in
         * order against a single `nextDouble()` draw (independent buckets — chaining draws skewed
         * them). Anything above the last bound is EMPTY. All terrains here are passable except
         * ASTEROIDS; PLASMA_CLOUD / ION_STORM block vision (a real, implemented effect).
         */
        private fun terrainWeights(archetype: MapArchetype): List<Pair<Double, TerrainType>> = when (archetype) {
            MapArchetype.ASTEROID_BELT -> listOf(
                0.09 to TerrainType.PLANET,
                0.34 to TerrainType.ASTEROIDS,
                0.40 to TerrainType.NEBULA,
                0.43 to TerrainType.PLASMA_CLOUD,
                0.45 to TerrainType.ION_STORM,
                0.47 to TerrainType.ANOMALY
            )
            MapArchetype.NEBULA_EXPANSE -> listOf(
                0.10 to TerrainType.PLANET,
                0.18 to TerrainType.ASTEROIDS,
                0.40 to TerrainType.NEBULA,
                0.48 to TerrainType.PLASMA_CLOUD,
                0.54 to TerrainType.ION_STORM,
                0.57 to TerrainType.ANOMALY
            )
            // STANDARD and ZODIAC share the baseline mix.
            else -> listOf(
                0.10 to TerrainType.PLANET,
                0.23 to TerrainType.ASTEROIDS,
                0.31 to TerrainType.NEBULA,
                0.35 to TerrainType.PLASMA_CLOUD,
                0.38 to TerrainType.ION_STORM,
                0.40 to TerrainType.ANOMALY
            )
        }

        private fun rollTerrain(weights: List<Pair<Double, TerrainType>>, roll: Double): TerrainType =
            weights.firstOrNull { roll < it.first }?.second ?: TerrainType.EMPTY

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

            val weights = terrainWeights(archetype)

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
                            else -> rollTerrain(weights, random.nextDouble())
                        }
                        if (terrain == TerrainType.PLANET) {
                            systemLevel = random.nextInt(2, 5)
                        }
                    }

                    tiles[coord] = HexTile(coord, terrain, systemLevel)
                }
            }

            // Ensure player spawn points are habitable planets.
            val spawnPoints = spawnPointsFor(radius)
            spawnPoints.forEach { coord ->
                if (tiles.containsKey(coord) && !zodiacNodes.contains(coord)) {
                    tiles[coord] = HexTile(coord, TerrainType.PLANET, 3)
                }
            }

            // Guarantee that every spawn and every planet sits in one passable region,
            // so a ship can always move and reach objectives regardless of the seed.
            ensureConnectivity(tiles, spawnPoints)

            // Symmetric wormhole pairs (count scales with radius). Wormholes are passable, so
            // this only ever adds passability and never breaks the connectivity guaranteed above.
            placeWormholes(tiles, radius, spawnPoints)

            // Assign specialties to ~25 % of non-spawn planets.
            val specialties = PlanetSpecialty.values()
            tiles.keys.toList().forEach { coord ->
                val t = tiles[coord] ?: return@forEach
                if (t.terrain == TerrainType.PLANET && coord !in spawnPoints && random.nextFloat() < 0.25f) {
                    tiles[coord] = t.copy(specialty = specialties[random.nextInt(specialties.size)])
                }
            }

            return GameMap(tiles, radius, archetype, zodiacNodes, seed)
        }

        /**
         * Places `radius/4` (1‑3) point‑symmetric wormhole pairs at mid‑ring anchors, skipping
         * spawns, planets, black holes and existing wormholes. With `tech_wormhole_nav` every
         * wormhole links to every other, so more pairs mean more long‑range jump options on
         * bigger maps (previously a single pair, i.e. a single link, regardless of size).
         */
        private fun placeWormholes(tiles: MutableMap<HexCoord, HexTile>, radius: Int, spawnPoints: List<HexCoord>) {
            val half = radius / 2
            if (half <= 0) return
            val pairCount = (radius / 4).coerceIn(1, 3)
            val anchors = listOf(
                HexCoord(half, -half, 0),
                HexCoord(half, 0, -half),
                HexCoord(0, half, -half)
            ).take(pairCount)
            for (anchor in anchors) {
                for (w in listOf(anchor, HexCoord(-anchor.q, -anchor.r, -anchor.s))) {
                    val existing = tiles[w] ?: continue
                    if (existing.terrain != TerrainType.PLANET &&
                        existing.terrain != TerrainType.BLACK_HOLE &&
                        existing.terrain != TerrainType.WORMHOLE &&
                        w !in spawnPoints) {
                        tiles[w] = existing.copy(terrain = TerrainType.WORMHOLE, systemLevel = 0)
                    }
                }
            }
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

            // Maintain the reachable set incrementally: each carved corridor runs to the hub, so
            // it is contiguous with the reachable region — a BFS seeded from just the carved cells
            // adds only the newly-connected hexes instead of re-scanning the whole region each time.
            val reachable = reachablePassable(tiles, hub)
            for (target in targets) {
                if (target in reachable) continue
                val carved = carveLine(tiles, target, hub)
                expandReachable(tiles, reachable, carved)
            }
        }

        /** Continues a BFS from [seeds], growing [reachable] with every newly-connected passable hex. */
        private fun expandReachable(
            tiles: Map<HexCoord, HexTile>,
            reachable: MutableSet<HexCoord>,
            seeds: List<HexCoord>
        ) {
            val queue = ArrayDeque<HexCoord>()
            for (c in seeds) {
                if (c !in reachable && isPassable(tiles, c)) {
                    reachable.add(c)
                    queue.add(c)
                }
            }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (dir in HexCoord.directions) {
                    val next = current + dir
                    if (next !in reachable && isPassable(tiles, next)) {
                        reachable.add(next)
                        queue.add(next)
                    }
                }
            }
        }

        /** Carves a straight hex line, clearing impassable cells; returns the line's coords. */
        private fun carveLine(tiles: MutableMap<HexCoord, HexTile>, from: HexCoord, to: HexCoord): List<HexCoord> {
            val dist = from.distanceTo(to)
            val line = ArrayList<HexCoord>(dist + 1)
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
                line.add(coord)
            }
            return line
        }
    }
}
