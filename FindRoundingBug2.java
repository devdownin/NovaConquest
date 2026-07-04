public class FindRoundingBug2 {
    public static void main(String[] args) {
        // From https://www.redblobgames.com/grids/hexagons/#rounding:
        // "Math.round() in python 3 does half-to-even rounding...
        // However, in Javascript Math.round does half-up rounding, which breaks the tie breaking."

        // Wait, why didn't I find it?
        // Maybe because Java `Math.round(-0.5)` is 0?
        // Let's test what javascript does for Math.round(-0.5)
        // JS: Math.round(-0.5) returns -0.
        // Java: Math.round(-0.5) returns 0.

        // Let's look at kotlin standard library code for round-to-even.
        // If I change it to `kotlin.math.roundToInt()`, does it fix the problem they're complaining about?
        // Wait! The user said "rondins bug", maybe they meant "rounding bug" and they just spelled it "rondins" (it's french for logs, but also a typo for rounding, or 'arrondis' (roundings in french)).

        // If they mean "rounding bug", and the code currently uses `Math.round(fracQ).toInt()`,
        // this is actually calling `java.lang.Math.round` because kotlin.math.round returns Double.

        // In Kotlin standard library, kotlin.math.roundToInt() rounds half-to-even.
        // Java's Math.round() rounds half-up.

        // Let's modify BOTH `TacticalMapScreen.kt` and `HexCoord.kt` to use `kotlin.math.roundToInt()`.
    }
}
