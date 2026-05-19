package com.novaempire.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.*
import com.novaempire.core.domain.state.CombatEvent
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
    visibleHexes: Set<HexCoord>,
    onHexClick: (HexCoord) -> Unit,
    onMoveUnit: (HexCoord, HexCoord) -> Unit,
    onAttackUnit: (HexCoord, HexCoord) -> Unit,
    onEndTurnClick: () -> Unit,
    onOpenSystemManagement: (HexCoord) -> Unit,
    onSiegePlanet: (HexCoord, HexCoord) -> Unit,
    onCapturePlanet: (HexCoord, HexCoord) -> Unit,
    onOpenAcademy: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selectedHex by remember { mutableStateOf<HexCoord?>(null) }
    var combatPreviewData by remember { mutableStateOf<Pair<HexCoord, HexCoord>?>(null) }
    var ghostPath by remember { mutableStateOf<List<HexCoord>?>(null) }
    var dragStartHex by remember { mutableStateOf<HexCoord?>(null) }

    val laserProgress = remember { Animatable(0f) }
    val explosionScale = remember { Animatable(0f) }
    var activeCombatEvent by remember { mutableStateOf<CombatEvent?>(null) }

    val haptic = LocalHapticFeedback.current
    val exploredHexes = gameState.playerStates[gameState.activeFaction]?.exploredHexes ?: emptySet()

    LaunchedEffect(gameState.lastCombatEvent) {
        gameState.lastCombatEvent?.let { combat ->
            activeCombatEvent = combat
            AudioManager.playSound(SoundType.END_TURN) // using available sound
            laserProgress.snapTo(0f)
            explosionScale.snapTo(0f)

            laserProgress.animateTo(1f, animationSpec = tween(300))
            AudioManager.playSound(SoundType.END_TURN) // using available sound
            explosionScale.animateTo(1f, animationSpec = tween(400))

            kotlinx.coroutines.delay(200)
            activeCombatEvent = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = {
                        val start = dragStartHex
                        val path = ghostPath
                        if (start != null && path != null && path.isNotEmpty()) {
                            val targetCoord = path.last()
                            val targetUnit = gameState.units[targetCoord]

                            if (targetUnit != null && targetUnit.faction != gameState.activeFaction) {
                                combatPreviewData = Pair(start, targetCoord)
                            } else if (targetUnit == null) {
                                onMoveUnit(start, targetCoord)
                            }
                        }
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
                                val targetUnit = gameState.units[coord]
                                val path = if (targetUnit != null && targetUnit.faction != gameState.activeFaction) {
                                    // Hack: simulate attack path. In a real scenario we'd do a more complex pathfinding
                                    if (start.distanceTo(coord) == 1) listOf(coord) else null
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            val hexRadius = 60f
            val hexWidth = sqrt(3f) * hexRadius
            val hexHeight = 2f * hexRadius
            val horizSpacing = hexWidth
            val vertSpacing = 3f / 4f * hexHeight

            gameState.map.tiles.values.forEach { tile ->
                val q = tile.coord.q
                val r = tile.coord.r
                val x = centerX + horizSpacing * (q + r / 2f)
                val y = centerY + vertSpacing * r

                val isVisible = visibleHexes.contains(tile.coord)
                val isExplored = exploredHexes.contains(tile.coord)

                if (isExplored) {
                    val baseColor = when (tile.terrain) {
                        TerrainType.EMPTY -> VoidBlack
                        TerrainType.ASTEROIDS -> Color(0xFF2A2E39)
                        TerrainType.NEBULA -> Color(0xFF3B2A45)
                        TerrainType.PLANET -> Color(0xFF15202B)
                        TerrainType.BLACK_HOLE -> Color(0xFF452A15)
                        TerrainType.WORMHOLE -> Color(0xFF151B2B)
                        TerrainType.PLASMA_CLOUD -> Color(0xFF451A15)
                        TerrainType.ION_STORM -> Color(0xFF3B3A45)
                        TerrainType.ANOMALY -> Color(0xFF2A4539)
                    }

                    val alpha = if (isVisible) 1f else 0.4f

                    drawHexagonPath(
                        centerX = x, centerY = y, radius = hexRadius,
                        color = baseColor.copy(alpha = alpha), fill = true
                    )

                    drawHexagonPath(
                        centerX = x, centerY = y, radius = hexRadius,
                        color = Color(0xFF8F9094).copy(alpha = 0.2f * alpha),
                        strokeWidth = 1f
                    )

                    when (tile.terrain) {
                        TerrainType.PLANET -> drawPlanet(x, y, hexRadius, tile.owner)
                        TerrainType.ASTEROIDS -> drawAsteroids(x, y, hexRadius)
                        TerrainType.NEBULA -> drawNebula(x, y, hexRadius)
                        TerrainType.BLACK_HOLE -> {
                            drawCircle(color = NeonOrange.copy(alpha = 0.8f * alpha), radius = hexRadius * 0.6f, center = Offset(x, y))
                        }
                        else -> {}
                    }

                    val unit = gameState.units[tile.coord]
                    if (unit != null && (isVisible || unit.faction == gameState.activeFaction)) {
                        val unitColor = getFactionColor(unit.faction)
                        drawCircle(
                            color = unitColor,
                            radius = hexRadius * 0.3f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = VoidBlack,
                            radius = hexRadius * 0.2f,
                            center = Offset(x, y)
                        )
                    }

                    if (selectedHex == tile.coord) {
                        drawHexagonPath(
                            centerX = x, centerY = y, radius = hexRadius,
                            color = NeonCyan, strokeWidth = 4f
                        )
                    }
                } else {
                    drawHexagonPath(x, y, hexRadius, color = VoidBlack, fill = true)
                    drawHexagonPath(x, y, hexRadius, color = Color(0xFF1A1D24), fill = false, strokeWidth = 1f)
                }
            }

            ghostPath?.let { path ->
                if (path.isNotEmpty() && dragStartHex != null) {
                    var prevPoint = Offset(
                        centerX + horizSpacing * (dragStartHex!!.q + dragStartHex!!.r / 2f),
                        centerY + vertSpacing * dragStartHex!!.r
                    )
                    path.forEach { coord ->
                        val px = centerX + horizSpacing * (coord.q + coord.r / 2f)
                        val py = centerY + vertSpacing * coord.r
                        val currentPoint = Offset(px, py)

                        drawLine(
                            color = NeonCyan.copy(alpha = 0.6f),
                            start = prevPoint,
                            end = currentPoint,
                            strokeWidth = 8f
                        )
                        prevPoint = currentPoint
                    }
                    val target = path.last()
                    val targetUnit = gameState.units[target]
                    val highlightColor = if (targetUnit != null && targetUnit.faction != gameState.activeFaction) NeonRed else NeonCyan

                    val tx = centerX + horizSpacing * (target.q + target.r / 2f)
                    val ty = centerY + vertSpacing * target.r
                    drawHexagonPath(
                        centerX = tx, centerY = ty, radius = hexRadius,
                        color = highlightColor.copy(alpha = 0.5f), fill = true
                    )
                }
            }

            activeCombatEvent?.let { combat ->
                val ax = centerX + horizSpacing * (combat.attackerCoord.q + combat.attackerCoord.r / 2f)
                val ay = centerY + vertSpacing * combat.attackerCoord.r

                val dx = centerX + horizSpacing * (combat.defenderCoord.q + combat.defenderCoord.r / 2f)
                val dy = centerY + vertSpacing * combat.defenderCoord.r

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
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Top Navigation Bar (HUD)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Menu */ }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = NeonCyan)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("NOVA CONQUEST", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Credits
                IndustrialPanel(modifier = Modifier.padding(vertical = 4.dp), backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("145,200 C", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Turn
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TURN", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Text(gameState.turn.toString(), style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                }

                // Active Faction
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(NeonRed, shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(gameState.activeFaction.name, style = MaterialTheme.typography.labelLarge)
                }

                // Event
                if (gameState.activeEvent != GalacticEvent.NONE) {
                    IndustrialPanel(modifier = Modifier.padding(vertical = 4.dp), borderColor = NeonOrange.copy(alpha = 0.5f), backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(gameState.activeEvent.displayName.uppercase(), style = MaterialTheme.typography.labelLarge, color = NeonOrange)
                        }
                    }
                }
            }

            IconButton(onClick = { /* Wallet */ }) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Wallet", tint = NeonCyan)
            }
        }

        // Side Contextual Action Bar
        selectedHex?.let { coord ->
            val tile = gameState.map.getTileAt(coord)
            if (tile != null && combatPreviewData == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndustrialPanel(modifier = Modifier.width(180.dp).padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("SECTOR \${coord.q},\${coord.r}", style = MaterialTheme.typography.labelLarge, color = NeonCyan, modifier = Modifier.padding(bottom = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                Text(tile.terrain.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Action Buttons
                    IndustrialPanel(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = { /* Move */ }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary)
                        }
                    }
                    IndustrialPanel(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = { /* Defend/Hold */ }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary)
                        }
                    }
                    IndustrialPanel(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = { /* Scout */ }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary)
                        }
                    }
                    IndustrialPanel(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = { onOpenSystemManagement(coord) }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }
        }

        // Bottom Navigation / Floating Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            IndustrialPanel {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SMART FOCUS", style = MaterialTheme.typography.labelLarge)
                        Text("3 IDLE FLEETS", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            IndustrialButton(
                text = "END TURN",
                onClick = {
                    AudioManager.playSound(SoundType.END_TURN)
                    onEndTurnClick()
                },
                isPrimary = true,
                color = NeonOrange,
                icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                modifier = Modifier.width(200.dp)
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

fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
    val q = (sqrt(3f) / 3 * (x - centerX) - 1f / 3 * (y - centerY)) / 60f
    val r = (2f / 3 * (y - centerY)) / 60f
    return hexRound(q.toDouble(), r.toDouble(), -q.toDouble() - r.toDouble())
}

fun DrawScope.drawPlanet(x: Float, y: Float, hexRadius: Float, owner: Faction?) {
    val planetColor = owner?.let { getFactionColor(it) } ?: NeonGreen // Neutral

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
