public class test_java_round {
    public static void main(String[] args) {
        double fracQ = -0.5;
        double fracR = 1.0;
        double fracS = -0.5;

        int jq = (int)Math.round(fracQ);
        int jr = (int)Math.round(fracR);
        int js = (int)Math.round(fracS);

        double jqDiff = Math.abs(jq - fracQ);
        double jrDiff = Math.abs(jr - fracR);
        double jsDiff = Math.abs(js - fracS);

        if (jqDiff > jrDiff && jqDiff > jsDiff) jq = -jr - js;
        else if (jrDiff > jsDiff) jr = -jq - js;
        else js = -jq - jr;

        System.out.println(jq + ", " + jr + ", " + js + " sum=" + (jq+jr+js));
    }
}
