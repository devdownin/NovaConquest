import kotlin.math.*

fun main() {
    val kotlinRound = kotlin.math.round(0.5) // returns 0.0 because of half-to-even
    val javaRound = java.lang.Math.round(0.5) // returns 1

    // In com.novaempire.core.hex.HexCoord.Companion.round:
    // var q = Math.round(fracQ).toInt()
    // It's using java.lang.Math because java.lang.* is imported by default in Kotlin, and kotlin.math.round requires `import kotlin.math.round` or `import kotlin.math.*`.
    // Wait, let's see if HexCoord.kt imports kotlin.math.* or java.lang.Math.*
}
