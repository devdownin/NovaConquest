import kotlin.math.*

data class HexCoord(val q: Int, val r: Int, val s: Int)

fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
    var q = Math.round(fracQ).toInt()
    var r = Math.round(fracR).toInt()
    var s = Math.round(fracS).toInt()

    val qDiff = Math.abs(q - fracQ)
    val rDiff = Math.abs(r - fracR)
    val sDiff = Math.abs(s - fracS)

    if (qDiff > rDiff && qDiff > sDiff) {
        q = -r - s
    } else if (rDiff > sDiff) {
        r = -q - s
    } else {
        s = -q - r
    }
    return HexCoord(q, r, s)
}

fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
    val HEX_RADIUS = 30f
    val q = (sqrt(3f) / 3 * (x - centerX) - 1f / 3 * (y - centerY)) / HEX_RADIUS
    val r = (2f / 3 * (y - centerY)) / HEX_RADIUS
    return hexRound(q.toDouble(), r.toDouble(), -q.toDouble() - r.toDouble())
}

fun main() {
    var prev = pixelToHex(0f, 0f, 0f, 0f)
    for (i in -1000..1000) {
        for (j in -1000..1000) {
            val curr = pixelToHex(i / 10f, j / 10f, 0f, 0f)
            // A coordinate change of 0.1 pixel should not jump by more than 1 hex
            val dq = abs(curr.q - prev.q)
            val dr = abs(curr.r - prev.r)
            val ds = abs(curr.s - prev.s)
            val dist = (dq + dr + ds) / 2
            if (dist > 1 && i != -1000) {
                println("Jump detected at $i, $j: prev=$prev, curr=$curr, dist=$dist")
                return
            }
            prev = curr
        }
    }
    println("No jump detected.")
}
