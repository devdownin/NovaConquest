import kotlin.math.*

fun main() {
    val kotlinRound05 = kotlin.math.round(0.5)
    val kotlinRoundMinus05 = kotlin.math.round(-0.5)

    val javaRound05 = java.lang.Math.round(0.5)
    val javaRoundMinus05 = java.lang.Math.round(-0.5)

    println("Kotlin round(0.5): \$kotlinRound05") // expected: 0.0
    println("Kotlin round(-0.5): \$kotlinRoundMinus05") // expected: -0.0

    println("Java round(0.5): \$javaRound05") // expected: 1
    println("Java round(-0.5): \$javaRoundMinus05") // expected: 0
}
