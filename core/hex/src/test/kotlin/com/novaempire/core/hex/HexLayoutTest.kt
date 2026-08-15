package com.novaempire.core.hex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexLayoutTest {

    private val radius = 60f
    private val viewW = 1080f
    private val viewH = 1920f
    private val eps = 0.01f

    @Test
    fun hexCentreRoundTripsBackToTheSameHex() {
        val coords = listOf(
            HexCoord(0, 0, 0), HexCoord(3, -1, -2), HexCoord(-4, 2, 2),
            HexCoord(12, -6, -6), HexCoord(-2, -3, 5)
        )
        for (coord in coords) {
            val x = HexLayout.centerXOf(coord, viewW / 2f, radius)
            val y = HexLayout.centerYOf(coord, viewH / 2f, radius)
            assertEquals(coord, HexLayout.hexAtLocal(x, y, viewW / 2f, viewH / 2f, radius))
        }
    }

    @Test
    fun hitTestingFollowsTheCamera() {
        val coord = HexCoord(2, -3, 1)
        val scale = 1.7f
        val panX = -140f
        val panY = 260f
        // Where the renderer puts the hex on screen, per the graphicsLayer transform.
        val localX = HexLayout.centerXOf(coord, viewW / 2f, radius)
        val localY = HexLayout.centerYOf(coord, viewH / 2f, radius)
        val screenX = viewW / 2f + (localX - viewW / 2f) * scale + panX
        val screenY = viewH / 2f + (localY - viewH / 2f) * scale + panY

        assertEquals(
            coord,
            HexLayout.hexAtScreen(screenX, screenY, viewW, viewH, panX, panY, scale, radius)
        )
    }

    @Test
    fun centeringPanPutsTheHexInTheMiddleOfTheScreen() {
        val coord = HexCoord(-5, 2, 3)
        val scale = 0.8f
        val panX = HexLayout.panToCenterX(coord, radius, scale)
        val panY = HexLayout.panToCenterY(coord, radius, scale)

        assertEquals(
            coord,
            HexLayout.hexAtScreen(viewW / 2f, viewH / 2f, viewW, viewH, panX, panY, scale, radius)
        )
    }

    @Test
    fun focalZoomKeepsTheHexUnderTheFingers() {
        val focusX = 300f
        val focusY = 1400f
        val panX = 55f
        val panY = -80f
        val oldScale = 1f
        val newScale = 2.4f

        val before = HexLayout.hexAtScreen(focusX, focusY, viewW, viewH, panX, panY, oldScale, radius)
        val zoomedPanX = HexLayout.focalPan(panX, focusX, viewW / 2f, oldScale, newScale)
        val zoomedPanY = HexLayout.focalPan(panY, focusY, viewH / 2f, oldScale, newScale)
        val after = HexLayout.hexAtScreen(focusX, focusY, viewW, viewH, zoomedPanX, zoomedPanY, newScale, radius)

        assertEquals(before, after)
    }

    @Test
    fun naiveZoomWithoutFocalCorrectionDoesNotKeepTheHex() {
        // Guards the fix: scaling without touching the pan (what the map used to do) slides the
        // board out from under the fingers as soon as they are away from the screen centre.
        val focusX = 300f
        val focusY = 1400f
        val before = HexLayout.hexAtScreen(focusX, focusY, viewW, viewH, 0f, 0f, 1f, radius)
        val after = HexLayout.hexAtScreen(focusX, focusY, viewW, viewH, 0f, 0f, 2.4f, radius)
        assertTrue("expected the naive zoom to drift", before != after)
    }

    @Test
    fun zoomingAboutTheScreenCentreLeavesThePanUntouched() {
        val panX = 12f
        assertEquals(panX, HexLayout.focalPan(panX, viewW / 2f + panX, viewW / 2f, 1f, 3f), eps)
    }

    @Test
    fun panClampKeepsPartOfTheBoardOnScreen() {
        val halfBoard = HexLayout.halfBoardWidth(radius, 12, 1f)
        val clamped = HexLayout.clampPanAxis(999_999f, halfBoard, viewW, 96f)

        assertEquals(halfBoard + viewW / 2f - 96f, clamped, eps)
        // The board's left edge is still inside the viewport, by the margin.
        val boardLeftEdgeOnScreen = viewW / 2f + clamped - halfBoard
        assertTrue(boardLeftEdgeOnScreen <= viewW - 96f + eps)
    }

    @Test
    fun panClampLeavesAReasonableViewUntouched() {
        val halfBoard = HexLayout.halfBoardWidth(radius, 5, 1f)
        assertEquals(0f, HexLayout.clampPanAxis(0f, halfBoard, viewW, 96f), eps)
        assertEquals(-50f, HexLayout.clampPanAxis(-50f, halfBoard, viewW, 96f), eps)
    }

    @Test
    fun spacingMatchesThePointyTopLayout() {
        assertEquals(103.923f, HexLayout.horizontalSpacing(60f), eps)
        assertEquals(90f, HexLayout.verticalSpacing(60f), eps)
    }
}
