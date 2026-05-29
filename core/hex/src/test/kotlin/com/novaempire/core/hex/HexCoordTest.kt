package com.novaempire.core.hex

import org.junit.Assert.assertEquals
import org.junit.Test

class HexCoordTest {

    @Test
    fun testInitialization() {
        val coord = HexCoord(1, -1, 0)
        assertEquals(1, coord.q)
        assertEquals(-1, coord.r)
        assertEquals(0, coord.s)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidInitialization() {
        HexCoord(1, 1, 1) // q + r + s != 0
    }

    @Test
    fun testAddition() {
        val a = HexCoord(1, -1, 0)
        val b = HexCoord(0, 1, -1)
        val result = a + b
        assertEquals(HexCoord(1, 0, -1), result)
    }

    @Test
    fun testRoundExact() {
        assertEquals(HexCoord(1, -1, 0), HexCoord.round(1.0, -1.0, 0.0))
    }

    @Test
    fun testRoundFractional() {
        // Tie broken by largest diff component
        assertEquals(HexCoord(1, -1, 0), HexCoord.round(0.6, -0.9, 0.3))
    }

    @Test
    fun testDistance() {
        val a = HexCoord(0, 0, 0)
        val b = HexCoord(2, -1, -1)
        assertEquals(2, a.distanceTo(b))
    }
}
