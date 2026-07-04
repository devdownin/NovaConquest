data class HexCoord(val q: Int, val r: Int, val s: Int) {
    init {
        require(q + r + s == 0) { "q + r + s must equal 0, got $q + $r + $s = ${q + r + s}" }
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

fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
    val HEX_RADIUS = 30f
    val q = (Math.sqrt(3.0) / 3 * (x - centerX) - 1.0 / 3 * (y - centerY)) / HEX_RADIUS
    val r = (2.0 / 3 * (y - centerY)) / HEX_RADIUS
    // Bug might be here: computing fracS exactly as -q - r but there's a floating point issue?
    return hexRound(q, r, -q - r)
}

fun main() {
    var fails = 0
    for (i in 0..10000) {
        val x = (Math.random() * 2000 - 1000).toFloat()
        val y = (Math.random() * 2000 - 1000).toFloat()
        try {
            pixelToHex(x, y, 0f, 0f)
        } catch(e: Exception) {
            println("Failed at $x, $y: ${e.message}")
            fails++
        }
    }
    println("Completed with $fails fails.")
}
