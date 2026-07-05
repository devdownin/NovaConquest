package com.novaempire.app.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import com.novaempire.core.domain.models.Faction
import kotlin.math.cos
import kotlin.math.sin

object FactionCosmetics {
    fun drawFactionEmblem(drawScope: DrawScope, x: Float, y: Float, radius: Float, faction: Faction, color: Color) {
        val inkBlack = Color(0xFF130F0A)
        val alphaColor = color.copy(alpha = 0.8f)

        drawScope.apply {
            when (faction) {
                Faction.DOMINION -> {
                    // Triangle emblem
                    val path = Path().apply {
                        moveTo(x, y - radius)
                        lineTo(x + radius * 0.866f, y + radius * 0.5f)
                        lineTo(x - radius * 0.866f, y + radius * 0.5f)
                        close()
                    }
                    drawPath(path, color = alphaColor, style = Stroke(width = 2f))
                    drawCircle(color = inkBlack, radius = radius * 0.4f, center = Offset(x, y + radius * 0.1f))
                    drawCircle(color = alphaColor, radius = radius * 0.4f, center = Offset(x, y + radius * 0.1f), style = Stroke(width = 1.5f))
                }
                Faction.SYNTH -> {
                    // Hexagon tech emblem
                    val path = Path().apply {
                        for (i in 0..5) {
                            val angleRad = kotlin.math.PI / 180f * (60f * i - 30f)
                            val px = x + radius * cos(angleRad).toFloat()
                            val py = y + radius * sin(angleRad).toFloat()
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(path, color = alphaColor, style = Stroke(width = 2f))
                    drawLine(alphaColor, Offset(x, y - radius), Offset(x, y + radius), strokeWidth = 2f)
                }
                Faction.TRADERS -> {
                    // Coin/Diamond emblem
                    drawRect(color = alphaColor, topLeft = Offset(x - radius * 0.6f, y - radius * 0.6f), size = Size(radius * 1.2f, radius * 1.2f), style = Stroke(width = 2f))
                    drawCircle(color = alphaColor, radius = radius * 0.5f, center = Offset(x, y), style = Stroke(width = 2f))
                }
                Faction.NOMADS -> {
                    // Star/Compass emblem
                    drawLine(alphaColor, Offset(x, y - radius), Offset(x, y + radius), strokeWidth = 2f)
                    drawLine(alphaColor, Offset(x - radius, y), Offset(x + radius, y), strokeWidth = 2f)
                    drawCircle(color = alphaColor, radius = radius * 0.6f, center = Offset(x, y), style = Stroke(width = 1.5f))
                }
                Faction.KAELEN -> {
                    // Eye emblem
                    val path = Path().apply {
                        moveTo(x - radius, y)
                        quadraticBezierTo(x, y - radius, x + radius, y)
                        quadraticBezierTo(x, y + radius, x - radius, y)
                    }
                    drawPath(path, color = alphaColor, style = Stroke(width = 2f))
                    drawCircle(color = inkBlack, radius = radius * 0.4f, center = Offset(x, y))
                    drawCircle(color = alphaColor, radius = radius * 0.3f, center = Offset(x, y))
                }
                Faction.ANCIENT_NPC -> {
                    drawCircle(color = alphaColor, radius = radius * 0.8f, center = Offset(x, y), style = Stroke(width = 2f))
                    drawCircle(color = inkBlack, radius = radius * 0.4f, center = Offset(x, y), style = Fill)
                }
                Faction.XYLAR -> {
                    // Swarm/Claw emblem
                    val path = Path().apply {
                        moveTo(x, y - radius)
                        lineTo(x + radius * 0.5f, y)
                        lineTo(x + radius * 0.8f, y + radius * 0.8f)
                        lineTo(x, y + radius * 0.4f)
                        lineTo(x - radius * 0.8f, y + radius * 0.8f)
                        lineTo(x - radius * 0.5f, y)
                        close()
                    }
                    drawPath(path, color = alphaColor, style = Stroke(width = 2f))
                }
            }
        }
    }
}
