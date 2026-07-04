public class TestFloatingPointErrors {
    public static void main(String[] args) {
        // Pixel coordinates that produce exact halves can be problematic
        // E.g., when x and y cause fracQ, fracR to be things like 0.5, 0.5

        System.out.println("Let's simulate thousands of random pixels on the screen");
        int fails = 0;
        float HEX_RADIUS = 30f;

        for (int i = 0; i < 10000; i++) {
            float x = (float) (Math.random() * 2000 - 1000);
            float y = (float) (Math.random() * 2000 - 1000);

            float q = (float)(Math.sqrt(3f) / 3 * x - 1f / 3 * y) / HEX_RADIUS;
            float r = (2f / 3 * y) / HEX_RADIUS;
            float s = -q - r;

            // This represents fracQ, fracR, fracS passed to hexRound
            // Wait, in Kotlin it's:
            // return hexRound(q.toDouble(), r.toDouble(), -q.toDouble() - r.toDouble())

            double dQ = q;
            double dR = r;
            double dS = -dQ - dR;

            int rq = (int)Math.round(dQ);
            int rr = (int)Math.round(dR);
            int rs = (int)Math.round(dS);

            double qDiff = Math.abs(rq - dQ);
            double rDiff = Math.abs(rr - dR);
            double sDiff = Math.abs(rs - dS);

            if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
            else if (rDiff > sDiff) rr = -rq - rs;
            else rs = -rq - rr;

            if (rq + rr + rs != 0) {
                System.out.println("Failed on pixel " + x + ", " + y + " => q=" + dQ + " r=" + dR + " s=" + dS);
                System.out.println("Rounded: " + rq + ", " + rr + ", " + rs + " diffs=" + qDiff + "," + rDiff + "," + sDiff);
                fails++;
                if (fails > 10) return;
            }
        }
        if (fails == 0) {
            System.out.println("No failures from random pixels.");
        }
    }
}
