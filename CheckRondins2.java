public class CheckRondins2 {
    public static void main(String[] args) {
        // "rondins bug"
        // Wait, maybe it's Kotlin `kotlin.math.roundToInt()` instead of `Math.round().toInt()`!
        // Red Blob games bug:
        // "Math.round() in python 3 does half-to-even rounding, which is fine.
        // However, in Javascript Math.round does half-up rounding, which breaks the tie breaking."

        // Java's Math.round does half-up rounding. This breaks tie breaking.
        // What breaks tie breaking?
        // If q=0.5 and r=-0.5, Java Math.round gives rq=1, rr=0.
        // Wait, what if q=-0.5, r=0.5, s=0.0?
        // rq=0, rr=1.

        // What if we test with the EXACT formula but look for errors where the returned hex is NOT the nearest hex geometrically?
    }
}
