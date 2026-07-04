import kotlin.math.abs

data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        if (q + r + s != 0) throw IllegalArgumentException("q + r + s must equal 0, got \$q + \$r + \$s = \${q + r + s}")
    }
}

fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
    var q = java.lang.Math.round(fracQ).toInt()
    var r = java.lang.Math.round(fracR).toInt()
    var s = java.lang.Math.round(fracS).toInt()

    val qDiff = abs(q - fracQ)
    val rDiff = abs(r - fracR)
    val sDiff = abs(s - fracS)

    if (qDiff > rDiff && qDiff > sDiff) q = -r - s
    else if (rDiff > sDiff) r = -q - s
    else s = -q - r

    return HexCoord(q, r, s)
}

fun main() {
    var fails = 0
    // Try exhaustive on a small grid of fractional coordinates
    for (q in -200..200) {
        for (r in -200..200) {
            val fracQ = q / 100.0
            val fracR = r / 100.0
            val fracS = -fracQ - fracR
            try {
                hexRound(fracQ, fracR, fracS)
            } catch (e: Exception) {
                println("Failed on: \$fracQ, \$fracR, \$fracS")
                fails++
                if (fails > 10) return
            }
        }
    }
    if (fails == 0) println("All exhaustive tests passed.")
}
