public class TestRound {
    public static void main(String[] args) {
        java.util.Random random = new java.util.Random(42);
        int errors = 0;
        for (int i = 0; i < 100000; i++) {
            double fracQ = random.nextDouble() * 20 - 10;
            double fracR = random.nextDouble() * 20 - 10;
            double fracS = -fracQ - fracR;

            int q = (int)Math.round(fracQ);
            int r = (int)Math.round(fracR);
            int s = (int)Math.round(fracS);

            double qDiff = Math.abs(q - fracQ);
            double rDiff = Math.abs(r - fracR);
            double sDiff = Math.abs(s - fracS);

            if (qDiff > rDiff && qDiff > sDiff) {
                q = -r - s;
            } else if (rDiff > sDiff) {
                r = -q - s;
            } else {
                s = -q - r;
            }

            if (q + r + s != 0) {
                System.out.println("Failed on " + fracQ + ", " + fracR + ", " + fracS + " : q=" + q + ", r=" + r + ", s=" + s + " sum=" + (q+r+s));
                errors++;
                if (errors > 5) break;
            }
        }
        if (errors == 0) System.out.println("All tests passed with default Math.round");
    }
}
