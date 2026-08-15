package com.novaempire.core.domain.theme

/**
 * Conversion d'une couleur hexadécimale de thème en entier ARGB.
 *
 * Volontairement sans dépendance Android : `android.graphics.Color.parseColor` **lève** sur une
 * chaîne invalide, et comme la conversion se faisait au moment du rendu, une coquille dans un JSON
 * écrit à la main faisait planter l'application en pleine composition. Ici l'échec est une valeur
 * (`null`), l'appelant décide quoi en faire — et le tout est testable sur la JVM.
 */
object HexColor {

    /**
     * Accepte `#AARRGGBB` et `#RRGGBB` (alpha implicite `FF`).
     * Retourne `null` pour toute autre forme, sans jamais lever.
     */
    fun parse(value: String): Int? {
        val text = value.trim()
        if (!text.startsWith("#")) return null

        val digits = text.substring(1)
        if (digits.length != 6 && digits.length != 8) return null
        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

        val raw = digits.toLong(16)
        return if (digits.length == 6) (0xFF000000L or raw).toInt() else raw.toInt()
    }

    /** `true` si [value] est une couleur de thème exploitable. */
    fun isValid(value: String): Boolean = parse(value) != null
}
