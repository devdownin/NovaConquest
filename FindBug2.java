public class FindBug2 {
    public static void main(String[] args) {
        // Redblobgames says:
        // When using Math.round, the coordinate x.5 is rounded up.
        // If two coordinates are exactly .5, we can have problems.
        double q = 0.5;
        double r = 0.5;
        double s = -1.0;

        int rq = (int)Math.round(q); // 1
        int rr = (int)Math.round(r); // 1
        int rs = (int)Math.round(s); // -1

        double qDiff = Math.abs(rq - q); // 0.5
        double rDiff = Math.abs(rr - r); // 0.5
        double sDiff = Math.abs(rs - s); // 0.0

        if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
        else if (rDiff > sDiff) rr = -rq - rs;
        else rs = -rq - rr;

        // This is exactly the code. It results in rq=1, rr=1, rs=-1 initially.
        // Diff for q and r is 0.5. s is 0.0.
        // (qDiff > rDiff && qDiff > sDiff) => 0.5 > 0.5 is FALSE
        // (rDiff > sDiff) => 0.5 > 0.0 is TRUE => rr = -1 - (-1) = 0.
        // Result is 1, 0, -1. sum is 0.

        // What about kotlin math round instead of java math round?
        System.out.println("No sum bug yet. Wait... the prompt says 'Audit code rondins bug'");
        // In French: 'arrondi' means 'rounding'. Maybe there's a bug in how `Math.round` is used?
    }
}
