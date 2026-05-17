package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.core.domain.state.GameState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TacticalMapScreen(
    gameState: GameState,
    onEndTurnClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tactical Map Canvas (Placeholder for hex grid)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hexRadius = 60f
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            // Draw a few sample hexes
            drawHexagon(centerX, centerY, hexRadius, NeonCyan)
            drawHexagon(centerX + hexRadius * 1.5f, centerY + hexRadius * 0.866f, hexRadius, Color.DarkGray)
            drawHexagon(centerX - hexRadius * 1.5f, centerY + hexRadius * 0.866f, hexRadius, Color.DarkGray)
        }

        // Top HUD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "124 C",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
                Text(
                    text = "TURN ${gameState.turn}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "ION STORM ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonOrange
                )
            }
        }

        // Bottom Action Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.End
        ) {
            IndustrialButton(
                text = "END TURN",
                onClick = onEndTurnClick,
                modifier = Modifier.width(150.dp)
            )
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color
) {
    val path = Path()
    for (i in 0..5) {
        val angleDeg = 60f * i - 30f
        val angleRad = Math.PI / 180f * angleDeg
        val x = centerX + radius * cos(angleRad).toFloat()
        val y = centerY + radius * sin(angleRad).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2f)
    )
}
