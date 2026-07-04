public class test_java_round_bug_math {
    public static void main(String[] args) {
        // According to the bug report: "Audit code rondins bug" -> rounding bug
        // Kotlin Math.round calls java.lang.Math.round
        // HexCoord.round(0.6, -0.9, 0.3) -> q=1, r=-1, s=0

        System.out.println("0.6, -0.9, 0.3");
        double fracQ = 0.6;
        double fracR = -0.9;
        double fracS = 0.3;

        int q = (int)Math.round(fracQ); // 1
        int r = (int)Math.round(fracR); // -1
        int s = (int)Math.round(fracS); // 0

        double qDiff = Math.abs(q - fracQ); // 0.4
        double rDiff = Math.abs(r - fracR); // 0.1
        double sDiff = Math.abs(s - fracS); // 0.3

        System.out.println("qDiff=" + qDiff + " rDiff=" + rDiff + " sDiff=" + sDiff);
        if (qDiff > rDiff && qDiff > sDiff) q = -r - s; // 1 = 1 - 0 = 1
        else if (rDiff > sDiff) r = -q - s;
        else s = -q - r;

        System.out.println(q + ", " + r + ", " + s + " sum=" + (q+r+s));

        // Let's test with a point exactly halfway between hexes
        // fracQ = 0.5, fracR = -1.5, fracS = 1.0
        System.out.println("\n0.5, -1.5, 1.0");
        fracQ = 0.5;
        fracR = -1.5;
        fracS = 1.0;

        q = (int)Math.round(fracQ); // 1
        r = (int)Math.round(fracR); // -1
        s = (int)Math.round(fracS); // 1
        System.out.println("Initial round: " + q + ", " + r + ", " + s); // sum = 1

        qDiff = Math.abs(q - fracQ); // 0.5
        rDiff = Math.abs(r - fracR); // 0.5
        sDiff = Math.abs(s - fracS); // 0.0

        System.out.println("qDiff=" + qDiff + " rDiff=" + rDiff + " sDiff=" + sDiff);
        if (qDiff > rDiff && qDiff > sDiff) q = -r - s; // Not taken, 0.5 is not > 0.5
        else if (rDiff > sDiff) r = -q - s; // 0.5 > 0.0 -> r = -1 - 1 = -2
        else s = -q - r;

        System.out.println(q + ", " + r + ", " + s + " sum=" + (q+r+s));
    }
}
