public class TestEx {
    public static void main(String[] args) {
        int fails = 0;
        for (int q = -200; q <= 200; q++) {
            for (int r = -200; r <= 200; r++) {
                double fracQ = q / 100.0;
                double fracR = r / 100.0;
                double fracS = -fracQ - fracR;

                int rq = (int)Math.round(fracQ);
                int rr = (int)Math.round(fracR);
                int rs = (int)Math.round(fracS);

                double qDiff = Math.abs(rq - fracQ);
                double rDiff = Math.abs(rr - fracR);
                double sDiff = Math.abs(rs - fracS);

                if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
                else if (rDiff > sDiff) rr = -rq - rs;
                else rs = -rq - rr;

                if (rq + rr + rs != 0) {
                    System.out.println("Failed on: " + fracQ + ", " + fracR + ", " + fracS + " => " + rq + ", " + rr + ", " + rs + " diffs=" + qDiff + "," + rDiff + "," + sDiff);
                    fails++;
                    if (fails > 10) return;
                }
            }
        }
        if (fails == 0) System.out.println("All exhaustive tests passed.");
    }
}
