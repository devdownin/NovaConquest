public class FindBug {
    public static void main(String[] args) {
        // Redblobgames says:
        // function hex_round(frac):
        //     var q = Math.round(frac.q)
        //     var r = Math.round(frac.r)
        //     var s = Math.round(frac.s)
        //     var q_diff = Math.abs(q - frac.q)
        //     var r_diff = Math.abs(r - frac.r)
        //     var s_diff = Math.abs(s - frac.s)
        //     if q_diff > r_diff and q_diff > s_diff:
        //         q = -r - s
        //     else if r_diff > s_diff:
        //         r = -q - s
        //     else:
        //         s = -q - r
        //     return Hex(q, r, s)

        // However, there is a known bug in Kotlin/Java with this implementation
        // if Math.round is used because it rounds x.5 to positive infinity.
        // E.g. Math.round(-0.5) is 0, Math.round(0.5) is 1.
        // Let's find an input where q + r + s != 0

        for (double x = -10.0; x <= 10.0; x += 0.1) {
            for (double y = -10.0; y <= 10.0; y += 0.1) {
                for (double z = -10.0; z <= 10.0; z += 0.1) {
                    if (Math.abs(x + y + z) > 0.0001) continue;

                    int q = (int)Math.round(x);
                    int r = (int)Math.round(y);
                    int s = (int)Math.round(z);

                    double q_diff = Math.abs(q - x);
                    double r_diff = Math.abs(r - y);
                    double s_diff = Math.abs(s - z);

                    if (q_diff > r_diff && q_diff > s_diff) q = -r - s;
                    else if (r_diff > s_diff) r = -q - s;
                    else s = -q - r;

                    if (q + r + s != 0) {
                        System.out.println("Found bug: " + x + ", " + y + ", " + z);
                        return;
                    }
                }
            }
        }
        System.out.println("No bug found via direct Math.round and diff comparison.");
    }
}
