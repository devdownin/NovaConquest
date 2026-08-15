package com.novaempire.core.hex

import kotlin.math.sqrt

/**
 * Screen geometry for a pointy-top hex board, including the inverse of the pan/zoom camera the
 * tactical map draws through.
 *
 * This lives in `:core:hex` rather than in the Compose screen for two reasons. It is the same
 * layout maths the renderer already needed — the screen carried its own private copy of the cube
 * rounding step, duplicating [HexCoord.round] — and it is the part of the camera most easily got
 * wrong, so keeping it Android-free puts it under the JVM test suite CI actually runs.
 *
 * The camera model matches Compose's centre-anchored `graphicsLayer`:
 *
 * ```
 * screen = viewCentre + (local - viewCentre) * scale + pan
 * ```
 *
 * `local` is the untransformed drawing plane (where [centerXOf] / [centerYOf] place a hex),
 * `pan` is expressed in post-transform screen pixels.
 */
object HexLayout {

    /** Horizontal distance between the centres of two side-by-side hexes. */
    fun horizontalSpacing(hexRadius: Float): Float = sqrt(3f) * hexRadius

    /** Vertical distance between the centres of two stacked hex rows. */
    fun verticalSpacing(hexRadius: Float): Float = 1.5f * hexRadius

    /** Local-plane X of [coord]'s centre, for a board centred on [viewCenterX]. */
    fun centerXOf(coord: HexCoord, viewCenterX: Float, hexRadius: Float): Float =
        viewCenterX + horizontalSpacing(hexRadius) * (coord.q + coord.r / 2f)

    /** Local-plane Y of [coord]'s centre, for a board centred on [viewCenterY]. */
    fun centerYOf(coord: HexCoord, viewCenterY: Float, hexRadius: Float): Float =
        viewCenterY + verticalSpacing(hexRadius) * coord.r

    /** The hex containing local-plane point ([x], [y]). Inverse of [centerXOf] / [centerYOf]. */
    fun hexAtLocal(x: Float, y: Float, viewCenterX: Float, viewCenterY: Float, hexRadius: Float): HexCoord {
        val q = (sqrt(3f) / 3f * (x - viewCenterX) - 1f / 3f * (y - viewCenterY)) / hexRadius
        val r = (2f / 3f * (y - viewCenterY)) / hexRadius
        return HexCoord.round(q.toDouble(), r.toDouble(), -q.toDouble() - r.toDouble())
    }

    /** Undoes the camera on one axis: screen pixel → local-plane pixel. */
    fun screenToLocal(screenValue: Float, viewCenter: Float, pan: Float, scale: Float): Float =
        viewCenter + (screenValue - viewCenter - pan) / scale

    /** Applies the camera on one axis: local-plane pixel → screen pixel. Inverse of [screenToLocal]. */
    fun localToScreen(localValue: Float, viewCenter: Float, pan: Float, scale: Float): Float =
        viewCenter + (localValue - viewCenter) * scale + pan

    /**
     * Whether [coord] sits inside the middle [comfortFraction] of the viewport.
     *
     * Used to decide whether a keyboard / D-pad cursor needs the camera to follow it. Recentring
     * on every key press makes the board lurch under a sighted keyboard user; never recentring
     * lets the cursor walk off screen, which strands anyone who cannot pan by touch. Following
     * only once the cursor leaves the comfortable middle gives both a stable board.
     */
    fun isComfortablyVisible(
        coord: HexCoord,
        viewWidth: Float,
        viewHeight: Float,
        panX: Float,
        panY: Float,
        scale: Float,
        hexRadius: Float,
        comfortFraction: Float = 0.7f
    ): Boolean {
        if (viewWidth <= 0f || viewHeight <= 0f) return true
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f
        val screenX = localToScreen(centerXOf(coord, cx, hexRadius), cx, panX, scale)
        val screenY = localToScreen(centerYOf(coord, cy, hexRadius), cy, panY, scale)
        val marginX = viewWidth * (1f - comfortFraction) / 2f
        val marginY = viewHeight * (1f - comfortFraction) / 2f
        return screenX >= marginX && screenX <= viewWidth - marginX &&
            screenY >= marginY && screenY <= viewHeight - marginY
    }

    /** The hex under a screen-space point, camera included. */
    fun hexAtScreen(
        screenX: Float,
        screenY: Float,
        viewWidth: Float,
        viewHeight: Float,
        panX: Float,
        panY: Float,
        scale: Float,
        hexRadius: Float
    ): HexCoord {
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f
        return hexAtLocal(
            screenToLocal(screenX, cx, panX, scale),
            screenToLocal(screenY, cy, panY, scale),
            cx, cy, hexRadius
        )
    }

    /** Pan that centres [coord] on screen at [scale]. */
    fun panToCenterX(coord: HexCoord, hexRadius: Float, scale: Float): Float =
        -horizontalSpacing(hexRadius) * (coord.q + coord.r / 2f) * scale

    /** Pan that centres [coord] on screen at [scale]. */
    fun panToCenterY(coord: HexCoord, hexRadius: Float, scale: Float): Float =
        -verticalSpacing(hexRadius) * coord.r * scale

    /**
     * New pan on one axis so the board point currently under [focus] stays under it while the
     * zoom goes from [oldScale] to [newScale] — i.e. a pinch anchored on the fingers rather than
     * on the middle of the screen.
     */
    fun focalPan(pan: Float, focus: Float, viewCenter: Float, oldScale: Float, newScale: Float): Float {
        if (oldScale <= 0f) return pan
        return pan + (focus - viewCenter - pan) * (1f - newScale / oldScale)
    }

    /**
     * Half the on-screen width of a radius-[mapRadius] board. The extreme hex of a hex-shaped
     * board sits at `|q - s| / 2 == mapRadius` columns from the centre.
     */
    fun halfBoardWidth(hexRadius: Float, mapRadius: Int, scale: Float): Float =
        horizontalSpacing(hexRadius) * mapRadius * scale

    /** Half the on-screen height of a radius-[mapRadius] board (`|r| == mapRadius` rows). */
    fun halfBoardHeight(hexRadius: Float, mapRadius: Int, scale: Float): Float =
        verticalSpacing(hexRadius) * mapRadius * scale

    /**
     * Clamps a pan axis so the board keeps at least [margin] pixels of overlap with the viewport.
     * Without it a single fling leaves the player looking at empty space with no cue as to which
     * way the galaxy went.
     */
    fun clampPanAxis(pan: Float, halfBoard: Float, viewSize: Float, margin: Float): Float {
        val limit = (halfBoard + viewSize / 2f - margin).coerceAtLeast(0f)
        return pan.coerceIn(-limit, limit)
    }
}
