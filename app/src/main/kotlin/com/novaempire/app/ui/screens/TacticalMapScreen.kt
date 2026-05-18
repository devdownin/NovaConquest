package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.VoidBlack
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun TacticalMapScreen(
    gameState: GameState,
    onEndTurnClick: () -> Unit
) {
    var selectedHex by remember { mutableStateOf<HexCoord?>(null) }
    val hexRadius = 80f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tactical Map Canvas
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Convert pixel coordinates back to hex coordinates
                    val q = (sqrt(3.0) / 3 * (offset.x - centerX) - 1.0 / 3 * (offset.y - centerY)) / hexRadius
                    val r = (2.0 / 3 * (offset.y - centerY)) / hexRadius

                    selectedHex = hexRound(q, r, -q-r)
                }
            }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            gameState.map.tiles.values.forEach { tile ->
                val x = centerX + hexRadius * (sqrt(3f) * tile.coord.q + sqrt(3f) / 2 * tile.coord.r)
                val y = centerY + hexRadius * (3f / 2 * tile.coord.r)

                val terrainColor = when (tile.terrain) {
                    TerrainType.PLANET -> Color.Green.copy(alpha = 0.3f)
                    TerrainType.ASTEROIDS -> Color.Gray.copy(alpha = 0.5f)
                    TerrainType.NEBULA -> Color.Magenta.copy(alpha = 0.3f)
                    TerrainType.BLACK_HOLE -> Color.Black
                    else -> VoidBlack
                }

                // Draw filled hex for terrain
                drawHexagon(x, y, hexRadius - 2f, terrainColor, fill = true)

                // Draw stroke
                val strokeColor = if (tile.coord == selectedHex) NeonCyan else Color.DarkGray
                val strokeWidth = if (tile.coord == selectedHex) 4f else 2f
                drawHexagon(x, y, hexRadius, strokeColor, fill = false, strokeWidth)

                // Draw planet indicator
                if (tile.terrain == TerrainType.PLANET) {
                    drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
                }
            }
        }

        // Top HUD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
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
                    text = "TURN \${gameState.turn}",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        // Action Panel when hex is selected
        selectedHex?.let { coord ->
            val tile = gameState.map.getTileAt(coord)
            if (tile != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 100.dp, start = 16.dp)
                        .width(200.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HEX \${coord.q}, \${coord.r}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Text("Terrain: \${tile.terrain.name}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        IndustrialButton(text = "MOVE", onClick = {})
                        Spacer(modifier = Modifier.height(8.dp))
                        IndustrialButton(text = "INFO", onClick = {})
                    }
                }
            }
        }

        // Bottom Right End Turn
        IndustrialButton(
            text = "END TURN",
            onClick = onEndTurnClick,
            modifier = Modifier
                .width(150.dp)
                .padding(bottom = 100.dp, end = 16.dp)
                .align(Alignment.BottomEnd),
            color = NeonOrange
        )
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    fill: Boolean = false,
    strokeWidth: Float = 2f
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

    if (fill) {
        drawPath(path = path, color = color, style = Fill)
    } else {
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
    }
}

fun hexRound(fracQ: Double, fracR: Double, fracS: Double): HexCoord {
    var q = Math.round(fracQ).toInt()
    var r = Math.round(fracR).toInt()
    var s = Math.round(fracS).toInt()

    val qDiff = Math.abs(q - fracQ)
    val rDiff = Math.abs(r - fracR)
    val sDiff = Math.abs(s - fracS)

    if (qDiff > rDiff && qDiff > sDiff) {
        q = -r - s
    } else if (rDiff > sDiff) {
        r = -q - s
    } else {
        s = -q - r
    }
    return HexCoord(q, r, s)
}
