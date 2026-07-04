import kotlin.math.*

data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        require(q + r + s == 0) { "q=$q, r=$r, s=$s, sum=${q+r+s}" }
    }
}

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

fun main() {
    val random = java.util.Random(42)
    var errors = 0
    for (i in 0 until 100000) {
        val fracQ = random.nextDouble() * 20 - 10
        val fracR = random.nextDouble() * 20 - 10
        val fracS = -fracQ - fracR
        try {
            hexRound(fracQ, fracR, fracS)
        } catch (e: Exception) {
            println("Failed on $fracQ, $fracR, $fracS: ${e.message}")
            errors++
            if (errors > 5) break
        }
    }
    if (errors == 0) println("All tests passed!")
}
