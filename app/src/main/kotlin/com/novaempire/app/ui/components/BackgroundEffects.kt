package com.novaempire.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

@Composable
fun HalftoneBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.05f),
    dotSize: Float = 2f,
    spacing: Float = 12f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        var y = 0f
        while (y < height) {
            var x = 0f
            while (x < width) {
                drawCircle(
                    color = color,
                    radius = dotSize,
                    center = Offset(x, y)
                )
                x += spacing
            }
            y += spacing
        }
    }
}

@Composable
fun NoiseOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.03f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width.toInt()
        val height = size.height.toInt()
        val step = 4

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                if (Random.nextFloat() > 0.5f) {
                    drawRect(
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(x.toFloat(), y.toFloat()),
                        size = androidx.compose.ui.geometry.Size(step.toFloat(), step.toFloat())
                    )
                }
            }
        }
    }
}
