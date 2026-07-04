import kotlin.math.*

fun hexRound(fracQ: Double, fracR: Double, fracS: Double) {
    var q = kotlin.math.round(fracQ).toInt()
    var r = kotlin.math.round(fracR).toInt()
    var s = kotlin.math.round(fracS).toInt()

    val qDiff = kotlin.math.abs(q - fracQ)
    val rDiff = kotlin.math.abs(r - fracR)
    val sDiff = kotlin.math.abs(s - fracS)

    if (qDiff > rDiff && qDiff > sDiff) {
        q = -r - s
    } else if (rDiff > sDiff) {
        r = -q - s
    } else {
        s = -q - r
    }
    if (q + r + s != 0) {
        println("FAILED: \$fracQ, \$fracR, \$fracS => \$q, \$r, \$s sum=\${q+r+s}")
    }
}

fun main() {
    var errors = 0
    for (q in -20..20) {
        for (r in -20..20) {
            val fracQ = q / 10.0
            val fracR = r / 10.0
            val fracS = -fracQ - fracR

            var jq = java.lang.Math.round(fracQ).toInt()
            var jr = java.lang.Math.round(fracR).toInt()
            var js = java.lang.Math.round(fracS).toInt()

            val jqDiff = java.lang.Math.abs(jq - fracQ)
            val jrDiff = java.lang.Math.abs(jr - fracR)
            val jsDiff = java.lang.Math.abs(js - fracS)

            if (jqDiff > jrDiff && jqDiff > jsDiff) jq = -jr - js
            else if (jrDiff > jsDiff) jr = -jq - js
            else js = -jq - jr

            if (jq + jr + js != 0) {
                println("Java Math.round FAILED: \$fracQ, \$fracR, \$fracS => \$jq, \$jr, \$js sum=\${jq+jr+js}")
                errors++
            }
        }
    }
    if (errors == 0) println("No errors found.")
}
