public class TestRoundingKotlin {
    public static void main(String[] args) {
        // Red Blob Games article explicitly mentions Kotlin's Math.round
        // "Math.round() in Kotlin uses round-to-even" -> no, kotlin.math.round does.
        // java.lang.Math.round rounds half-up.
        // Let's replace the script to test round-to-even.
        System.out.println("Nothing broken about Math.round algorithm itself.");
    }
}
