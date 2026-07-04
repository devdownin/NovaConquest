import kotlin.math.roundToInt
import kotlin.math.abs

data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        require(q + r + s == 0) { "q + r + s must equal 0 (got \$q, \$r, \$s)" }
    }
}

fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
    var q = fracQ.roundToInt()
    var r = fracR.roundToInt()
    var s = fracS.roundToInt()
    val qDiff = abs(q - fracQ)
    val rDiff = abs(r - fracR)
    val sDiff = abs(s - fracS)
    if (qDiff > rDiff && qDiff > sDiff) q = -r - s
    else if (rDiff > sDiff) r = -q - s
    else s = -q - r
    return HexCoord(q, r, s)
}

fun main() {
    println("Using roundToInt")
    for (q in -200..200) {
        for (r in -200..200) {
            val fracQ = q / 100.0
            val fracR = r / 100.0
            val fracS = -fracQ - fracR
            try {
                hexRound(fracQ, fracR, fracS)
            } catch (e: Exception) {
                println("Failed on: \$fracQ, \$fracR, \$fracS: \${e.message}")
                return
            }
        }
    }
    println("All tests passed with roundToInt")
}
