public class TestDiff {
    public static void main(String[] args) {
        int q = -1;
        double fracQ = -0.6;
        double diff = Math.abs(q - fracQ);
        System.out.println("Math.abs(" + q + " - " + fracQ + ") = " + diff);
    }
}
