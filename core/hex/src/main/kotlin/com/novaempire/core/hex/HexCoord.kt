package com.novaempire.core.hex
import kotlinx.serialization.Serializable

import kotlin.math.abs

/**
 * Cube coordinates for a hexagonal grid.
 */
@Serializable
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

        fun round(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
            var q = Math.round(fracQ).toInt()
            var r = Math.round(fracR).toInt()
            var s = Math.round(fracS).toInt()
            val qDiff = Math.abs(q - fracQ)
            val rDiff = Math.abs(r - fracR)
            val sDiff = Math.abs(s - fracS)
            if (qDiff > rDiff && qDiff > sDiff) q = -r - s
            else if (rDiff > sDiff) r = -q - s
            else s = -q - r
            return HexCoord(q, r, s)
        }
    }
}
