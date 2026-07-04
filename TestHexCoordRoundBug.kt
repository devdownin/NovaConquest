data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        require(q + r + s == 0) { "q + r + s must equal 0, but got \$q + \$r + \$s = \${q + r + s}" }
    }
}

fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
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

fun main() {
    val random = java.util.Random(42)
    var failures = 0
    // Try to trigger the rounding bug where q+r+s != 0
    for (i in 0 until 1000000) {
        val q = random.nextDouble() * 20 - 10
        val r = random.nextDouble() * 20 - 10
        val s = -q - r
        try {
            hexRound(q, r, s)
        } catch(e: IllegalArgumentException) {
            println("Failed for \$q, \$r, \$s")
            println(e.message)
            failures++
            if (failures > 5) break
        }
    }

    // Explicit test case: when two diffs are equal and maximal
    val q = -0.5
    val r = -0.5
    val s = 1.0
    try {
        hexRound(q, r, s)
    } catch(e: IllegalArgumentException) {
        println("Explicit test case failed!")
        println(e.message)
    }

    val q2 = 0.5
    val r2 = 0.5
    val s2 = -1.0
    try {
        hexRound(q2, r2, s2)
    } catch(e: IllegalArgumentException) {
        println("Explicit test case 2 failed!")
        println(e.message)
    }
}
