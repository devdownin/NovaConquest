package com.novaempire.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.novaempire.app.ui.theme.MapPalette
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.hex.HexCoord

private val supplyCorridor = setOf(
    HexCoord(0, -4, 4), HexCoord(0, -3, 3), HexCoord(0, -2, 2), HexCoord(0, -1, 1),
    HexCoord(1, -3, 2), HexCoord(1, -2, 1), HexCoord(1, -1, 0),
    HexCoord(2, -2, 0), HexCoord(2, -1, -1)
)
private val stormFront = HexCoord(2, 1, -3)

/** Narrative marks must obey the same exploration boundary as the terrain below them. */
fun awakeningAccentVisible(missionId: String?, coord: HexCoord, explored: Set<HexCoord>): Boolean =
    missionId == "mission_1" && coord in explored && (coord in supplyCorridor || coord == stormFront)

fun DrawScope.drawAwakeningAccent(
    tile: HexTile,
    center: Offset,
    hexRadius: Float,
    palette: MapPalette,
    alpha: Float,
    stormActive: Boolean
) {
    val x = center.x
    val y = center.y
    if (tile.coord in supplyCorridor) {
        val ink = if (tile.owner == Faction.DOMINION) palette.planet else Color(0xFFB7B19B)
        // Broken rails leave the terrain and selection outlines readable.
        for (side in listOf(-1f, 1f)) {
            drawLine(ink.copy(alpha = 0.55f * alpha),
                Offset(x - hexRadius * 0.68f, y + side * hexRadius * 0.37f),
                Offset(x - hexRadius * 0.26f, y + side * hexRadius * 0.37f),
                strokeWidth = (hexRadius * 0.035f).coerceAtLeast(1f))
        }
        if (tile.terrain == TerrainType.PLANET && tile.owner == Faction.DOMINION) {
            drawArc(palette.planet.copy(alpha = 0.75f * alpha), 198f, 144f, false,
                Offset(x - hexRadius * 0.42f, y - hexRadius * 0.42f),
                Size(hexRadius * 0.84f, hexRadius * 0.84f),
                style = Stroke(width = (hexRadius * 0.035f).coerceAtLeast(1f)))
        }
    }
    if (tile.coord == stormFront) {
        val frontColor = if (stormActive) Color(0xFFFFAB55) else palette.ionStorm
        // Concentric broken rings intensify when the scripted turn-eight storm is active.
        drawArc(frontColor.copy(alpha = (if (stormActive) 0.95f else 0.45f) * alpha),
            35f, 110f, false, Offset(x - hexRadius * 0.58f, y - hexRadius * 0.58f),
            Size(hexRadius * 1.16f, hexRadius * 1.16f),
            style = Stroke(width = (hexRadius * 0.04f).coerceAtLeast(1f)))
        drawArc(frontColor.copy(alpha = 0.6f * alpha), 215f, 88f, false,
            Offset(x - hexRadius * 0.58f, y - hexRadius * 0.58f),
            Size(hexRadius * 1.16f, hexRadius * 1.16f),
            style = Stroke(width = (hexRadius * 0.04f).coerceAtLeast(1f)))
    }
}

/** Shown only on a fleet that already passed the map's visibility filter. */
fun DrawScope.drawAwakeningXylarContact(center: Offset, hexRadius: Float) {
    val ink = Color(0xFFD9B5D8)
    val h = hexRadius * 0.5f
    drawLine(ink, Offset(center.x - h, center.y - h), Offset(center.x - h * 0.7f, center.y),
        strokeWidth = 2f)
    drawLine(ink, Offset(center.x - h * 0.7f, center.y), Offset(center.x - h, center.y + h),
        strokeWidth = 2f)
    drawLine(ink, Offset(center.x + h, center.y - h), Offset(center.x + h * 0.7f, center.y),
        strokeWidth = 2f)
    drawLine(ink, Offset(center.x + h * 0.7f, center.y), Offset(center.x + h, center.y + h),
        strokeWidth = 2f)
}
