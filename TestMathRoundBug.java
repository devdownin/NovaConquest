public class TestMathRoundBug {
    public static void main(String[] args) {
        // Redblobgames hex rounding algorithm:
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

        // This is exactly what the code has.
        // Wait, Redblobgames says:
        // var q = Math.round(frac.q)
        // If frac.q = 0.5, Math.round(0.5) in JS is 1
        // If frac.r = -0.5, Math.round(-0.5) in JS is -0 (i.e. 0)

        // Let's check kotlin code again.

        double fracQ = 0.5;
        double fracR = 0.5;
        double fracS = -1.0;

        System.out.println((int)Math.round(fracQ));
    }
}
