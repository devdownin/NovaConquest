package com.novaempire.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.novaempire.app.ui.screens.getFactionColor
import com.novaempire.app.ui.theme.MapPalette
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.TerrainType

/** Three readable representations of the same terrain, selected from the camera zoom. */
internal enum class MapDetailLevel { OVERVIEW, STANDARD, CLOSE }

internal fun mapDetailLevel(scale: Float): MapDetailLevel = when {
    scale < 0.7f -> MapDetailLevel.OVERVIEW
    scale >= 1.5f -> MapDetailLevel.CLOSE
    else -> MapDetailLevel.STANDARD
}

/** Vector emblems remain recognizable when a whole sector is only a few screen pixels wide. */
internal fun DrawScope.drawTerrainEmblem(
    x: Float,
    y: Float,
    radius: Float,
    terrain: TerrainType,
    owner: Faction?,
    palette: MapPalette,
    alpha: Float
) {
    val center = Offset(x, y)
    val ink = palette.ink.copy(alpha = alpha)
    val base = when (terrain) {
        TerrainType.PLANET -> owner?.let { getFactionColor(it) } ?: palette.planet
        else -> palette.terrainColor(terrain)
    }
    // Terrain fills are deliberately dark; an overview emblem must remain legible above them.
    val color = lerp(base, Color.White, 0.55f).copy(alpha = alpha)
    when (terrain) {
        TerrainType.PLANET -> {
            drawCircle(ink, radius * 0.42f, center)
            drawCircle(color, radius * 0.34f, center)
            drawCircle(Color.White.copy(alpha = 0.5f * alpha), radius * 0.06f,
                Offset(x - radius * 0.12f, y - radius * 0.13f))
        }
        TerrainType.ASTEROIDS -> {
            val rock = Path().apply {
                moveTo(x - radius * 0.4f, y + radius * 0.2f)
                lineTo(x - radius * 0.24f, y - radius * 0.32f)
                lineTo(x + radius * 0.14f, y - radius * 0.38f)
                lineTo(x + radius * 0.43f, y)
                lineTo(x + radius * 0.22f, y + radius * 0.37f)
                close()
            }
            drawPath(rock, color)
            drawPath(rock, ink, style = Stroke(width = radius * 0.06f))
        }
        TerrainType.NEBULA, TerrainType.PLASMA_CLOUD, TerrainType.ION_STORM -> {
            drawCircle(color.copy(alpha = 0.42f * alpha), radius * 0.45f,
                Offset(x - radius * 0.12f, y - radius * 0.04f))
            drawCircle(color, radius * 0.23f, Offset(x + radius * 0.18f, y + radius * 0.1f))
        }
        TerrainType.BLACK_HOLE -> {
            drawCircle(color, radius * 0.43f, center, style = Stroke(width = radius * 0.11f))
            drawCircle(ink, radius * 0.31f, center)
        }
        TerrainType.WORMHOLE -> {
            drawOval(color, Offset(x - radius * 0.45f, y - radius * 0.2f),
                Size(radius * 0.9f, radius * 0.4f), style = Stroke(width = radius * 0.09f))
            drawCircle(color, radius * 0.1f, center)
        }
        TerrainType.ANOMALY -> {
            drawLine(color, Offset(x, y - radius * 0.39f), Offset(x, y + radius * 0.39f),
                strokeWidth = radius * 0.12f)
            drawCircle(color, radius * 0.07f, Offset(x, y + radius * 0.48f))
        }
        TerrainType.EMPTY -> Unit
    }
}

/** Fine cartographic engravings appear only when there is enough room to read them. */
internal fun DrawScope.drawTerrainEngraving(
    x: Float,
    y: Float,
    radius: Float,
    terrain: TerrainType,
    palette: MapPalette,
    alpha: Float
) {
    val light = Color.White.copy(alpha = 0.25f * alpha)
    val stroke = radius * 0.025f
    when (terrain) {
        TerrainType.PLANET -> {
            drawArc(light, 205f, 115f, false, Offset(x - radius * 0.36f, y - radius * 0.29f),
                Size(radius * 0.72f, radius * 0.58f), style = Stroke(width = stroke))
            drawCircle(light, radius * 0.025f, Offset(x + radius * 0.2f, y - radius * 0.14f))
        }
        TerrainType.ASTEROIDS -> {
            drawLine(light, Offset(x - radius * 0.37f, y - radius * 0.12f),
                Offset(x - radius * 0.16f, y - radius * 0.23f), strokeWidth = stroke)
            drawLine(light, Offset(x + radius * 0.12f, y + radius * 0.23f),
                Offset(x + radius * 0.28f, y + radius * 0.14f), strokeWidth = stroke)
        }
        TerrainType.NEBULA -> {
            drawArc(palette.nebulaHaze.copy(alpha = 0.5f * alpha), 195f, 125f, false,
                Offset(x - radius * 0.43f, y - radius * 0.35f),
                Size(radius * 0.7f, radius * 0.7f), style = Stroke(width = stroke * 2f))
        }
        TerrainType.BLACK_HOLE -> {
            drawArc(light, 15f, 105f, false, Offset(x - radius * 0.48f, y - radius * 0.18f),
                Size(radius * 0.96f, radius * 0.36f), style = Stroke(width = stroke))
        }
        TerrainType.WORMHOLE -> {
            drawArc(light, 200f, 125f, false, Offset(x - radius * 0.36f, y - radius * 0.36f),
                Size(radius * 0.72f, radius * 0.72f), style = Stroke(width = stroke))
        }
        else -> Unit
    }
}
