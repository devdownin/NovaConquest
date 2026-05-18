package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
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
    onMoveUnit: (from: HexCoord, to: HexCoord) -> Unit,
    onAttackUnit: (from: HexCoord, to: HexCoord) -> Unit,
    onSiegePlanet: (from: HexCoord, to: HexCoord) -> Unit,
    onCapturePlanet: (from: HexCoord, to: HexCoord) -> Unit,
    onOpenAcademy: () -> Unit
) {
    var selectedHex by remember { mutableStateOf<HexCoord?>(null) }
    var ghostPath by remember { mutableStateOf<List<HexCoord>?>(null) }
    var dragStartHex by remember { mutableStateOf<HexCoord?>(null) }

    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }


    var combatPreviewData by remember { mutableStateOf<Pair<HexCoord, HexCoord>?>(null) }

    // Animation state
    var activeCombatAnim by remember { mutableStateOf<com.novaempire.core.domain.state.CombatEvent?>(null) }
    val laserProgress = remember { Animatable(0f) }
    val explosionScale = remember { Animatable(0f) }

    LaunchedEffect(gameState.lastCombatEvent) {
        if (gameState.lastCombatEvent != null && gameState.lastCombatEvent != activeCombatAnim) {
            activeCombatAnim = gameState.lastCombatEvent
            // Reset
            laserProgress.snapTo(0f)
            explosionScale.snapTo(0f)

            // Animate laser
            laserProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing)
            )

            // Animate explosion
            explosionScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )

            delay(200)
            activeCombatAnim = null
        }
    }

    val hexRadius = 80f

    val currentPlayerState = gameState.playerStates[gameState.activeFaction]
    val exploredHexes = currentPlayerState?.exploredHexes ?: emptySet()
    val visibleHexes = currentPlayerState?.visibleHexes ?: emptySet()

    fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
        val adjustedX = (x - centerX - pan.x) / scale
        val adjustedY = (y - centerY - pan.y) / scale
        val q = (sqrt(3.0) / 3 * adjustedX - 1.0 / 3 * adjustedY) / hexRadius
        val r = (2.0 / 3 * adjustedY) / hexRadius
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
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    scale = (scale * zoomChange).coerceIn(0.5f, 3f)
                    pan += panChange
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = pan.x,
                translationY = pan.y
            )
            .pointerInput(gameState) {
                detectTapGestures { offset ->
                    val coord = pixelToHex(offset.x, offset.y, size.width / 2f, size.height / 2f)
                    if (gameState.map.tiles.containsKey(coord) && exploredHexes.contains(coord)) {
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
                            val targetCoord = path.last()
                            val targetUnit = gameState.units[targetCoord]

                            // Ensure we have a valid action
                            if (targetUnit != null && targetUnit.faction != gameState.activeFaction) {
                                // Initiate Combat Preview
                                combatPreviewData = Pair(start, targetCoord)
                            } else if (targetUnit == null) {
                                // Execute movement
                                onMoveUnit(start, targetCoord)
                            }
                        }

                        // Only clear drag states, DO NOT clear combatPreviewData here!
                        dragStartHex = null
                        ghostPath = null
                    },
                    onDragCancel = {
                        dragStartHex = null
                        ghostPath = null
                    },
                    onDrag = { change, _ ->
                        val start = dragStartHex
                        if (start != null) {
                            val coord = pixelToHex(change.position.x, change.position.y, size.width / 2f, size.height / 2f)
                            if (coord != start && gameState.map.tiles.containsKey(coord)) {
                                val gridMap = GameGridMap(gameState)
                                // If hovering over enemy unit, pretend it's passable just to draw the attack path
                                val originalUnit = gameState.units[coord]
                                val tile = gameState.map.getTileAt(coord)
                                val path = if (originalUnit != null && originalUnit.faction != gameState.activeFaction) {
                                    if (start.distanceTo(coord) == 1) listOf(coord) else null
                                } else if (tile != null && !tile.terrain.isPassable) {
                                    null // Prevent drawing path to an impassable terrain directly
                                } else {
                                    HexPathfinder.findPath(start, coord, gridMap, maxCost = 4)
                                }
                                ghostPath = path
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

            // Draw Terrain & Fog of War
            gameState.map.tiles.values.forEach { tile ->
                val x = centerX + hexRadius * (sqrt(3f) * tile.coord.q + sqrt(3f) / 2 * tile.coord.r)
                val y = centerY + hexRadius * (3f / 2 * tile.coord.r)

                val isExplored = exploredHexes.contains(tile.coord)
                val isVisible = visibleHexes.contains(tile.coord)

                if (isExplored) {
                    val baseColor = when (tile.terrain) {
                        TerrainType.PLANET -> NeonGreen
                        TerrainType.ASTEROIDS -> NeonOrange
                        TerrainType.NEBULA -> Color(0xFFB026FF)
                        TerrainType.BLACK_HOLE -> Color.Black
                        else -> NeonCyan
                    }

                    val alphaMod = if (isVisible) 0.15f else 0.05f

                    val bgBrush = Brush.radialGradient(
                        colors = listOf(baseColor.copy(alpha = alphaMod), Color.Transparent),
                        center = Offset(x, y),
                        radius = hexRadius
                    )
                    drawHexagonPath(x, y, hexRadius, brush = bgBrush, fill = true)

                    if (isVisible && tile.terrain != TerrainType.EMPTY) {
                         drawHexagonPath(x, y, hexRadius, color = baseColor.copy(alpha=0.05f), fill = true)
                    }

                    val strokeColor = if (tile.coord == selectedHex) NeonCyan else baseColor.copy(alpha = if (isVisible) 0.5f else 0.2f)
                    val strokeWidth = if (tile.coord == selectedHex) 4f else 1.5f
                    drawHexagonPath(x, y, hexRadius - strokeWidth/2, color = strokeColor, fill = false, strokeWidth = strokeWidth)

                    if (isVisible) {
                        when (tile.terrain) {
                            TerrainType.PLANET -> drawPlanet(x, y, hexRadius, tile.owner)
                            TerrainType.ASTEROIDS -> drawAsteroids(x, y, hexRadius)
                            TerrainType.NEBULA -> drawNebula(x, y, hexRadius)
                            else -> {}
                        }
                    }
                } else {
                    drawHexagonPath(x, y, hexRadius, color = VoidBlack, fill = true)
                    drawHexagonPath(x, y, hexRadius, color = Color(0xFF1A1D24), fill = false, strokeWidth = 1f)
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

                        val isAttack = gameState.units[coord]?.faction?.let { it != gameState.activeFaction } ?: false
                        val pathColor = if (isAttack) NeonRed else NeonCyan

                        drawLine(
                            color = pathColor.copy(alpha = 0.6f),
                            start = prevPoint,
                            end = currentPoint,
                            strokeWidth = 6f
                        )
                        prevPoint = currentPoint
                    }
                    val dest = path.last()
                    val dx = centerX + hexRadius * (sqrt(3f) * dest.q + sqrt(3f) / 2 * dest.r)
                    val dy = centerY + hexRadius * (3f / 2 * dest.r)
                    val isAttack = gameState.units[dest]?.faction?.let { it != gameState.activeFaction } ?: false
                    drawHexagonPath(dx, dy, hexRadius, color = (if (isAttack) NeonRed else NeonCyan).copy(alpha = 0.3f), fill = true)
                }
            }

            // Draw Units
            gameState.units.values.filter { visibleHexes.contains(it.position) }.forEach { unit ->
                val x = centerX + hexRadius * (sqrt(3f) * unit.position.q.toFloat() + sqrt(3f) / 2 * unit.position.r.toFloat())
                val y = centerY + hexRadius * (3f / 2 * unit.position.r.toFloat())

                // Hide unit if it was just destroyed and explosion is happening
                val isDestroyedTarget = activeCombatAnim?.defenderCoord == unit.position && activeCombatAnim?.targetDestroyed == true
                if (isDestroyedTarget && explosionScale.value > 0.5f) return@forEach

                val unitColor = when(unit.faction) {
                    Faction.DOMINION -> NeonRed
                    Faction.TRADERS -> NeonGold
                    Faction.SYNTH -> NeonCyan
                    else -> Color.White
                }

                val alpha = if (unit.hasMoved) 0.5f else 1.0f
                val path = Path()
                path.moveTo(x, y - 25f)
                path.lineTo(x + 18f, y + 15f)
                path.lineTo(x, y + 5f)
                path.lineTo(x - 18f, y + 15f)
                path.close()

                val glowBrush = Brush.radialGradient(
                    colors = listOf(unitColor.copy(alpha = alpha * 0.8f), Color.Transparent),
                    center = Offset(x, y),
                    radius = 30f
                )
                drawPath(path, glowBrush, style = Fill)
                drawPath(path, unitColor.copy(alpha = alpha), style = Stroke(width = 3f))
            }

            // Draw Combat Animations
            activeCombatAnim?.let { combat ->
                val ax = centerX + hexRadius * (sqrt(3f) * combat.attackerCoord.q.toFloat() + sqrt(3f) / 2 * combat.attackerCoord.r.toFloat())
                val ay = centerY + hexRadius * (3f / 2 * combat.attackerCoord.r.toFloat())

                val dx = centerX + hexRadius * (sqrt(3f) * combat.defenderCoord.q.toFloat() + sqrt(3f) / 2 * combat.defenderCoord.r.toFloat())
                val dy = centerY + hexRadius * (3f / 2 * combat.defenderCoord.r.toFloat())

                if (laserProgress.value > 0f && laserProgress.value < 1f) {
                    val currentEx = ax + (dx - ax) * laserProgress.value
                    val currentEy = ay + (dy - ay) * laserProgress.value
                    drawLine(
                        color = NeonRed,
                        start = Offset(ax, ay),
                        end = Offset(currentEx, currentEy),
                        strokeWidth = 8f
                    )
                }

                if (explosionScale.value > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonOrange.copy(alpha = 1f - explosionScale.value), Color.Transparent),
                            center = Offset(dx, dy),
                            radius = hexRadius * explosionScale.value
                        ),
                        radius = hexRadius * explosionScale.value,
                        center = Offset(dx, dy)
                    )
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
                if (gameState.activeEvent != GalacticEvent.NONE) {
                    Text(
                        text = gameState.activeEvent.displayName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonOrange
                    )
                }
            }
        }

        // Action Panel when hex is selected
        selectedHex?.let { coord ->
            val tile = gameState.map.getTileAt(coord)
            val unit = gameState.units[coord]
            if (tile != null && combatPreviewData == null) {
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

                        if (unit != null && visibleHexes.contains(coord)) {
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

        // Bottom Right Actions
        Column(
            modifier = Modifier
                .width(150.dp)
                .padding(bottom = 100.dp, end = 16.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IndustrialButton(
                text = "ACADEMY",
                onClick = onOpenAcademy,
                color = NeonCyan
            )
            IndustrialButton(
                text = "END TURN",
                onClick = onEndTurnClick,
                color = NeonOrange
            )
        }

        // Combat Preview Overlay
        combatPreviewData?.let { (attackerCoord, defenderCoord) ->
            val attacker = gameState.units[attackerCoord]
            val defender = gameState.units[defenderCoord]

            if (attacker != null && defender != null) {
                CombatPreviewScreen(
                    attacker = attacker,
                    defender = defender,
                    onConfirm = {
                        onAttackUnit(attackerCoord, defenderCoord)
                        combatPreviewData = null
                    },
                    onCancel = {
                        combatPreviewData = null
                    }
                )
            }
        }
    }
}

fun DrawScope.drawPlanet(x: Float, y: Float, hexRadius: Float, owner: Faction?) {
    val planetColor = when (owner) {
        Faction.DOMINION -> NeonRed
        Faction.TRADERS -> NeonGold
        Faction.SYNTH -> NeonCyan
        Faction.NOMADS -> NeonOrange
        Faction.KAELEN -> NeonGreen
        Faction.XYLAR -> Color.Cyan
        Faction.ANCIENT_NPC -> Color.Magenta
        else -> NeonGreen // Neutral
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(planetColor.copy(alpha = 0.8f), planetColor.copy(alpha = 0.2f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.4f
        ),
        radius = hexRadius * 0.4f,
        center = Offset(x, y)
    )
    drawOval(
        color = NeonCyan.copy(alpha = 0.6f),
        topLeft = Offset(x - hexRadius * 0.6f, y - hexRadius * 0.2f),
        size = Size(hexRadius * 1.2f, hexRadius * 0.4f),
        style = Stroke(width = 2f)
    )
    if (owner != null) {
        drawCircle(
            color = planetColor,
            radius = hexRadius * 0.5f,
            center = Offset(x, y),
            style = Stroke(width = 3f)
        )
    }
}

fun DrawScope.drawAsteroids(x: Float, y: Float, hexRadius: Float) {
    val offsets = listOf(
        Offset(-15f, -20f), Offset(10f, -25f), Offset(20f, 10f),
        Offset(-25f, 15f), Offset(0f, 25f), Offset(-5f, 0f)
    )
    val sizes = listOf(8f, 12f, 10f, 14f, 6f, 16f)

    offsets.forEachIndexed { index, offset ->
        val ax = x + offset.x
        val ay = y + offset.y
        val r = sizes[index]

        val path = Path()
        path.moveTo(ax, ay - r)
        path.lineTo(ax + r, ay)
        path.lineTo(ax, ay + r)
        path.lineTo(ax - r, ay)
        path.close()

        drawPath(path, color = NeonOrange.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
    }
}

fun DrawScope.drawNebula(x: Float, y: Float, hexRadius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFB026FF).copy(alpha = 0.5f), Color.Transparent),
            center = Offset(x - 10f, y - 10f),
            radius = hexRadius * 0.7f
        ),
        radius = hexRadius * 0.7f,
        center = Offset(x - 10f, y - 10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(x + 15f, y + 10f),
            radius = hexRadius * 0.6f
        ),
        radius = hexRadius * 0.6f,
        center = Offset(x + 15f, y + 10f)
    )
}

fun DrawScope.drawHexagonPath(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color = Color.Unspecified,
    brush: Brush? = null,
    fill: Boolean = false,
    strokeWidth: Float = 2f
) {
    val path = Path()
    for (i in 0..5) {
        val angleDeg = 60f * i - 30f
        val angleRad = Math.PI / 180f * angleDeg
        val px = centerX + radius * cos(angleRad).toFloat()
        val py = centerY + radius * sin(angleRad).toFloat()
        if (i == 0) {
            path.moveTo(px, py)
        } else {
            path.lineTo(px, py)
        }
    }
    path.close()

    if (fill) {
        if (brush != null) drawPath(path = path, brush = brush)
        else drawPath(path = path, color = color, style = Fill)
    } else {
        if (brush != null) drawPath(path = path, brush = brush, style = Stroke(width = strokeWidth))
        else drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
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
