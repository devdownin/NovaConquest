public class TestRoundingBug2 {
    public static void main(String[] args) {
        // Find inputs that result in sum != 0 after adjustment
        for (double q = -2.0; q <= 2.0; q += 0.1) {
            for (double r = -2.0; r <= 2.0; r += 0.1) {
                double s = -q - r;

                int rq = (int)Math.round(q);
                int rr = (int)Math.round(r);
                int rs = (int)Math.round(s);

                double qDiff = Math.abs(rq - q);
                double rDiff = Math.abs(rr - r);
                double sDiff = Math.abs(rs - s);

                if (qDiff > rDiff && qDiff > sDiff) {
                    rq = -rr - rs;
                } else if (rDiff > sDiff) {
                    rr = -rq - rs;
                } else {
                    rs = -rq - rr;
                }

                if (rq + rr + rs != 0) {
                    System.out.printf("Failed for %.1f, %.1f, %.1f: qDiff=%.1f rDiff=%.1f sDiff=%.1f => %d, %d, %d sum=%d\n",
                        q, r, s, qDiff, rDiff, sDiff, rq, rr, rs, rq+rr+rs);
                }
            }
        }
    }
}
