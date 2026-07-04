public class TestRounding2 {
    public static void main(String[] args) {
        System.out.println("HexCoord Rounding");
        double fracQ = -0.4;
        double fracR = 0.8;
        double fracS = -0.4;
        int q = (int)Math.round(fracQ);
        int r = (int)Math.round(fracR);
        int s = (int)Math.round(fracS);

        System.out.println("Initial round: " + q + ", " + r + ", " + s + " sum: " + (q+r+s));

        double qDiff = Math.abs(q - fracQ);
        double rDiff = Math.abs(r - fracR);
        double sDiff = Math.abs(s - fracS);

        if (qDiff > rDiff && qDiff > sDiff) q = -r - s;
        else if (rDiff > sDiff) r = -q - s;
        else s = -q - r;

        System.out.println("Adjusted round: " + q + ", " + r + ", " + s + " sum: " + (q+r+s));
    }
}
