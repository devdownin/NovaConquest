package com.novaempire.core.hex

import kotlin.math.abs

/**
 * Cube coordinates for a hexagonal grid.
 */
data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        require(q + r + s == 0) { "q + r + s must equal 0" }
    }

    operator fun plus(other: HexCoord) = HexCoord(q + other.q, r + other.r, s + other.s)
    operator fun minus(other: HexCoord) = HexCoord(q - other.q, r - other.r, s - other.s)

    fun distanceTo(other: HexCoord): Int {
        return (abs(q - other.q) + abs(r - other.r) + abs(s - other.s)) / 2
    }

    companion object {
        val directions = listOf(
            HexCoord(1, 0, -1), HexCoord(1, -1, 0), HexCoord(0, -1, 1),
            HexCoord(-1, 0, 1), HexCoord(-1, 1, 0), HexCoord(0, 1, -1)
        )
    }
}
