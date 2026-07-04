public class MathRound2 {
    public static void main(String[] args) {
        // Redblobgames note:
        // Math.round() rounds half-way values like 0.5 to positive infinity.
        // It means Math.round(-0.5) is 0 and Math.round(0.5) is 1.
        // This asymmetry breaks the invariant that q+r+s = 0 in some cases!

        // Wait, did I find a case where q+r+s != 0? I did earlier!
        double q = 0.5;
        double r = -0.5;
        double s = 0.0;

        int rq = (int)Math.round(q); // 1
        int rr = (int)Math.round(r); // 0
        int rs = (int)Math.round(s); // 0

        double qDiff = Math.abs(rq - q); // 0.5
        double rDiff = Math.abs(rr - r); // 0.5
        double sDiff = Math.abs(rs - s); // 0.0

        System.out.println("Diffs: " + qDiff + ", " + rDiff + ", " + sDiff);

        if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
        else if (rDiff > sDiff) rr = -rq - rs;
        else rs = -rq - rr;

        System.out.println(rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs));

        System.out.println("Wait! In Java Math.round returns Long! So we cast it to int.");
        // (int)Math.round(0.5) -> 1
        // (int)Math.round(-0.5) -> 0
        // (int)Math.round(0.0) -> 0

        // rq = 1, rr = 0, rs = 0
        // qDiff = |1 - 0.5| = 0.5
        // rDiff = |0 - (-0.5)| = 0.5
        // sDiff = |0 - 0.0| = 0.0

        // qDiff > rDiff? 0.5 > 0.5 is false.
        // rDiff > sDiff? 0.5 > 0.0 is true.
        // => rr = -rq - rs = -1 - 0 = -1

        // So rq=1, rr=-1, rs=0. Sum is 0.
        // It didn't break!
    }
}
