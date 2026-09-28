package com.novaempire.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.screens.getFactionColor
import com.novaempire.app.ui.theme.MapPalette
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import kotlin.math.cos
import kotlin.math.sin

/** Seven distinct silhouettes; color is an accent, never the sole faction cue. */
fun DrawScope.drawFactionEmblem(center: Offset, radius: Float, faction: Faction, color: Color) {
    val x = center.x
    val y = center.y
    val line = (radius * 0.21f).coerceAtLeast(1.1f)
    fun segment(ax: Float, ay: Float, bx: Float, by: Float) {
        drawLine(color, Offset(x + ax * radius, y + ay * radius),
            Offset(x + bx * radius, y + by * radius), strokeWidth = line)
    }
    when (faction) {
        Faction.DOMINION -> {
            segment(-0.7f, -0.6f, 0f, 0.65f)
            segment(0.7f, -0.6f, 0f, 0.65f)
            segment(-0.4f, -0.18f, 0.4f, -0.18f)
        }
        Faction.TRADERS -> {
            val diamond = Path().apply {
                moveTo(x, y - radius * 0.85f)
                lineTo(x + radius * 0.75f, y)
                lineTo(x, y + radius * 0.85f)
                lineTo(x - radius * 0.75f, y)
                close()
            }
            drawPath(diamond, color, style = Stroke(width = line))
            segment(-0.42f, 0f, 0.42f, 0f)
        }
        Faction.SYNTH -> {
            drawRect(color, Offset(x - radius * 0.73f, y - radius * 0.73f),
                Size(radius * 1.46f, radius * 1.46f), style = Stroke(width = line))
            drawRect(color, Offset(x - radius * 0.28f, y - radius * 0.28f),
                Size(radius * 0.56f, radius * 0.56f))
        }
        Faction.NOMADS -> {
            drawArc(color, 205f, 250f, false, Offset(x - radius * 0.8f, y - radius * 0.8f),
                Size(radius * 1.6f, radius * 1.6f), style = Stroke(width = line))
            drawCircle(color, radius * 0.15f, Offset(x + radius * 0.48f, y - radius * 0.5f))
        }
        Faction.KAELEN -> {
            segment(0f, -0.88f, 0f, 0.78f)
            segment(-0.7f, 0.45f, 0f, -0.25f)
            segment(0.7f, 0.45f, 0f, -0.25f)
            drawCircle(color, radius * 0.14f, Offset(x, y - radius * 0.3f))
        }
        Faction.XYLAR -> {
            for (dx in listOf(-0.55f, 0f, 0.55f)) {
                segment(dx - 0.16f, -0.65f, dx + 0.1f, 0.45f)
            }
            segment(-0.68f, 0.65f, 0.68f, 0.65f)
        }
        Faction.ANCIENT_NPC -> {
            drawCircle(color, radius * 0.7f, center, style = Stroke(width = line))
            for (i in 0 until 4) {
                val angle = i * kotlin.math.PI.toFloat() / 2f
                drawCircle(color, radius * 0.12f,
                    Offset(x + cos(angle) * radius * 0.7f, y + sin(angle) * radius * 0.7f))
            }
        }
    }
}

/** Keep the faction mark legible on every ship silhouette, including during motion. */
fun DrawScope.drawIdentifiedUnit(x: Float, y: Float, unit: GameUnit, hexRadius: Float, palette: MapPalette) {
    drawUnit(x, y, unit, hexRadius, palette)
    val markCenter = Offset(x, y)
    drawCircle(palette.ink, hexRadius * 0.19f, markCenter)
    drawFactionEmblem(markCenter, hexRadius * 0.14f, unit.faction, Color.White)
}

@Composable
fun FactionBadge(faction: Faction, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier.semantics { contentDescription = "Emblème ${faction.displayName}" }) {
        drawFactionEmblem(center, size.minDimension * 0.36f, faction, getFactionColor(faction))
    }
}
