public class TestRoundingBug3 {
    public static void main(String[] args) {
        // Find inputs that result in sum != 0 after adjustment, using Kotlin's Math.round which is java.lang.Math.round
        // But what if `Math.round(Double.NaN)`?
        double q = Double.NaN;
        System.out.println(Math.round(q)); // 0
    }
}
