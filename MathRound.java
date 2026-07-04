public class MathRound {
    public static void main(String[] args) {
        System.out.println(Math.round(Double.NaN)); // 0
        System.out.println(Math.round(Double.POSITIVE_INFINITY)); // 9223372036854775807 (Long.MAX_VALUE)
        System.out.println(Math.round(Double.NEGATIVE_INFINITY)); // -9223372036854775808 (Long.MIN_VALUE)
    }
}
