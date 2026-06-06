package com.novaempire.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.nativeCanvas
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

private const val HEX_RADIUS = 60f

@Composable
fun TacticalMapScreen(
    isAiThinking: Boolean = false,
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
    centerRequest: Pair<HexCoord, Int>? = null,
    modifier: Modifier = Modifier
) {
    val initScale = 0.8f
    val initCoord = gameState.playerStates[gameState.humanFaction]?.capitalCoord
        ?: gameState.units.values.firstOrNull { it.faction == gameState.humanFaction }?.position
        ?: HexCoord(0, 0)
    val horizSpacingInit = sqrt(3f) * HEX_RADIUS
    val vertSpacingInit = 1.5f * HEX_RADIUS

    var scale by remember { mutableStateOf(initScale) }
    var pan by remember {
        mutableStateOf(
            Offset(
                -horizSpacingInit * (initCoord.q + initCoord.r / 2f) * initScale,
                -vertSpacingInit * initCoord.r * initScale
            )
        )
    }
    var selectedHex by remember { mutableStateOf<HexCoord?>(null) }
    var combatPreviewData by remember { mutableStateOf<Pair<HexCoord, HexCoord>?>(null) }
    var ghostPath by remember { mutableStateOf<List<HexCoord>?>(null) }
    var dragStartHex by remember { mutableStateOf<HexCoord?>(null) }
    var currentHoveredHex by remember { mutableStateOf<HexCoord?>(null) }

    // rememberUpdatedState so the pointerInput(Unit) closures (created once, never
    // recreated) always read the current gameState and callbacks on every gesture.
    val currentGameState by rememberUpdatedState(gameState)
    val currentOnMoveUnit by rememberUpdatedState(onMoveUnit)
    val currentOnAttackUnit by rememberUpdatedState(onAttackUnit)
    val currentOnSiegePlanet by rememberUpdatedState(onSiegePlanet)
    val currentOnCapturePlanet by rememberUpdatedState(onCapturePlanet)

    val laserProgress = remember { Animatable(0f) }
    val explosionScale = remember { Animatable(0f) }
    var activeCombatEvent by remember { mutableStateOf<CombatEvent?>(null) }

    // Unit movement animation
    data class MovingUnitAnim(val id: String, val from: HexCoord, val to: HexCoord)
    var movingUnitAnim by remember { mutableStateOf<MovingUnitAnim?>(null) }
    val movingProgress = remember { Animatable(1f) }
    val prevUnits = remember { mutableStateOf(gameState.units) }

    val haptic = LocalHapticFeedback.current
    val playerState = gameState.playerStates[gameState.activeFaction]
    val exploredHexes = playerState?.exploredHexes ?: emptySet()
    val credits = playerState?.credits ?: 0
    val activeFactionColor = getFactionColor(gameState.activeFaction)

    val incomePerTurn = remember(gameState) {
        val ps = gameState.playerStates[gameState.activeFaction] ?: return@remember 0
        var income = 10
        val ownedPlanets = gameState.map.tiles.values.filter {
            it.terrain == TerrainType.PLANET && it.owner == gameState.activeFaction
        }
        income += ownedPlanets.sumOf { 5 + it.systemLevel * 2 }
        if (ps.recruitedHeroes.contains(HeroRegistry.ELARA)) income += (income * 0.10).toInt() + 2
        if (gameState.activeEvent == GalacticEvent.ECONOMIC_BOOM) income += 3
        income += gameState.activeFaction.bonusCredits
        income
    }
    val buildingPlanets = remember(gameState) {
        gameState.playerStates[gameState.activeFaction]?.buildQueue?.map { it.planetCoord }?.toSet() ?: emptySet()
    }

    // Hexes the selected unit can still move to (empty when no unit selected / already moved)
    val reachableHexes = remember(selectedHex, gameState) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasMoved) return@remember emptySet<HexCoord>()
        val ionPenalty = if (gameState.activeEvent == GalacticEvent.ION_STORM) 1 else 0
        HexPathfinder.findReachable(sel, GameGridMap(gameState), (unit.type.movement + unit.faction.bonusMovement - ionPenalty).coerceAtLeast(1))
    }

    // Enemy units the selected unit can attack this turn
    val attackableCoords = remember(selectedHex, gameState) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        gameState.units.values
            .filter { it.faction != gameState.activeFaction && sel.distanceTo(it.position) <= unit.type.range }
            .map { it.position }
            .toSet()
    }

    LaunchedEffect(gameState.lastCombatEvent) {
        gameState.lastCombatEvent?.let { combat ->
            activeCombatEvent = combat
            AudioManager.playSound(SoundType.COMBAT_LASER)
            laserProgress.snapTo(0f)
            explosionScale.snapTo(0f)

            laserProgress.animateTo(1f, animationSpec = tween(300))
            AudioManager.playSound(SoundType.COMBAT_EXPLOSION)
            explosionScale.animateTo(1f, animationSpec = tween(400))

            kotlinx.coroutines.delay(200)
            activeCombatEvent = null
        }
    }

    LaunchedEffect(gameState.units) {
        val prev = prevUnits.value
        val curr = gameState.units
        val prevById = prev.values.associateBy { it.id }
        val movedUnit = curr.values.firstOrNull { unit ->
            val prevPos = prevById[unit.id]?.position
            prevPos != null && prevPos != unit.position
        }
        if (movedUnit != null) {
            val fromCoord = prevById[movedUnit.id]!!.position
            movingUnitAnim = MovingUnitAnim(movedUnit.id, fromCoord, movedUnit.position)
            movingProgress.snapTo(0f)
            movingProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            movingUnitAnim = null
        }
        prevUnits.value = curr
    }

    // Center map on a coord when requested by SMART FOCUS
    LaunchedEffect(centerRequest) {
        centerRequest?.let { (coord, _) ->
            val hSpacing = sqrt(3f) * HEX_RADIUS
            val vSpacing = 1.5f * HEX_RADIUS
            pan = Offset(
                -hSpacing * (coord.q + coord.r / 2f) * scale,
                -vSpacing * coord.r * scale
            )
        }
    }

    val sweepProgress = rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanlineSweep"
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Map layer (gestures + canvas). Pointer inputs sit INSIDE graphicsLayer so
        // Compose applies the inverse transform automatically — taps/drags are
        // expressed in pre-transform local coords, which match pixelToHex.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        pan += panChange
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val coord = pixelToHex(offset.x, offset.y, size.width / 2f, size.height / 2f)
                        val gs = currentGameState
                        val explored = gs.playerStates[gs.activeFaction]?.exploredHexes ?: emptySet()
                        if (!gs.map.tiles.containsKey(coord)) return@detectTapGestures
                        if (!explored.contains(coord)) return@detectTapGestures

                        val prev = selectedHex
                        val prevUnit = prev?.let { gs.units[it] }
                        val tappedUnit = gs.units[coord]
                        val tappedTile = gs.map.tiles[coord]

                        when {
                            // Same tile → deselect
                            prev == coord -> {
                                selectedHex = null
                                onClearSelection()
                            }
                            // Friendly unit selected → interpret second tap as action
                            prev != null && prevUnit != null && prevUnit.faction == gs.activeFaction -> when {
                                // Enemy unit in attack range → open combat preview
                                tappedUnit != null && tappedUnit.faction != gs.activeFaction &&
                                !prevUnit.hasAttacked && prev.distanceTo(coord) <= prevUnit.type.range -> {
                                    combatPreviewData = Pair(prev, coord)
                                    selectedHex = null
                                    onClearSelection()
                                }
                                // Adjacent enemy planet → siege or capture
                                tappedUnit == null && !prevUnit.hasAttacked &&
                                tappedTile?.terrain == TerrainType.PLANET &&
                                tappedTile.owner != null && tappedTile.owner != gs.activeFaction &&
                                prev.distanceTo(coord) == 1 -> {
                                    if (tappedTile.systemLevel <= 0) currentOnCapturePlanet(prev, coord)
                                    else currentOnSiegePlanet(prev, coord)
                                    selectedHex = null
                                    onClearSelection()
                                }
                                // Empty hex and unit hasn't moved → move
                                tappedUnit == null && !prevUnit.hasMoved -> {
                                    currentOnMoveUnit(prev, coord)
                                    selectedHex = null
                                    onClearSelection()
                                }
                                // Any other second tap → reselect new tile
                                else -> {
                                    selectedHex = coord
                                    onHexClick(coord)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            // No friendly unit selected → select tile
                            else -> {
                                selectedHex = coord
                                onHexClick(coord)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val coord = pixelToHex(offset.x, offset.y, size.width / 2f, size.height / 2f)
                            val gs = currentGameState
                            val unit = gs.units[coord]
                            if (unit != null && unit.faction == gs.activeFaction && !unit.hasMoved) {
                                dragStartHex = coord
                                selectedHex = coord
                                onHexClick(coord)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            val start = dragStartHex
                            val path = ghostPath
                            if (start != null && path != null && path.isNotEmpty()) {
                                val gs = currentGameState
                                val unit = gs.units[start]
                                val targetCoord = path.last()
                                val targetUnit = gs.units[targetCoord]
                                val targetTile = gs.map.tiles[targetCoord]

                                when {
                                    // Enemy unit → combat preview
                                    targetUnit != null && targetUnit.faction != gs.activeFaction ->
                                        combatPreviewData = Pair(start, targetCoord)
                                    // Enemy planet adjacent → siege or capture
                                    unit != null && !unit.hasAttacked &&
                                        targetUnit == null &&
                                        targetTile?.terrain == TerrainType.PLANET &&
                                        targetTile.owner != null && targetTile.owner != gs.activeFaction &&
                                        start.distanceTo(targetCoord) == 1 -> {
                                        if (targetTile.systemLevel <= 0) {
                                            currentOnCapturePlanet(start, targetCoord)
                                        } else {
                                            currentOnSiegePlanet(start, targetCoord)
                                        }
                                    }
                                    // Neutral/owned/empty hex → move
                                    targetUnit == null -> currentOnMoveUnit(start, targetCoord)
                                }
                            }
                            dragStartHex = null
                            ghostPath = null
                            currentHoveredHex = null
                        },
                        onDragCancel = {
                            dragStartHex = null
                            ghostPath = null
                            currentHoveredHex = null
                        },
                        onDrag = { change, _ ->
                            val start = dragStartHex ?: return@detectDragGestures
                            val gs = currentGameState
                            val unit = gs.units[start] ?: return@detectDragGestures
                            val coord = pixelToHex(change.position.x, change.position.y, size.width / 2f, size.height / 2f)
                            if (coord == currentHoveredHex) return@detectDragGestures
                            currentHoveredHex = coord

                            if (coord == start || !gs.map.tiles.containsKey(coord)) {
                                ghostPath = null
                                return@detectDragGestures
                            }

                            val gridMap = GameGridMap(gs)
                            val targetUnit = gs.units[coord]
                            val path = if (targetUnit != null && targetUnit.faction != gs.activeFaction) {
                                if (start.distanceTo(coord) <= unit.type.range) listOf(coord) else null
                            } else {
                                HexPathfinder.findPath(start, coord, gridMap, maxCost = unit.type.movement + unit.faction.bonusMovement)
                            }

                            if (path != null && path != ghostPath) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            ghostPath = path
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                val hexRadius = HEX_RADIUS
                val hexWidth = sqrt(3f) * hexRadius
                val hexHeight = 2f * hexRadius
                val horizSpacing = hexWidth
                val vertSpacing = 3f / 4f * hexHeight

                // Draw blueprint scanline
                val scanlineY = sweepProgress.value * size.height
                drawLine(
                    color = NeonCyan.copy(alpha = 0.10f),
                    start = Offset(-size.width, scanlineY - size.height / 2f),
                    end = Offset(size.width, scanlineY - size.height / 2f),
                    strokeWidth = 2f
                )

                // Pre-allocate paints for performance
                val textPaintVisible = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb((0.15f * 255f).toInt(), 0, 255, 255)
                    textSize = 12f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                val textPaintFog = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb((0.15f * 255f * 0.4f).toInt(), 0, 255, 255)
                    textSize = 12f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.MONOSPACE
                }

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
                            color = Color(0xFF8F9094).copy(alpha = 0.15f * alpha),
                            strokeWidth = 1f
                        )

                        // Movement range overlay (cyan fill)
                        if (reachableHexes.contains(tile.coord)) {
                            drawHexagonPath(
                                centerX = x, centerY = y, radius = hexRadius,
                                color = NeonCyan.copy(alpha = 0.18f), fill = true
                            )
                        }

                        // Attack range overlay (red outline on enemy units)
                        if (attackableCoords.contains(tile.coord)) {
                            drawHexagonPath(
                                centerX = x, centerY = y, radius = hexRadius,
                                color = NeonRed.copy(alpha = 0.55f), strokeWidth = 3f
                            )
                        }

                        // Sector ID (Blueprint style)
                        drawContext.canvas.nativeCanvas.drawText(
                            "${tile.coord.q},${tile.coord.r}",
                            x, y + hexRadius * 0.7f,
                            if (isVisible) textPaintVisible else textPaintFog
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

                        // Production indicator: small orange square on planet with active build order
                        if (tile.terrain == TerrainType.PLANET && buildingPlanets.contains(tile.coord)) {
                            val iconSize = hexRadius * 0.22f
                            val iconX = x + hexRadius * 0.45f
                            val iconY = y - hexRadius * 0.55f
                            drawRect(
                                color = NeonOrange.copy(alpha = 0.9f),
                                topLeft = Offset(iconX - iconSize / 2f, iconY - iconSize / 2f),
                                size = Size(iconSize, iconSize)
                            )
                        }

                        val unit = gameState.units[tile.coord]
                        if (unit != null && (isVisible || unit.faction == gameState.activeFaction)) {
                            // Skip the animating unit — it is drawn in the overlay canvas
                            val anim = movingUnitAnim
                            if (anim == null || anim.id != unit.id) {
                                drawUnit(x, y, unit)
                            }
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
            }

            // Dynamic overlay layer — ghost path and combat FX change on every drag
            // frame / animation tick. Keeping them in a separate Canvas means the
            // terrain loop above is not re-executed for those high-frequency updates.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                val hexRadius = HEX_RADIUS
                val hexWidth = sqrt(3f) * hexRadius
                val hexHeight = 2f * hexRadius
                val horizSpacing = hexWidth
                val vertSpacing = 3f / 4f * hexHeight

                ghostPath?.let { path ->
                    val start = dragStartHex
                    if (path.isNotEmpty() && start != null) {
                        var prevPoint = Offset(
                            centerX + horizSpacing * (start.q + start.r / 2f),
                            centerY + vertSpacing * start.r
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
                        val targetTile = gameState.map.tiles[target]
                        val highlightColor = when {
                            targetUnit != null && targetUnit.faction != gameState.activeFaction -> NeonRed
                            targetTile?.terrain == TerrainType.PLANET &&
                                targetTile.owner != null && targetTile.owner != gameState.activeFaction -> NeonOrange
                            else -> NeonCyan
                        }

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
                        val explosionRadius = hexRadius * explosionScale.value
                        if (explosionRadius > 0f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(NeonOrange.copy(alpha = 1f - explosionScale.value), Color.Transparent),
                                    center = Offset(dx, dy),
                                    radius = explosionRadius
                                ),
                                radius = explosionRadius,
                                center = Offset(dx, dy)
                            )
                        }
                    }
                }

                // Moving unit — drawn on top at its interpolated position
                val anim = movingUnitAnim
                if (anim != null) {
                    val fromX = centerX + horizSpacing * (anim.from.q + anim.from.r / 2f)
                    val fromY = centerY + vertSpacing * anim.from.r
                    val toX = centerX + horizSpacing * (anim.to.q + anim.to.r / 2f)
                    val toY = centerY + vertSpacing * anim.to.r
                    val t = movingProgress.value
                    val animX = fromX + (toX - fromX) * t
                    val animY = fromY + (toY - fromY) * t
                    if (t < 0.95f) {
                        drawLine(
                            color = NeonCyan.copy(alpha = 0.5f * (1f - t)),
                            start = Offset(fromX, fromY),
                            end = Offset(animX, animY),
                            strokeWidth = 3f
                        )
                    }
                    val animUnit = gameState.units[anim.to] ?: gameState.units[anim.from]
                    if (animUnit != null) drawUnit(animX, animY, animUnit)
                }
            }
        }

        // HUD overlay
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenAcademy) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = "Hero Academy", tint = NeonCyan)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("NOVA CONQUEST", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Credits + income preview
                IndustrialPanel(modifier = Modifier.padding(vertical = 4.dp), backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("$credits C", style = MaterialTheme.typography.labelLarge)
                            Text("+$incomePerTurn C/turn", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                        }
                    }
                }

                // Turn
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TURN", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Text(gameState.turn.toString(), style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                }

                // Active Faction (colored dot)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(activeFactionColor, shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(gameState.activeFaction.name, style = MaterialTheme.typography.labelLarge, color = activeFactionColor)
                }

                if (isAiThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = NeonOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Thinking...",
                            color = NeonOrange,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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

            Spacer(modifier = Modifier.width(16.dp))
        }

        // Side Contextual Action Bar
        selectedHex?.let { coord ->
            val tile = gameState.map.getTileAt(coord)
            val unitOnTile = gameState.units[coord]
            if (tile != null && combatPreviewData == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndustrialPanel(modifier = Modifier.width(220.dp).padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SECTOR ${coord.q},${coord.r}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                                IconButton(
                                    onClick = {
                                        selectedHex = null
                                        onClearSelection()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Deselect",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                Text(tile.terrain.name, style = MaterialTheme.typography.labelLarge)
                            }
                            if (tile.terrain == TerrainType.PLANET) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Owner", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    Text(tile.owner?.name ?: "NEUTRAL", style = MaterialTheme.typography.labelLarge, color = tile.owner?.let { getFactionColor(it) } ?: NeonGreen)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Level", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    Text(tile.systemLevel.toString(), style = MaterialTheme.typography.labelLarge)
                                }
                                if (tile.systemLevel > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Defense", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                        Text("${tile.systemLevel * 2} dmg/siege", style = MaterialTheme.typography.labelLarge, color = NeonOrange)
                                    }
                                }
                            }
                            if (unitOnTile != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                // Unit name + faction color
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(unitOnTile.type.name, style = MaterialTheme.typography.labelLarge, color = getFactionColor(unitOnTile.faction))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (unitOnTile.hasMoved) Text("MOVED", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        if (unitOnTile.hasAttacked) Text("FIRED", style = MaterialTheme.typography.labelSmall, color = NeonRed.copy(alpha = 0.8f))
                                    }
                                }
                                // HP bar
                                val hpFraction = unitOnTile.currentHp.toFloat() / unitOnTile.type.maxHp
                                val hpColor = when {
                                    hpFraction > 0.6f -> NeonGreen
                                    hpFraction > 0.3f -> NeonOrange
                                    else -> NeonRed
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(hpFraction).background(hpColor))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${unitOnTile.currentHp}/${unitOnTile.type.maxHp}", style = MaterialTheme.typography.labelSmall, color = hpColor)
                                }
                                // Stats row
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    StatChip("ATK", unitOnTile.type.attack.toString())
                                    StatChip("RNG", unitOnTile.type.range.toString())
                                    StatChip("MOV", if (unitOnTile.hasMoved) "0/${unitOnTile.type.movement}" else unitOnTile.type.movement.toString())
                                }
                            }
                        }
                    }

                    // System management — only for planets owned by the active faction
                    if (tile.terrain == TerrainType.PLANET && tile.owner == gameState.activeFaction) {
                        IndustrialPanel(modifier = Modifier.size(48.dp)) {
                            IconButton(onClick = { onOpenSystemManagement(coord) }, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Build, contentDescription = "Manage System", tint = NeonCyan)
                            }
                        }
                    }

                    // Siege / Capture — when a friendly unit is selected and adjacent enemy planet exists
                    if (unitOnTile != null && unitOnTile.faction == gameState.activeFaction && !unitOnTile.hasAttacked) {
                        val adjacentEnemyPlanets = HexCoord.directions
                            .map { coord + it }
                            .mapNotNull { gameState.map.tiles[it] }
                            .filter { it.terrain == TerrainType.PLANET && it.owner != null && it.owner != gameState.activeFaction }

                        adjacentEnemyPlanets.firstOrNull()?.let { enemyPlanet ->
                            if (enemyPlanet.systemLevel > 0) {
                                IndustrialPanel(modifier = Modifier.size(48.dp)) {
                                    IconButton(
                                        onClick = { onSiegePlanet(coord, enemyPlanet.coord) },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Siege Planet", tint = NeonOrange)
                                    }
                                }
                            } else {
                                IndustrialPanel(modifier = Modifier.size(48.dp)) {
                                    IconButton(
                                        onClick = { onCapturePlanet(coord, enemyPlanet.coord) },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Capture Planet", tint = NeonGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
            } else {
                // Stale preview (unit removed by AI between preview and confirm) — close it
                combatPreviewData = null
            }
        }
    }
}

fun pixelToHex(x: Float, y: Float, centerX: Float, centerY: Float): HexCoord {
    val q = (sqrt(3f) / 3 * (x - centerX) - 1f / 3 * (y - centerY)) / HEX_RADIUS
    val r = (2f / 3 * (y - centerY)) / HEX_RADIUS
    return hexRound(q.toDouble(), r.toDouble(), -q.toDouble() - r.toDouble())
}

fun DrawScope.drawPlanet(x: Float, y: Float, hexRadius: Float, owner: Faction?) {
    val planetColor = owner?.let { getFactionColor(it) } ?: NeonGreen // Neutral
    val atmosphereColor = planetColor.copy(alpha = 0.2f)

    // Core glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(planetColor.copy(alpha = 0.7f), atmosphereColor, Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.6f
        ),
        radius = hexRadius * 0.6f,
        center = Offset(x, y)
    )

    // Technical orbital rings
    val ringPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(
            Offset(x - hexRadius * 0.8f, y - hexRadius * 0.2f),
            Size(hexRadius * 1.6f, hexRadius * 0.4f)
        ))
    }
    drawPath(
        path = ringPath,
        color = NeonCyan.copy(alpha = 0.4f),
        style = Stroke(width = 1.5f)
    )

    // Inner details (industrial nodes/cities)
    for (i in 0 until 3) {
        val angle = (i * 120f) * (Math.PI / 180f)
        val dist = hexRadius * 0.2f
        drawCircle(
            color = planetColor,
            radius = 3f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }

    // Industrial border if owned
    if (owner != null) {
        drawCircle(
            color = planetColor.copy(alpha = 0.5f),
            radius = hexRadius * 0.45f,
            center = Offset(x, y),
            style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        )
    }
}

fun DrawScope.drawUnit(x: Float, y: Float, unit: GameUnit) {
    val factionColor = getFactionColor(unit.faction)
    val size = 25f
    
    when (unit.type) {
        UnitType.CRUISER, UnitType.BATTLESHIP -> {
            // Industrial ship body (Angular)
            val path = Path().apply {
                moveTo(x + size, y)
                lineTo(x - size * 0.5f, y - size * 0.7f)
                lineTo(x - size * 0.8f, y - size * 0.4f)
                lineTo(x - size * 0.8f, y + size * 0.4f)
                lineTo(x - size * 0.5f, y + size * 0.7f)
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 2.5f))
            drawPath(path, color = factionColor.copy(alpha = 0.2f), style = Fill)
            
            // Antennas / Sensors
            drawLine(factionColor, Offset(x + size * 0.2f, y - size * 0.5f), Offset(x + size * 0.2f, y - size * 1.2f), strokeWidth = 1.5f)
            drawCircle(factionColor, radius = 2f, center = Offset(x + size * 0.2f, y - size * 1.2f))
        }
        UnitType.FIGHTER -> {
            // Delta wing style
            val path = Path().apply {
                moveTo(x + size * 0.7f, y)
                lineTo(x - size * 0.7f, y - size * 0.6f)
                lineTo(x - size * 0.4f, y)
                lineTo(x - size * 0.7f, y + size * 0.6f)
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 2f))
            
            // Twin engines
            drawRect(factionColor, Offset(x - size * 0.8f, y - size * 0.4f), Size(8f, 4f))
            drawRect(factionColor, Offset(x - size * 0.8f, y + size * 0.3f), Size(8f, 4f))
        }
        UnitType.SCOUT -> {
            // Diamond technical shape
            val path = Path().apply {
                moveTo(x + size * 0.8f, y)
                lineTo(x, y - size * 0.4f)
                lineTo(x - size * 0.8f, y)
                lineTo(x, y + size * 0.4f)
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 2f))
            
            // Radar dish
            drawArc(
                color = factionColor,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(x - size * 0.3f, y - size * 0.3f),
                size = Size(size * 0.6f, size * 0.6f),
                style = Stroke(width = 1.5f)
            )
        }
        UnitType.CARRIER -> {
            // Large rectangular technical hull
            val path = Path().apply {
                moveTo(x + size, y - size * 0.4f)
                lineTo(x + size, y + size * 0.4f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size, y - size * 0.6f)
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 2.5f))
            
            // Flight deck lines
            drawLine(factionColor, Offset(x - size * 0.5f, y), Offset(x + size * 0.5f, y), strokeWidth = 1f)
        }
        UnitType.DREADNOUGHT -> {
            // Massive heavy hull
            val path = Path().apply {
                moveTo(x + size * 1.2f, y)
                lineTo(x - size * 0.2f, y - size * 0.8f)
                lineTo(x - size, y - size * 0.6f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size * 0.2f, y + size * 0.8f)
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 3.5f))
            drawPath(path, color = factionColor.copy(alpha = 0.3f), style = Fill)
            
            // Multiple turrets / antennas
            for (i in 0 until 3) {
                val tx = x - size * 0.5f + (i * size * 0.4f)
                drawCircle(factionColor, radius = 3f, center = Offset(tx, y))
                drawLine(factionColor, Offset(tx, y), Offset(tx, y - size * 0.3f), strokeWidth = 2f)
            }
        }
        UnitType.DEFENSE_PLATFORM -> {
            // Octagonal station structure
            val path = Path().apply {
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (Math.PI / 180f)
                    val r = size * 0.7f
                    val px = x + cos(angle).toFloat() * r
                    val py = y + sin(angle).toFloat() * r
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(path, color = factionColor, style = Stroke(width = 3f))
            
            // Core energy ring
            drawCircle(factionColor.copy(alpha = 0.4f), radius = size * 0.3f, center = Offset(x, y), style = Stroke(width = 2f))
            
            // Defense arrays (Solar/Shield)
            for (i in 0 until 4) {
                val angle = (i * 90f + 22.5f) * (Math.PI / 180f)
                val rx = x + cos(angle).toFloat() * size * 0.9f
                val ry = y + sin(angle).toFloat() * size * 0.9f
                drawRect(factionColor, Offset(rx - 4f, ry - 4f), Size(8f, 8f))
            }
        }
        else -> {
            // Fallback for any unknown units
            drawCircle(factionColor, radius = size * 0.5f, center = Offset(x, y), style = Stroke(width = 2f))
        }
    }

    // HP Bar (Technical overlay)
    val hpPercent = unit.currentHp.toFloat() / unit.type.maxHp
    val barWidth = 40f
    val barHeight = 4f
    val barTop = y + size + 10f
    
    drawRect(Color.Gray.copy(alpha = 0.3f), Offset(x - barWidth/2, barTop), Size(barWidth, barHeight))
    drawRect(factionColor, Offset(x - barWidth/2, barTop), Size(barWidth * hpPercent, barHeight))
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

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
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
