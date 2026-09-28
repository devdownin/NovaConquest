package com.novaempire.app.ui.screens

import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.map.awakeningAccentVisible
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPresentationTest {
    @Test
    fun measuredWidthsSelectThreeUsableCompositions() {
        assertEquals(MapWindowClass.COMPACT, mapWindowClass(399.dp))
        assertEquals(MapWindowClass.MEDIUM, mapWindowClass(400.dp))
        assertEquals(MapWindowClass.MEDIUM, mapWindowClass(839.dp))
        assertEquals(MapWindowClass.EXPANDED, mapWindowClass(840.dp))
    }

    @Test
    fun awakeningMarksCannotExposeUnexploredOrOtherMissions() {
        val corridor = HexCoord(0, -3, 3)
        val storm = HexCoord(2, 1, -3)
        assertFalse(awakeningAccentVisible("mission_1", corridor, emptySet()))
        assertFalse(awakeningAccentVisible("mission_2", corridor, setOf(corridor)))
        assertTrue(awakeningAccentVisible("mission_1", corridor, setOf(corridor)))
        assertTrue(awakeningAccentVisible("mission_1", storm, setOf(storm)))
        assertFalse(awakeningAccentVisible("mission_1", HexCoord(3, 0, -3), setOf(HexCoord(3, 0, -3))))
    }
}
