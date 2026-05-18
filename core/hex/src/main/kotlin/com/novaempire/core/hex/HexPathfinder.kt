package com.novaempire.core.hex

import java.util.PriorityQueue

interface GridMap {
    fun isPassable(coord: HexCoord): Boolean
    fun getNeighbors(coord: HexCoord): List<HexCoord>
}

object HexPathfinder {

    fun findPath(
        start: HexCoord,
        goal: HexCoord,
        gridMap: GridMap,
        maxCost: Int = Int.MAX_VALUE
    ): List<HexCoord>? {
        val frontier = PriorityQueue<Pair<HexCoord, Int>>(compareBy { it.second })
        frontier.add(start to 0)

        val cameFrom = mutableMapOf<HexCoord, HexCoord?>()
        val costSoFar = mutableMapOf<HexCoord, Int>()

        cameFrom[start] = null
        costSoFar[start] = 0

        while (frontier.isNotEmpty()) {
            val current = frontier.poll()?.first ?: break

            if (current == goal) {
                break
            }

            for (next in gridMap.getNeighbors(current)) {
                if (!gridMap.isPassable(next)) continue

                // Base movement cost is 1 for adjacent hexes
                val newCost = costSoFar[current]!! + 1

                if (newCost > maxCost) continue

                if (!costSoFar.containsKey(next) || newCost < costSoFar[next]!!) {
                    costSoFar[next] = newCost
                    val priority = newCost + current.distanceTo(goal)
                    frontier.add(next to priority)
                    cameFrom[next] = current
                }
            }
        }

        if (!cameFrom.containsKey(goal)) {
            return null // No path found
        }

        // Reconstruct path
        val path = mutableListOf<HexCoord>()
        var current: HexCoord? = goal
        while (current != null && current != start) {
            path.add(current)
            current = cameFrom[current]
        }
        path.reverse()
        return path
    }
}
