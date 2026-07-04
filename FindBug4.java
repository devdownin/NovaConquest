public class FindBug4 {
    public static void main(String[] args) {
        // Redblobgames says:
        // "There is an edge case where two components have the same difference,
        // e.g., with 0.5 and -0.5, the absolute difference is 0.5.
        // Depending on which you change, you might get a valid hex or an invalid hex."

        for (double q = -2.0; q <= 2.0; q += 0.1) {
            for (double r = -2.0; r <= 2.0; r += 0.1) {
                for (double s = -2.0; s <= 2.0; s += 0.1) {
                    if (Math.abs(q + r + s) > 0.0001) continue;

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
                        return;
                    }
                }
            }
        }
        System.out.println("No failure with 0.1 steps either.");
    }
}
