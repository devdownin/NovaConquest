public class TestKotlinRound {
    public static void main(String[] args) {
        System.out.println("Testing Kotlin Math.round behavior in Java");

        // Let's test standard double rounding
        double val = -0.5;
        System.out.println("Math.round(-0.5): " + Math.round(val)); // Expected: 0

        // Wait, Kotlin's kotlin.math.round returns Double, not Long or Int.
        // And it rounds half to even, unlike java.lang.Math.round which rounds half to positive infinity.
        // Wait, the Kotlin code in TacticalMapScreen uses `Math.round`, which actually resolves to `java.lang.Math.round` if it's imported, or Kotlin's standard library if we import kotlin.math.*
        // But TacticalMapScreen.kt doesn't import java.lang.Math, it uses `Math.round` which must mean `java.lang.Math.round`. Let's check imports.
    }
}
