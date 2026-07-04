public class TestRoundingBug {
    public static void main(String[] args) {
        System.out.println(Math.round(0.5)); // Java: 1
        System.out.println(Math.round(-0.5)); // Java: 0

        // If fractional coordinates have a .5 component and a negative .5 component
        // Java's round rounds towards positive infinity for half-way values
        double q = 0.5;
        double r = -0.5;
        double s = 0.0;

        int rq = (int)Math.round(q); // 1
        int rr = (int)Math.round(r); // 0
        int rs = (int)Math.round(s); // 0

        System.out.println("Java rounded: " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs)); // 1, 0, 0 sum=1

        double qDiff = Math.abs(rq - q); // 0.5
        double rDiff = Math.abs(rr - r); // 0.5
        double sDiff = Math.abs(rs - s); // 0.0

        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs;
        } else if (rDiff > sDiff) {
            rr = -rq - rs;
        } else {
            rs = -rq - rr;
        }

        System.out.println("After adjustment: " + rq + ", " + rr + ", " + rs + " sum=" + (rq+rr+rs)); // sum is 0
    }
}
