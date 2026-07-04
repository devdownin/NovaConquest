public class FindBug6 {
    public static void main(String[] args) {
        // Kotlin Math.round returns Long. We cast to Int.
        // Wait, is Kotlin Math.round standard kotlin.math.round?
        // TacticalMapScreen has `import kotlin.math.cos` `import kotlin.math.sin` `import kotlin.math.sqrt`
        // But NO `import kotlin.math.round`.
        // HexCoord has `import kotlin.math.abs`. NO `import kotlin.math.round`.

        // This means `Math.round` resolves to `java.lang.Math.round` in both files!
        // Does java.lang.Math.round have a bug?
        // According to Java documentation:
        // "Returns the closest long to the argument, with ties rounding to positive infinity."

        // So for half-way values like 0.5 it rounds to 1, -0.5 rounds to 0.
        // We ALREADY TESTED THIS. And our tests showed that the fixup algorithm actually perfectly handles this asymmetry, producing a valid hex where q+r+s=0.
        // Wait! Look at Red Blob Games:
        // https://www.redblobgames.com/grids/hexagons/#rounding

        // In kotlin, standard Kotlin standard library `kotlin.math.round` uses "round to even"!
        // If someone uses `kotlin.math.round`, half values round to the nearest even number.

        // What if `kotlin.math.round` is what they meant?
        // "Audit code rondins bug"
        // In French: "Arrondi" means rounding.
        // Wait, is there a bug with the *formula*?
        // let's look at kotlin.math.round implementation.
    }
}
