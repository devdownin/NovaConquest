public class TestFloatingPoint {
    public static void main(String[] args) {
        // Let's test what happens when qDiff == rDiff or similar.
        double fracQ = 0.5;
        double fracR = 0.5;
        double fracS = -1.0;

        int jq = (int)Math.round(fracQ); // 1
        int jr = (int)Math.round(fracR); // 1
        int js = (int)Math.round(fracS); // -1

        System.out.println("Initial rounded: " + jq + ", " + jr + ", " + js + " sum=" + (jq+jr+js));

        double jqDiff = Math.abs(jq - fracQ); // 0.5
        double jrDiff = Math.abs(jr - fracR); // 0.5
        double jsDiff = Math.abs(js - fracS); // 0.0

        if (jqDiff > jrDiff && jqDiff > jsDiff) jq = -jr - js;
        else if (jrDiff > jsDiff) jr = -jq - js;
        else js = -jq - jr;

        System.out.println("Final: " + jq + ", " + jr + ", " + js + " sum=" + (jq+jr+js));
    }
}
