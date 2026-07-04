public class TestHexRound {
    public static void main(String[] args) {
        double q = 0.5;
        double r = 0.5;
        double s = -1.0;

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
            System.out.println("Failed on " + q + ", " + r + ", " + s + " => " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs));
        }

        q = -0.5;
        r = -0.5;
        s = 1.0;

        rq = (int)Math.round(q);
        rr = (int)Math.round(r);
        rs = (int)Math.round(s);
        qDiff = Math.abs(rq - q);
        rDiff = Math.abs(rr - r);
        sDiff = Math.abs(rs - s);

        if (qDiff > rDiff && qDiff > sDiff) rq = -rr - rs;
        else if (rDiff > sDiff) rr = -rq - rs;
        else rs = -rq - rr;

        if (rq + rr + rs != 0) {
            System.out.println("Failed on " + q + ", " + r + ", " + s + " => " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs));
        }
    }
}
