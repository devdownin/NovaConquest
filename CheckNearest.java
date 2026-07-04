public class CheckNearest {
    public static void main(String[] args) {
        // Geometric distance from (fracQ, fracR, fracS) to (rq, rr, rs)
        // Let's test if the algorithm always picks the *nearest* valid hex.
        for (double q = -2.0; q <= 2.0; q += 0.1) {
            for (double r = -2.0; r <= 2.0; r += 0.1) {
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

                // Now check if this is the ACTUAL nearest hex.
                int bestQ = rq;
                int bestR = rr;
                int bestS = rs;
                double bestDist = Math.abs(rq - q) + Math.abs(rr - r) + Math.abs(rs - s);

                for (int i = -4; i <= 4; i++) {
                    for (int j = -4; j <= 4; j++) {
                        int k = -i - j;
                        double dist = Math.abs(i - q) + Math.abs(j - r) + Math.abs(k - s);
                        if (dist < bestDist - 0.0001) { // floating point tolerance
                            System.out.println("NOT NEAREST! q=" + q + " r=" + r + " s=" + s);
                            System.out.println("Algorithm returned: " + rq + ", " + rr + ", " + rs + " dist=" + bestDist);
                            System.out.println("Better hex: " + i + ", " + j + ", " + k + " dist=" + dist);
                            return;
                        }
                    }
                }
            }
        }
        System.out.println("Always picked nearest with Java Math.round.");
    }
}
