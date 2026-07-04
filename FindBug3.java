public class FindBug3 {
    public static void main(String[] args) {
        // Redblobgames literally says this in the python implementation:
        // "Math.round() in python 3 does half-to-even rounding...
        // However, in Javascript Math.round does half-up rounding, which breaks the tie breaking."

        // Let's test half-up rounding (what Java does) to see if we can find a failure case.
        // What if we try more combinations?
        for (double q = -10.0; q <= 10.0; q += 0.25) {
            for (double r = -10.0; r <= 10.0; r += 0.25) {
                double s = -q - r;

                int rq = (int)Math.round(q);
                int rr = (int)Math.round(r);
                int rs = (int)Math.round(s);

                double qDiff = Math.abs(rq - q);
                double rDiff = Math.abs(rr - r);
                double sDiff = Math.abs(rs - s);

                if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
                else if (rDiff > sDiff) rr = -rq - rs;
                else rs = -rq - rr;

                if (rq + rr + rs != 0) {
                    System.out.println("FAILED! q=" + q + " r=" + r + " s=" + s);
                    System.out.println("Diffs: q=" + qDiff + " r=" + rDiff + " s=" + sDiff);
                    System.out.println("Result: " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs));
                    return;
                }
            }
        }
        System.out.println("Passed half-up combinations with 0.25 steps.");
    }
}
