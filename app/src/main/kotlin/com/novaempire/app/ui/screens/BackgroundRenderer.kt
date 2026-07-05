package com.novaempire.app.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.novaempire.core.domain.models.MapArchetype
import kotlin.random.Random

object BackgroundRenderer {
    private data class Star(val x: Float, val y: Float, val size: Float, val alpha: Float, val depth: Float)
    private var stars = emptyList<Star>()
    private var archetype: MapArchetype? = null

    private fun ensureInit(width: Float, height: Float, type: MapArchetype) {
        if (stars.isEmpty() || archetype != type) {
            archetype = type
            val random = Random(42) // Fixed seed for map consistency
            val count = if (type == MapArchetype.ZODIAC) 400 else 150
            stars = List(count) {
                Star(
                    x = random.nextFloat() * width * 3f - width,
                    y = random.nextFloat() * height * 3f - height,
                    size = random.nextFloat() * 2f + 0.5f,
                    alpha = random.nextFloat() * 0.6f + 0.1f,
                    depth = random.nextFloat() * 0.5f + 0.1f // Parallax factor
                )
            }
        }
    }

    fun drawParallaxBackground(drawScope: DrawScope, pan: Offset, scale: Float, type: MapArchetype) {
        ensureInit(drawScope.size.width, drawScope.size.height, type)

        val baseColor = when (type) {
            MapArchetype.ZODIAC -> Color(0xFF140D20) // Deep nebula violet
            MapArchetype.STANDARD -> Color(0xFF0F1113) // Cold industrial blue/black
        }

        drawScope.drawRect(color = baseColor)

        // Draw parallax stars
        for (star in stars) {
            val px = (star.x + pan.x * star.depth) * scale
            val py = (star.y + pan.y * star.depth) * scale

            // Only draw if visible
            if (px in -50f..(drawScope.size.width + 50f) && py in -50f..(drawScope.size.height + 50f)) {
                val starColor = if (type == MapArchetype.ZODIAC) {
                    Color(0xFFE8D0FF).copy(alpha = star.alpha)
                } else {
                    Color(0xFFC0D8E0).copy(alpha = star.alpha)
                }
                drawScope.drawCircle(
                    color = starColor,
                    radius = star.size * scale,
                    center = Offset(px, py)
                )
            }
        }

        // Add nebula clouds for Zodiac
        if (type == MapArchetype.ZODIAC) {
            drawScope.drawCircle(
                color = Color(0xFF4A2B6B).copy(alpha = 0.15f),
                radius = drawScope.size.width * 0.8f * scale,
                center = Offset(drawScope.size.width / 2f + pan.x * 0.2f, drawScope.size.height / 2f + pan.y * 0.2f)
            )
        }
    }
}
