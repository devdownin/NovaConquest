public class FindRoundingBug {
    public static void main(String[] args) {
        // According to Red Blob Games, if Math.round() rounds half-way values like 0.5 to positive infinity (as in JS and Java)
        // AND you use `q_diff > r_diff && q_diff > s_diff`, then you CAN have cases where q+r+s != 0.
        // Wait, Red Blob Games: "However, in Javascript Math.round does half-up rounding, which breaks the tie breaking."
        // Let's find one exactly!

        // We know for sure it's possible if qDiff and rDiff and sDiff interact poorly.
        // Let's test combinations of exact halves and quarters.
        double[] vals = {-1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5};
        for (double q : vals) {
            for (double r : vals) {
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
                    System.out.println("BUG! " + q + ", " + r + ", " + s);
                    System.out.println("Result: " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs));
                }
            }
        }
        System.out.println("Done check.");
    }
}
