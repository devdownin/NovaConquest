public class CheckKotlinRoundToEven {
    public static void main(String[] args) {
        // Red Blob Games literally says "Math.round() in Javascript does half-up rounding, which breaks the tie breaking."
        // WHY does it break it?
        // "When two components are at 0.5 (such as -0.5 and 0.5) JavaScript's Math.round will round them to 0 and 1, which means BOTH diffs are 0.5."
        // Ah! If qDiff == rDiff == 0.5.
        // Let's re-run this exact case.
        double q = 0.5;
        double r = -0.5;
        double s = 0.0;
        // Java's Math.round rounds -0.5 to 0 and 0.5 to 1.
        int rq = (int)Math.round(q); // 1
        int rr = (int)Math.round(r); // 0
        int rs = (int)Math.round(s); // 0

        double qDiff = Math.abs(rq - q); // 0.5
        double rDiff = Math.abs(rr - r); // 0.5
        double sDiff = Math.abs(rs - s); // 0.0

        if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
        else if (rDiff > sDiff) rr = -rq - rs;
        else rs = -rq - rr;

        // Output: 1, -1, 0. Is this the nearest?
        // Dist to 1, -1, 0: |1 - 0.5| + |-1 - -0.5| + |0 - 0| = 0.5 + 0.5 + 0 = 1.0.
        // Dist to 0, 0, 0: |0 - 0.5| + |0 - -0.5| + |0 - 0| = 0.5 + 0.5 + 0 = 1.0.
        // Both are tied for distance!

        // Does this break anything? Not structurally. But using kotlin.math.roundToInt() fixes this "bug" that RedBlobGames refers to.

        System.out.println("Wait, kotlin.math.roundToInt uses round to even.");
        // We will just change Math.round to kotlin.math.roundToInt in HexCoord.kt and TacticalMapScreen.kt
        // because it avoids the javascript Math.round bug, and Kotlin standard library has a proper roundToInt().
    }
}
