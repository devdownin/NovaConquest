package com.novaempire.core.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexColorTest {

    @Test
    fun `parse une couleur ARGB complete`() {
        assertEquals(0xFF4A7B9D.toInt(), HexColor.parse("#FF4A7B9D"))
        assertEquals(0x00000000, HexColor.parse("#00000000"))
        assertEquals(0xFFFFFFFF.toInt(), HexColor.parse("#FFFFFFFF"))
    }

    @Test
    fun `une couleur RGB recoit un alpha opaque`() {
        assertEquals(0xFF4A7B9D.toInt(), HexColor.parse("#4A7B9D"))
    }

    @Test
    fun `la casse des chiffres hexadecimaux est indifferente`() {
        assertEquals(HexColor.parse("#FF4A7B9D"), HexColor.parse("#ff4a7b9d"))
    }

    /**
     * Le cœur du problème : ces entrées faisaient auparavant lever
     * `android.graphics.Color.parseColor` en pleine composition, donc planter l'application.
     */
    @Test
    fun `une couleur invalide retourne null au lieu de lever`() {
        assertNull(HexColor.parse("#FG4A7B9D"))   // 'G' n'est pas hexadécimal
        assertNull(HexColor.parse("FF4A7B9D"))    // pas de '#'
        assertNull(HexColor.parse("#FF4A7B9"))    // 7 chiffres
        assertNull(HexColor.parse("#FF4A7B9DD"))  // 9 chiffres
        assertNull(HexColor.parse(""))
        assertNull(HexColor.parse("#"))
        assertNull(HexColor.parse("rouge"))
    }

    @Test
    fun `les espaces autour de la valeur sont tolérés`() {
        assertEquals(0xFF130F0A.toInt(), HexColor.parse("  #FF130F0A  "))
    }

    @Test
    fun `isValid reflete parse`() {
        assertTrue(HexColor.isValid("#FF130F0A"))
        assertFalse(HexColor.isValid("#nope"))
    }
}
