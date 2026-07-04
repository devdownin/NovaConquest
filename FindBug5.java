public class FindBug5 {
    public static void main(String[] args) {
        // Redblobgames hex rounding uses kotlin.math.round or java.lang.Math.round
        // A bug was submitted: "Audit code rondins bug".
        // It could just mean "I noticed you're using Math.round and it might be buggy, please check"
        // OR it's actually causing a crash somewhere.
        // Wait, did we find an Exception when we ran `./gradlew :core:hex:test :core:domain:test :core:engine:test` ?
        // No, tests passed.
        // But what if it's the `pixelToHex` function that is causing an issue?

        System.out.println("Wait, I found something earlier: TacticalMapScreen has a hexRound method.");
        // TacticalMapScreen.kt has its own `hexRound` function.
        // Is it exactly the same as HexCoord.Companion.round?
    }
}
