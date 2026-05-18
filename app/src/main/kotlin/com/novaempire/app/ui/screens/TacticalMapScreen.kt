package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.GameGridMap
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.hex.HexPathfinder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun TacticalMapScreen(
    gameState: GameState,
    onEndTurnClick: () -> Unit,
    onMoveUnit: (from: HexCoord, to: HexCoord) -> Unit
) {
    var selectedHex by remember { mutableStateOf<HexCoord?>(null) }
    var ghostPath by remember { mutableStateOf<List<HexCoord>?>(null) }
    var dragStartHex by remember { mutableStateOf<HexCoord?>(null) }
    val hexRadius = 80f

    fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
        val q = (sqrt(3.0) / 3 * (x - centerX) - 1.0 / 3 * (y - centerY)) / hexRadius
        val r = (2.0 / 3 * (y - centerY)) / hexRadius
        return hexRound(q, r, -q-r)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tactical Map Canvas
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState) { // Re-bind if state changes drastically
                detectTapGestures { offset ->
                    val coord = pixelToHex(offset.x, offset.y, size.width / 2f, size.height / 2f)
                    if (gameState.map.tiles.containsKey(coord)) {
                        selectedHex = coord
                    }
                }
            }
            .pointerInput(gameState) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val coord = pixelToHex(offset.x, offset.y, size.width / 2f, size.height / 2f)
                        val unit = gameState.units[coord]
                        if (unit != null && unit.faction == gameState.activeFaction && !unit.hasMoved) {
                            dragStartHex = coord
                            selectedHex = coord
                        }
                    },
                    onDragEnd = {
                        val start = dragStartHex
                        val path = ghostPath
                        if (start != null && path != null && path.isNotEmpty()) {
                            onMoveUnit(start, path.last())
                        }
                        dragStartHex = null
                        ghostPath = null
                    },
                    onDragCancel = {
                        dragStartHex = null
                        ghostPath = null
                    },
                    onDrag = { change, dragAmount ->
                        val start = dragStartHex
                        if (start != null) {
                            val coord = pixelToHex(change.position.x, change.position.y, size.width / 2f, size.height / 2f)
                            if (coord != start && gameState.map.tiles.containsKey(coord)) {
                                val gridMap = GameGridMap(gameState)
                                ghostPath = HexPathfinder.findPath(start, coord, gridMap, maxCost = 4) // Hardcoded mobility for now
                            } else {
                                ghostPath = null
                            }
                        }
                    }
                )
            }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            // Draw Terrain
            gameState.map.tiles.values.forEach { tile ->
                val x = centerX + hexRadius * (sqrt(3f) * tile.coord.q + sqrt(3f) / 2 * tile.coord.r)
                val y = centerY + hexRadius * (3f / 2 * tile.coord.r)

                val terrainColor = when (tile.terrain) {
                    TerrainType.PLANET -> Color.Green.copy(alpha = 0.2f)
                    TerrainType.ASTEROIDS -> Color.Gray.copy(alpha = 0.4f)
                    TerrainType.NEBULA -> Color.Magenta.copy(alpha = 0.2f)
                    TerrainType.BLACK_HOLE -> Color.Black
                    else -> VoidBlack
                }

                drawHexagon(x, y, hexRadius - 2f, terrainColor, fill = true)

                // Draw selection highlight
                if (tile.coord == selectedHex) {
                    drawHexagon(x, y, hexRadius, NeonCyan, fill = false, 4f)
                } else {
                    drawHexagon(x, y, hexRadius, Color.DarkGray, fill = false, 1f)
                }

                if (tile.terrain == TerrainType.PLANET) {
                    drawCircle(color = Color.White.copy(alpha = 0.5f), radius = 15f, center = Offset(x, y))
                }
            }

            // Draw Ghost Path
            ghostPath?.let { path ->
                if (path.isNotEmpty()) {
                    var prevPoint = Offset(
                        centerX + hexRadius * (sqrt(3f) * dragStartHex!!.q + sqrt(3f) / 2 * dragStartHex!!.r),
                        centerY + hexRadius * (3f / 2 * dragStartHex!!.r)
                    )
                    path.forEach { coord ->
                        val px = centerX + hexRadius * (sqrt(3f) * coord.q + sqrt(3f) / 2 * coord.r)
                        val py = centerY + hexRadius * (3f / 2 * coord.r)
                        val currentPoint = Offset(px, py)
                        drawLine(
                            color = NeonCyan.copy(alpha = 0.6f),
                            start = prevPoint,
                            end = currentPoint,
                            strokeWidth = 6f
                        )
                        prevPoint = currentPoint
                    }
                    // Highlight destination
                    val dest = path.last()
                    val dx = centerX + hexRadius * (sqrt(3f) * dest.q + sqrt(3f) / 2 * dest.r)
                    val dy = centerY + hexRadius * (3f / 2 * dest.r)
                    drawHexagon(dx, dy, hexRadius, NeonCyan.copy(alpha = 0.8f), fill = true)
                }
            }

            // Draw Units
            gameState.units.values.forEach { unit ->
                val x = centerX + hexRadius * (sqrt(3f) * unit.position.q + sqrt(3f) / 2 * unit.position.r)
                val y = centerY + hexRadius * (3f / 2 * unit.position.r)

                val unitColor = when(unit.faction) {
                    Faction.DOMINION -> NeonRed
                    Faction.TRADERS -> NeonOrange
                    Faction.SYNTH -> NeonCyan
                    else -> Color.White
                }

                // Draw ship icon placeholder (triangle)
                val path = Path()
                path.moveTo(x, y - 20f)
                path.lineTo(x + 15f, y + 15f)
                path.lineTo(x - 15f, y + 15f)
                path.close()
                drawPath(path, if (unit.hasMoved) unitColor.copy(alpha=0.5f) else unitColor, style = Fill)
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
            val unit = gameState.units[coord]
            if (tile != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 100.dp, start = 16.dp)
                        .width(220.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HEX \${coord.q}, \${coord.r}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        Text("Terrain: \${tile.terrain.name}", style = MaterialTheme.typography.bodyMedium)

                        if (unit != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Unit: \${unit.type.name}", style = MaterialTheme.typography.bodyLarge, color = NeonOrange)
                            Text("Faction: \${unit.faction.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("HP: \${unit.currentHp}/\${unit.type.maxHp}", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
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
