package com.novaempire.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.motionDelay
import com.novaempire.app.ui.components.motionMillis
import com.novaempire.app.ui.components.pointAlongPath
import com.novaempire.app.ui.components.rememberMotionLoop
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.*
import com.novaempire.core.domain.state.CombatEvent
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.AttackCalculator
import com.novaempire.core.engine.FleetActions
import com.novaempire.core.engine.GameGridMap
import com.novaempire.core.engine.IncomeCalculator
import com.novaempire.core.engine.MapAction
import com.novaempire.core.engine.MapInteraction
import com.novaempire.core.engine.MovementCalculator
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.novaempire.core.hex.HexLayout
import com.novaempire.core.hex.HexPathfinder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Hex radius in density-independent units — 30.dp reproduces the historical 60 px on a 2x screen. */
private val HEX_RADIUS_DP = 30.dp

/**
 * Zoom bounds. The floor used to be 0.5, which is not far enough out to fit a GIGANTIC galaxy
 * (radius 12 ≈ 1870 px of half-width at 3x density against a ~540 px screen half-width): the
 * player could never see their empire as a whole. 0.25 fits every map size on a phone.
 */
private const val MIN_MAP_SCALE = 0.25f
private const val MAX_MAP_SCALE = 3f

/** How much of the galaxy must stay on screen — panning can no longer lose the board entirely. */
private const val MAP_PAN_MARGIN_PX = 96f

/** Below this zoom the per-hex sector IDs are illegible, so drawing them is pure cost. */
private const val SECTOR_LABEL_MIN_SCALE = 0.9f

/** Débattement maximal de la secousse de caméra, à amplitude pleine. */
private val SHAKE_AMPLITUDE_DP = 5.dp

/** Durée de la secousse. Assez court pour ponctuer l'impact, trop court pour gêner la lecture. */
private const val SHAKE_DURATION_MS = 260

// Fréquences (en demi-tours) des deux axes de la secousse. Premières entre elles et paires, pour
// que le déplacement parte de zéro et y revienne exactement : une caméra qui se remet en place
// d'un coup sec se voit plus que la secousse elle-même.
private const val SHAKE_FREQ_X = 6f
private const val SHAKE_FREQ_Y = 4f

/** Durée par hex traversé, et bornes du total — un long trajet ne doit pas devenir une attente. */
private const val MOVE_MS_PER_HEX = 150
private const val MOVE_MS_MIN = 220
private const val MOVE_MS_MAX = 900

/** Levée du brouillard : le voile s'efface sur cette durée. */
private const val REVEAL_MS = 550

/** Onde de choc d'une prise ou d'un siège. */
private const val PLANET_FLASH_MS = 700

/**
 * Au-delà de tant de tuiles modifiées d'un coup, ce n'est pas une conquête mais un chargement de
 * partie ou un début de mission : illuminer la moitié de la galaxie n'apprendrait rien au joueur.
 */
private const val PLANET_FLASH_MAX_BATCH = 4

/** Unité détruite, conservée le temps de la montrer disparaître. */
private data class Wreck(val unit: GameUnit, val coord: HexCoord)

/**
 * Le chemin qu'une flotte vient d'emprunter, reconstruit sur l'état **d'avant** le déplacement.
 *
 * Rejoué avec le même A* et la même grille que `handleMoveUnit`, donc il retrouve le trajet du
 * moteur plutôt qu'un trajet plausible. L'état d'avant est indispensable : sur celui d'après,
 * l'arrivée est occupée par la flotte elle-même, `GameGridMap.isPassable` la déclare bloquée et A*
 * ne renvoie rien.
 *
 * Repli sur le saut direct quand aucun chemin n'existe — un déploiement depuis un transporteur ou
 * un saut par trou de ver ne *sont* pas des trajets pas à pas, et une animation ratée ne doit pas
 * empêcher l'unité d'arriver.
 */
private fun tracePath(previous: GameState, faction: Faction, from: HexCoord, to: HexCoord): List<HexCoord> {
    val steps = HexPathfinder.findPath(from, to, GameGridMap(previous, faction))
    return if (steps.isNullOrEmpty()) listOf(from, to) else listOf(from) + steps
}

/**
 * La fin de [path] que le joueur a le droit de voir : le plus long suffixe entièrement visible.
 *
 * Sans ce découpage, animer les déplacements de l'IA trahirait le brouillard de guerre — la couche
 * statique cache une flotte ennemie hors de vue, mais une trajectoire tracée depuis un hex non
 * observé montrerait justement d'où elle vient. Un suffixe d'un seul point veut dire « elle est
 * apparue au bord de la vision » : il n'y a alors rien à animer, la couche statique la dessine.
 */
internal fun visiblePathSuffix(path: List<HexCoord>, visible: Set<HexCoord>): List<HexCoord> {
    var start = path.size - 1
    while (start > 0 && path[start - 1] in visible) start--
    return path.subList(start, path.size)
}

/**
 * Une flotte en mouvement et le chemin qu'elle emprunte réellement.
 *
 * [path] part de l'hex de départ et inclut chaque étape : l'interpolation en ligne droite d'avant
 * faisait traverser les astéroïdes que la flotte contournait justement.
 */
private data class MovingUnitAnim(val unit: GameUnit, val path: List<HexCoord>)

/** Prise de planète (couleur du nouveau propriétaire) ou siège réussi (niveau perdu). */
private data class PlanetFlash(val coord: HexCoord, val color: Color)

// Neighbour steps named for where they land on screen (x = √3·R·(q + r/2), y = 1.5·R·r).
// A pointy-top hex has no neighbour straight above it, so the four arrows are mapped to the
// four "flat" directions and Shift reaches the two remaining diagonals.
private val HEX_STEP_EAST = HexCoord(1, 0, -1)
private val HEX_STEP_WEST = HexCoord(-1, 0, 1)
private val HEX_STEP_NORTH_WEST = HexCoord(0, -1, 1)
private val HEX_STEP_SOUTH_EAST = HexCoord(0, 1, -1)
private val HEX_STEP_NORTH_EAST = HexCoord(1, -1, 0)
private val HEX_STEP_SOUTH_WEST = HexCoord(-1, 1, 0)

/**
 * Camera (zoom + pan) for the tactical map.
 *
 * Hoisted out of [TacticalMapScreen] because the map Composable is swapped out whenever the
 * player visits the SYSTEM / TECH / INTEL tabs: a plain `remember` inside the screen threw the
 * camera away on every tab switch, dumping the player back on their capital at the default zoom
 * several times a turn. Owning it in the parent (which stays composed) keeps the view where the
 * player left it.
 */
@androidx.compose.runtime.Stable
class MapCameraState {
    /** Plain field, not snapshot state: [initializeOnce] must not re-run on recomposition. */
    private var initialized = false

    var scale by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    fun initializeOnce(initialScale: Float, initialPan: Offset) {
        if (initialized) return
        initialized = true
        scale = initialScale
        pan = initialPan
    }
}

/**
 * Screen pixel → hex, applying the map layer's transform by hand.
 *
 * The gesture detectors deliberately sit OUTSIDE the map's `graphicsLayer`. Inside it, Compose
 * reports pointer positions in pre-transform local pixels, which means a pan gesture feeds its
 * own output back into the next event's coordinates: the map then tracks the finger at
 * `1/(scale+1)` of its speed and a pinch cannot be anchored on the fingers at all. Outside the
 * layer the coordinates are stable, and the inverse of `screen = C + (local - C) * scale + pan`
 * is applied here instead.
 */
private fun screenToHex(
    offset: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pan: Offset,
    scale: Float,
    hexRadius: Float
): HexCoord = HexLayout.hexAtScreen(
    offset.x, offset.y, viewWidth, viewHeight, pan.x, pan.y, scale, hexRadius
)

/**
 * Clamps [pan] so the galaxy's bounding box always keeps [MAP_PAN_MARGIN_PX] of overlap with the
 * viewport. Without it a single fling left the player staring at empty space with no cue as to
 * which way the board went.
 */
private fun clampPan(
    pan: Offset,
    scale: Float,
    viewWidth: Float,
    viewHeight: Float,
    hexRadius: Float,
    mapRadius: Int
): Offset {
    if (mapRadius <= 0) return pan
    return Offset(
        HexLayout.clampPanAxis(
            pan.x, HexLayout.halfBoardWidth(hexRadius, mapRadius, scale), viewWidth, MAP_PAN_MARGIN_PX
        ),
        HexLayout.clampPanAxis(
            pan.y, HexLayout.halfBoardHeight(hexRadius, mapRadius, scale), viewHeight, MAP_PAN_MARGIN_PX
        )
    )
}

@Composable
fun TacticalMapScreen(
    isAiThinking: Boolean = false,
    gameState: GameState,
    visibleHexes: Set<HexCoord>,
    onHexClick: (HexCoord) -> Unit,
    onMoveUnit: (HexCoord, HexCoord) -> Unit,
    onAttackUnit: (HexCoord, HexCoord) -> Unit,
    onOpenSystemManagement: (HexCoord) -> Unit,
    onSiegePlanet: (HexCoord, HexCoord) -> Unit,
    onCapturePlanet: (HexCoord, HexCoord) -> Unit,
    onLoadUnit: (HexCoord, HexCoord) -> Unit = { _, _ -> },
    onDeployUnit: (HexCoord, HexCoord, Int) -> Unit = { _, _, _ -> },
    onOpenAcademy: () -> Unit,
    onClearSelection: () -> Unit,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    undoClosedByExploration: Boolean = false,
    /** Tirs résolus, à animer. Un flux, pas un champ d'état : voir CombatOutcome. */
    combatEvents: Flow<CombatEvent> = emptyFlow(),
    /** Secousses demandées par le moteur (`GameEffect.ShakeCamera`), une par impact. */
    shakeEvents: Flow<Unit> = emptyFlow(),
    // (coord, nonce): the Int is a monotonically increasing re-trigger counter — NOT a zoom
    // level — so LaunchedEffect(centerRequest) re-fires even when re-focusing the same coord.
    centerRequest: Pair<HexCoord, Int>? = null,
    initialSelectedHex: HexCoord? = null,
    combatLog: List<Pair<String, String>> = emptyList(),
    // Owned by the caller so zoom/pan survive a trip to the SYSTEM or TECH tab.
    camera: MapCameraState = remember { MapCameraState() },
    modifier: Modifier = Modifier
) {
    val initScale = 0.8f
    val initCoord = gameState.playerStates[gameState.humanFaction]?.capitalCoord
        ?: gameState.units.values.firstOrNull { it.faction == gameState.humanFaction }?.position
        ?: HexCoord(0, 0, 0)
    // DrawScope works in raw pixels, so a hard-coded radius made the whole map shrink as screen
    // density rose: 60 px is 30 dp at 2x but only 20 dp at 3x, and the sector labels became
    // unreadable. Deriving it from dp keeps the board the same physical size on every device.
    // This single value feeds both the drawing and the hit-testing (via HexLayout), so the two
    // cannot drift apart.
    val hexRadiusPx = with(LocalDensity.current) { HEX_RADIUS_DP.toPx() }
    val horizSpacingInit = sqrt(3f) * hexRadiusPx
    val vertSpacingInit = 1.5f * hexRadiusPx

    camera.initializeOnce(
        initScale,
        Offset(
            -horizSpacingInit * (initCoord.q + initCoord.r / 2f) * initScale,
            -vertSpacingInit * initCoord.r * initScale
        )
    )
    var selectedHex by remember { mutableStateOf(initialSelectedHex) }
    // Keyboard / D-pad cursor. Distinct from [selectedHex]: the cursor is where the player is
    // *looking*, the selection is what they have *committed to*, and the two-step
    // "select a fleet, then pick its target" flow needs both at once.
    var cursorHex by remember { mutableStateOf(initialSelectedHex) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // Only paint the cursor ring once the player has actually pressed a key: a touch player
    // shouldn't have a second outline following their taps around.
    var keyboardActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var combatPreviewData by remember { mutableStateOf<Pair<HexCoord, HexCoord>?>(null) }
    var siegePreviewData by remember { mutableStateOf<Triple<HexCoord, HexCoord, Boolean>?>(null) }
    var terrainTooltipCoord by remember { mutableStateOf<HexCoord?>(null) }
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
    val currentOnLoadUnit by rememberUpdatedState(onLoadUnit)
    val currentOnDeployUnit by rememberUpdatedState(onDeployUnit)
    // The gesture lambdas below are built once by `pointerInput(Unit)`, so any plain value they
    // capture is frozen at its first-frame value. Anything they must read live goes through
    // rememberUpdatedState.
    val currentIsAiThinking by rememberUpdatedState(isAiThinking)
    val currentOnHexClick by rememberUpdatedState(onHexClick)
    val currentOnClearSelection by rememberUpdatedState(onClearSelection)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentCanUndo by rememberUpdatedState(canUndo)
    // Le thème effectif est résolu une seule fois par NovaEmpireTheme. Lire ici
    // `gameState.themeConfig.currentTheme` brut court-circuitait la résolution saisonnière : la
    // carte se dessinait avec les réglages DEFAULT alors que l'interface était en HALLOWEEN.
    val graphicsConfig = com.novaempire.app.ui.theme.LocalGraphicsConfig.current
    val mapPalette = com.novaempire.app.ui.theme.LocalMapPalette.current
    val displaySettings = com.novaempire.app.settings.LocalDisplaySettings.current

    // Toutes les animations de jeu passent par ce drapeau — voir `Motion.kt`.
    val animationsOn = !displaySettings.reducedMotion
    // Les collecteurs `LaunchedEffect(Unit)` ci-dessous vivent aussi longtemps que l'écran : sans
    // `rememberUpdatedState`, basculer « Reduced Motion » en cours de partie resterait sans effet
    // sur le combat tant que la carte n'est pas quittée.
    val currentDisplaySettings by rememberUpdatedState(displaySettings)
    val currentAnimationsOn by rememberUpdatedState(animationsOn)

    val laserProgress = remember { Animatable(0f) }
    val explosionScale = remember { Animatable(0f) }
    var activeCombatEvent by remember { mutableStateOf<CombatEvent?>(null) }

    // Épave de l'unité détruite. Elle n'est plus dans `gameState.units` dès l'état suivant, donc la
    // garder ici est le seul moyen de la montrer mourir — et c'est bien un effet ponctuel, pas de
    // l'état de partie.
    var wreck by remember { mutableStateOf<Wreck?>(null) }
    // Dernière unité *vue* sur chaque hex, jamais purgée : l'effet de combat et le nouvel état
    // arrivent par deux chemins asynchrones distincts, donc l'ordre entre les deux n'est pas garanti.
    // Un registre qui n'oublie rien retrouve la victime quel que soit celui qui arrive le premier.
    val lastSeenUnits = remember { mutableMapOf<HexCoord, GameUnit>().apply { putAll(gameState.units) } }

    // Secousse de caméra. Amplitude décroissante 1 → 0 ; l'oscillation est dérivée de cette seule
    // valeur (voir SHAKE_*), ce qui évite une deuxième animation à synchroniser.
    val shakeDecay = remember { Animatable(0f) }
    val shakeAmplitudePx = with(LocalDensity.current) { SHAKE_AMPLITUDE_DP.toPx() }

    // Déplacements en cours. Une *liste* : pendant un tour d'IA plusieurs flottes bougent dans la
    // même transition d'état, et n'en animer qu'une téléportait toutes les autres.
    var movingUnits by remember { mutableStateOf<List<MovingUnitAnim>>(emptyList()) }
    val movingProgress = remember { Animatable(1f) }
    // L'état complet, pas seulement les unités : reconstruire le chemin parcouru demande la carte
    // *d'avant* le déplacement — sur celle d'après, l'arrivée est occupée par l'unité elle-même et
    // A* ne trouve plus rien.
    val prevState = remember { mutableStateOf(gameState) }

    // Hexs révélés à l'instant, à faire apparaître en fondu. `revealedBaseline` est ce qui était
    // déjà connu : le fondu ne porte que sur la différence, pas sur tout ce qui est exploré.
    var revealedHexes by remember { mutableStateOf<Set<HexCoord>>(emptySet()) }
    val revealProgress = remember { Animatable(1f) }
    // `null` = première composition : ce qui est déjà exploré n'est pas une découverte, et le
    // révéler en fondu à l'ouverture de la carte ferait clignoter la moitié de l'empire.
    val revealedBaseline = remember { mutableStateOf<Set<HexCoord>?>(null) }

    // Planètes qui viennent de changer de main ou de perdre un niveau (siège). Déduit de la carte
    // plutôt que d'un `GameEffect` : les prises de l'IA ne passent par aucun effet — son tour
    // remplace l'état d'un bloc — et un diff les attrape toutes.
    var planetFlashes by remember { mutableStateOf<List<PlanetFlash>>(emptyList()) }
    val planetFlashProgress = remember { Animatable(1f) }
    val prevTiles = remember { mutableStateOf(gameState.map.tiles) }

    // Seuil « compact » de Material : en dessous, la largeur ne permet pas une colonne latérale
    // sans manger le plateau.
    val isCompactWidth = LocalConfiguration.current.screenWidthDp < 600

    val haptic = LocalHapticFeedback.current
    val playerState = gameState.playerStates[gameState.activeFaction]
    val exploredHexes = playerState?.exploredHexes ?: emptySet()
    val credits = playerState?.credits ?: 0
    val rolledCredits by animateIntAsState(
        targetValue = credits,
        animationSpec = tween(displaySettings.motionMillis(600), easing = FastOutSlowInEasing),
        label = "Credits"
    )
    val activeFactionColor = getFactionColor(gameState.activeFaction)

    // Income preview comes from the shared IncomeCalculator — the same formula TurnManager grants
    // — instead of a hand-rolled copy that used a different base and mis-scoped event bonuses.
    val incomePerTurn = remember(
        gameState.map.tiles, gameState.playerStates, gameState.activeFaction,
        gameState.activeEvent, gameState.eventTargetFaction, gameState.units
    ) {
        IncomeCalculator.perTurn(gameState, gameState.activeFaction)
    }
    val buildingPlanets = remember(gameState.playerStates, gameState.activeFaction) {
        gameState.playerStates[gameState.activeFaction]?.buildQueue?.map { it.planetCoord }?.toSet() ?: emptySet()
    }

    // Hexes the selected unit can still move to (empty when no unit selected / already moved)
    val reachableHexes = remember(selectedHex, gameState.units, gameState.activeEvent, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasMoved) return@remember emptySet<HexCoord>()
        HexPathfinder.findReachable(
            sel, GameGridMap(gameState, gameState.activeFaction),
            MovementCalculator.remainingMovement(gameState, unit)
        )
    }
    // Same set, readable from the tap handler (see the note on rememberUpdatedState above).
    val currentReachableHexes by rememberUpdatedState(reachableHexes)

    // Enemy units the selected unit can attack this turn
    val attackableCoords = remember(selectedHex, gameState.units, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        gameState.units.values
            .filter { it.faction != gameState.activeFaction && sel.distanceTo(it.position) <= unit.type.range }
            .map { it.position }
            .toSet()
    }

    // The selected unit's full attack reach (every in-range hex), so the player can read its range.
    val attackRangeHexes = remember(selectedHex, gameState.units, gameState.map.tiles, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        // Enumerate the disc of radius `range` (at most 36 hexes) rather than filtering every
        // tile of the galaxy by distance — same result, independent of map size.
        val range = unit.type.range
        val hexes = HashSet<HexCoord>()
        for (dq in -range..range) {
            for (dr in maxOf(-range, -dq - range)..minOf(range, -dq + range)) {
                val coord = sel + HexCoord(dq, dr, -dq - dr)
                if (coord != sel && gameState.map.tiles.containsKey(coord)) hexes.add(coord)
            }
        }
        hexes
    }

    // Targets that cannot retaliate (attacker sits beyond their weapon range) — a free hit (see B3).
    val safeTargetCoords = remember(selectedHex, gameState.units, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        gameState.units.values
            .filter { it.faction != gameState.activeFaction &&
                sel.distanceTo(it.position) <= unit.type.range &&
                sel.distanceTo(it.position) > it.type.range }
            .map { it.position }
            .toSet()
    }

    // Adjacent enemy planets the selected unit can capture (level 0) or siege (level > 0) — see B2.
    val capturableCoords = remember(selectedHex, gameState.units, gameState.map.tiles, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        // Only the six neighbours are ever within capture reach — no need to walk every tile.
        HexCoord.directions
            .mapNotNull { gameState.map.tiles[sel + it] }
            .filter { it.terrain == TerrainType.PLANET && it.owner != gameState.activeFaction &&
                it.systemLevel == 0 }
            .map { it.coord }
            .toSet()
    }
    val siegeableCoords = remember(selectedHex, gameState.units, gameState.map.tiles, gameState.activeFaction) {
        val sel = selectedHex ?: return@remember emptySet<HexCoord>()
        val unit = gameState.units[sel] ?: return@remember emptySet<HexCoord>()
        if (unit.faction != gameState.activeFaction || unit.hasAttacked) return@remember emptySet<HexCoord>()
        HexCoord.directions
            .mapNotNull { gameState.map.tiles[sel + it] }
            .filter { it.terrain == TerrainType.PLANET && it.owner != gameState.activeFaction &&
                it.systemLevel > 0 }
            .map { it.coord }
            .toSet()
    }

    /**
     * Applies what a tap — or an Enter on the keyboard cursor — means for [coord].
     *
     * The *rules* (deselect / attack / siege / move / reselect) live in
     * [com.novaempire.core.engine.MapInteraction], a pure object covered by the JVM suite CI
     * runs; `:app` has no test source set, so leaving five interlocking branches in here meant
     * nothing could pin them down. What stays here is only the effects: UI state and callbacks.
     *
     * Shared by the pointer and keyboard paths on purpose. Every mutable value it touches is read
     * through a state holder, so the single instance captured by `pointerInput(Unit)` stays
     * correct across recompositions.
     */
    fun activateHex(coord: HexCoord) {
        val action = MapInteraction.activate(
            currentGameState, selectedHex, coord, currentReachableHexes
        )
        when (action) {
            is MapAction.OutsideMap -> Unit
            // Acting on a fog-of-war hex does nothing — give a light haptic tick so the input
            // doesn't feel dropped (A6).
            is MapAction.Unexplored ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            is MapAction.Deselect -> {
                selectedHex = null
                currentOnClearSelection()
            }
            is MapAction.Select -> {
                selectedHex = action.coord
                currentOnHexClick(action.coord)
            }
            is MapAction.Move -> {
                currentOnMoveUnit(action.from, action.to)
                selectedHex = null
                currentOnClearSelection()
            }
            is MapAction.PreviewCombat -> {
                combatPreviewData = Pair(action.attacker, action.defender)
                selectedHex = null
                currentOnClearSelection()
            }
            is MapAction.PreviewSiege -> {
                siegePreviewData = Triple(action.attacker, action.planet, action.isCapture)
                selectedHex = null
                currentOnClearSelection()
            }
        }
    }

    fun centerCameraOn(coord: HexCoord) {
        if (viewSize.width == 0 || viewSize.height == 0) return
        camera.pan = clampPan(
            Offset(
                HexLayout.panToCenterX(coord, hexRadiusPx, camera.scale),
                HexLayout.panToCenterY(coord, hexRadiusPx, camera.scale)
            ),
            camera.scale, viewSize.width.toFloat(), viewSize.height.toFloat(),
            hexRadiusPx, currentGameState.map.radius
        )
    }

    /** Moves the cursor, letting the camera follow only once it leaves the comfortable middle. */
    fun moveCursorTo(coord: HexCoord) {
        cursorHex = coord
        if (!HexLayout.isComfortablyVisible(
                coord, viewSize.width.toFloat(), viewSize.height.toFloat(),
                camera.pan.x, camera.pan.y, camera.scale, hexRadiusPx
            )
        ) {
            centerCameraOn(coord)
        }
    }

    /** One neighbour step for the keyboard / D-pad cursor. */
    fun stepCursor(direction: HexCoord) {
        val gs = currentGameState
        val current = cursorHex
        if (current == null) {
            // The very first key press just lands the cursor somewhere meaningful.
            val landing = selectedHex
                ?: gs.playerStates[gs.humanFaction]?.capitalCoord
                ?: gs.units.values.firstOrNull { it.faction == gs.activeFaction }?.position
            if (landing != null) moveCursorTo(landing)
            return
        }
        val next = current + direction
        if (gs.map.tiles.containsKey(next)) {
            moveCursorTo(next)
        } else {
            // Edge of the galaxy — a tick so the key press doesn't feel dropped.
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Collecté une fois pour la vie de l'écran : chaque tir émis est une animation, là où une clé
    // d'état ne rejouait pas deux échanges identiques d'affilée.
    LaunchedEffect(Unit) {
        combatEvents.collect { combat ->
            activeCombatEvent = combat
            // La victime doit être capturée *avant* que l'explosion ne commence : à ce moment-là
            // elle a déjà quitté l'état, ou elle est sur le point de le quitter.
            wreck = if (combat.targetDestroyed) {
                lastSeenUnits[combat.defenderCoord]?.let { Wreck(it, combat.defenderCoord) }
            } else null
            AudioManager.playSound(SoundType.COMBAT_LASER)
            laserProgress.snapTo(0f)
            explosionScale.snapTo(0f)

            laserProgress.animateTo(1f, animationSpec = tween(currentDisplaySettings.motionMillis(300)))
            AudioManager.playSound(SoundType.COMBAT_EXPLOSION)
            // L'épave se désintègre sur la même horloge que l'explosion : une seule animation à
            // suivre, donc aucun risque de voir les deux se désynchroniser.
            explosionScale.animateTo(1f, animationSpec = tween(currentDisplaySettings.motionMillis(400)))

            kotlinx.coroutines.delay(currentDisplaySettings.motionDelay(200))
            activeCombatEvent = null
            wreck = null
        }
    }

    LaunchedEffect(Unit) {
        shakeEvents.collect {
            if (!currentAnimationsOn) return@collect
            shakeDecay.snapTo(1f)
            shakeDecay.animateTo(0f, animationSpec = tween(SHAKE_DURATION_MS, easing = LinearEasing))
        }
    }

    LaunchedEffect(gameState.units) {
        val prev = prevState.value
        val curr = gameState.units
        curr.forEach { (coord, unit) -> lastSeenUnits[coord] = unit }
        val prevById = prev.units.values.associateBy { it.id }
        val moved = curr.values.mapNotNull { unit ->
            val from = prevById[unit.id]?.position
            if (from == null || from == unit.position) return@mapNotNull null
            // Même règle de visibilité que la couche statique des flottes, sinon une flotte
            // ennemie invisible se mettrait à voler sous les yeux du joueur.
            if (unit.faction != gameState.activeFaction && unit.position !in visibleHexes) {
                return@mapNotNull null
            }
            val full = tracePath(prev, unit.faction, from, unit.position)
            val shown = if (unit.faction == gameState.activeFaction) full
                        else visiblePathSuffix(full, visibleHexes)
            if (shown.size < 2) return@mapNotNull null
            MovingUnitAnim(unit, shown)
        }
        prevState.value = gameState
        if (moved.isNotEmpty()) {
            movingUnits = moved
            movingProgress.snapTo(0f)
            // Une seule horloge pour toutes les flottes : chacune parcourt sa propre polyligne à la
            // même fraction, donc les trajets courts arrivent avant les longs sans coordination.
            val longest = moved.maxOf { it.path.size - 1 }.coerceAtLeast(1)
            val duration = (MOVE_MS_PER_HEX * longest).coerceIn(MOVE_MS_MIN, MOVE_MS_MAX)
            try {
                movingProgress.animateTo(
                    1f,
                    animationSpec = tween(displaySettings.motionMillis(duration), easing = FastOutSlowInEasing)
                )
            } finally {
                // Un nouvel état pendant l'animation annule cet effet : sans ce `finally`, les
                // flottes concernées resteraient marquées « en vol » et la couche statique
                // continuerait à ne pas les dessiner — invisibles jusqu'à la fin de la partie.
                // Le test d'identité évite d'effacer une animation que le tour suivant a déjà
                // installée à notre place.
                if (movingUnits === moved) movingUnits = emptyList()
            }
        }
    }

    // Levée du brouillard. Le voile s'efface dans la couche d'animation, pas dans celle du terrain :
    // faire varier la tuile elle-même obligerait à redessiner toute la galaxie à chaque frame.
    LaunchedEffect(exploredHexes) {
        val baseline = revealedBaseline.value
        revealedBaseline.value = exploredHexes
        if (baseline == null) return@LaunchedEffect
        val fresh = exploredHexes - baseline
        if (fresh.isNotEmpty() && animationsOn) {
            revealedHexes = fresh
            revealProgress.snapTo(0f)
            try {
                revealProgress.animateTo(1f, animationSpec = tween(REVEAL_MS, easing = LinearEasing))
            } finally {
                // Sans ce `finally`, une annulation en cours de fondu laisserait un voile opaque
                // figé sur les hexs concernés, définitivement.
                if (revealedHexes === fresh) revealedHexes = emptySet()
            }
        }
    }

    LaunchedEffect(gameState.map.tiles) {
        val previous = prevTiles.value
        val changed = gameState.map.tiles.values.mapNotNull { tile ->
            val old = previous[tile.coord] ?: return@mapNotNull null
            when {
                old.owner != tile.owner && tile.owner != null ->
                    PlanetFlash(tile.coord, getFactionColor(tile.owner!!))
                // Un siège fait chuter le niveau du système sans changer le drapeau.
                old.systemLevel > tile.systemLevel -> PlanetFlash(tile.coord, NeonOrange)
                else -> null
            }
        }
        prevTiles.value = gameState.map.tiles
        if (changed.isNotEmpty() && changed.size <= PLANET_FLASH_MAX_BATCH && animationsOn) {
            planetFlashes = changed
            planetFlashProgress.snapTo(0f)
            try {
                planetFlashProgress.animateTo(1f, animationSpec = tween(PLANET_FLASH_MS, easing = LinearEasing))
            } finally {
                if (planetFlashes === changed) planetFlashes = emptyList()
            }
        }
    }

    // Center map on a coord when requested by SMART FOCUS
    LaunchedEffect(centerRequest) {
        centerRequest?.let { (coord, _) ->  // second component is the re-trigger nonce, intentionally unused here
            val hSpacing = sqrt(3f) * hexRadiusPx
            val vSpacing = 1.5f * hexRadiusPx
            camera.pan = Offset(
                -hSpacing * (coord.q + coord.r / 2f) * camera.scale,
                -vSpacing * coord.r * camera.scale
            )
        }
    }

    // Spoken by TalkBack on every cursor move — see the semantics block on the map below.
    val cursorDescription = remember(cursorHex, selectedHex, gameState, reachableHexes) {
        describeHexForAccessibility(gameState, cursorHex, selectedHex, reachableHexes)
    }

    LaunchedEffect(Unit) {
        // Best-effort: lets a keyboard or D-pad drive the map immediately, and takes nothing away
        // from touch. Throws harmlessly if the node isn't attached yet, hence runCatching.
        runCatching { focusRequester.requestFocus() }
    }

    // Les deux boucles continues s'arrêtent vraiment en mouvement réduit : une durée nulle ferait
    // tourner `infiniteRepeatable` à vide, image par image, pour un résultat immobile.
    val sweepProgress = rememberMotionLoop(
        enabled = animationsOn, durationMillis = 4000, label = "ScanlineSweep"
    )
    // Au repos, le halo reste à mi-course : les flottes qui ont encore un ordre à donner restent
    // désignées, elles ne clignotent simplement plus.
    val pulseProgress = rememberMotionLoop(
        enabled = animationsOn,
        durationMillis = 900,
        repeatMode = RepeatMode.Reverse,
        restValue = 0.5f,
        label = "UnitPulse"
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Gesture layer. The detectors sit OUTSIDE the map's graphicsLayer (see [screenToHex]):
        // inside it, every pan changed the coordinate system the next pointer event is reported
        // in, so the map lagged the finger by a factor of 1/(scale+1) and pinch-zoom could not be
        // anchored on the fingers. Screen coordinates are converted to hexes by hand instead.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                // Accessibility. The board is a bare Canvas, so without a keyboard/D-pad cursor
                // and a spoken description of what it sits on, the map is unusable by anyone who
                // can't see it or can't point precisely — the game's largest remaining gap.
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || currentIsAiThinking) {
                        return@onKeyEvent false
                    }
                    val handled = when (event.key) {
                        Key.DirectionLeft -> {
                            stepCursor(if (event.isShiftPressed) HEX_STEP_SOUTH_WEST else HEX_STEP_WEST)
                            true
                        }
                        Key.DirectionRight -> {
                            stepCursor(if (event.isShiftPressed) HEX_STEP_NORTH_EAST else HEX_STEP_EAST)
                            true
                        }
                        Key.DirectionUp -> { stepCursor(HEX_STEP_NORTH_WEST); true }
                        Key.DirectionDown -> { stepCursor(HEX_STEP_SOUTH_EAST); true }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter -> {
                            cursorHex?.let { activateHex(it) }
                            true
                        }
                        Key.Escape -> {
                            selectedHex = null
                            currentOnClearSelection()
                            true
                        }
                        Key.Z -> {
                            // Ctrl+Z only: a bare Z would fight any future letter shortcut.
                            if (event.isCtrlPressed && currentCanUndo) {
                                currentOnUndo()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    if (handled) keyboardActive = true
                    handled
                }
                .semantics {
                    contentDescription = cursorDescription
                    liveRegion = LiveRegionMode.Polite
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoom, _ ->
                        // A fleet drag owns the gesture — panning underneath it would slide the
                        // board out from under the ghost path.
                        if (dragStartHex != null) return@detectTransformGestures
                        val viewW = size.width.toFloat()
                        val viewH = size.height.toFloat()
                        val oldScale = camera.scale
                        val newScale = (oldScale * zoom).coerceIn(MIN_MAP_SCALE, MAX_MAP_SCALE)
                        // Hold the hex under the pinch centroid still: solving
                        // `screen = C + (local - C) * scale + pan` for a fixed `local` gives
                        // `pan' = pan + (centroid - C - pan) * (1 - newScale / oldScale)`.
                        val zoomedPan = Offset(
                            HexLayout.focalPan(camera.pan.x, centroid.x, viewW / 2f, oldScale, newScale),
                            HexLayout.focalPan(camera.pan.y, centroid.y, viewH / 2f, oldScale, newScale)
                        )
                        camera.scale = newScale
                        camera.pan = clampPan(
                            zoomedPan + panChange, newScale, viewW, viewH,
                            hexRadiusPx, currentGameState.map.radius
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            val coord = screenToHex(
                                offset, size.width.toFloat(), size.height.toFloat(),
                                camera.pan, camera.scale, hexRadiusPx
                            )
                            val gs = currentGameState
                            val explored = gs.playerStates[gs.activeFaction]?.exploredHexes ?: emptySet()
                            // A long press on one of your own mobile fleets is the start of a
                            // drag-to-move. Both detectors fire off independent timers, so without
                            // this guard the full-screen terrain sheet also opened and then sat on
                            // top of the map for the whole drag, swallowing the drop.
                            val unitHere = gs.units[coord]
                            val startsFleetDrag = !currentIsAiThinking && unitHere != null &&
                                unitHere.faction == gs.activeFaction && !unitHere.hasMoved
                            if (!startsFleetDrag && gs.map.tiles.containsKey(coord) && explored.contains(coord)) {
                                terrainTooltipCoord = coord
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    ) { offset ->
                        terrainTooltipCoord = null
                        // The reducer rejects every intent while the AI plays, so letting taps
                        // through only queued up "AI is thinking, please wait." snackbars. Pan and
                        // zoom stay live so the player can follow the AI's turn.
                        if (currentIsAiThinking) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                        val coord = screenToHex(
                            offset, size.width.toFloat(), size.height.toFloat(),
                            camera.pan, camera.scale, hexRadiusPx
                        )
                        // Keep the keyboard cursor on the last hex touched, so switching between
                        // finger and keyboard mid-turn doesn't teleport it.
                        cursorHex = coord
                        keyboardActive = false
                        activateHex(coord)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val coord = screenToHex(
                                offset, size.width.toFloat(), size.height.toFloat(),
                                camera.pan, camera.scale, hexRadiusPx
                            )
                            val gs = currentGameState
                            val unit = gs.units[coord]
                            if (!currentIsAiThinking && unit != null &&
                                unit.faction == gs.activeFaction && !unit.hasMoved) {
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
                                // Third copy of the selection rules, gone: onDragStart already set
                                // `selectedHex = start`, and the ghost path is only ever built to a
                                // hex the fleet can reach or a target in weapon range, so dropping
                                // on `path.last()` means exactly what activating it means.
                                activateHex(path.last())
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
                            val start = dragStartHex ?: return@detectDragGesturesAfterLongPress
                            val gs = currentGameState
                            val unit = gs.units[start] ?: return@detectDragGesturesAfterLongPress
                            val coord = screenToHex(
                                change.position, size.width.toFloat(), size.height.toFloat(),
                                camera.pan, camera.scale, hexRadiusPx
                            )
                            if (coord == currentHoveredHex) return@detectDragGesturesAfterLongPress
                            currentHoveredHex = coord

                            if (coord == start || !gs.map.tiles.containsKey(coord)) {
                                ghostPath = null
                                return@detectDragGesturesAfterLongPress
                            }

                            val gridMap = GameGridMap(gs, gs.activeFaction)
                            val targetUnit = gs.units[coord]
                            val path = if (targetUnit != null && targetUnit.faction != gs.activeFaction) {
                                if (start.distanceTo(coord) <= unit.type.range) listOf(coord) else null
                            } else {
                                HexPathfinder.findPath(
                                    start, coord, gridMap,
                                    maxCost = MovementCalculator.remainingMovement(gs, unit)
                                )
                            }

                            if (path != null && path != ghostPath) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            ghostPath = path
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = camera.scale
                        scaleY = camera.scale
                        // La secousse est ajoutée ici et jamais écrite dans `camera.pan` : le pan
                        // est ce que le joueur a réglé, il doit se retrouver intact une fois
                        // l'impact passé — et le clamp de `clampPan` n'a pas à arbitrer un
                        // déplacement qui n'est pas un déplacement.
                        val decay = shakeDecay.value
                        translationX = camera.pan.x +
                            if (decay > 0f) sin(decay * PI.toFloat() * SHAKE_FREQ_X) * shakeAmplitudePx * decay else 0f
                        translationY = camera.pan.y +
                            if (decay > 0f) sin(decay * PI.toFloat() * SHAKE_FREQ_Y) * shakeAmplitudePx * decay else 0f
                    }
            ) {
                // Terrain layer. It carries its own graphicsLayer: sibling draw modifiers that have
                // no layer of their own all record into the nearest enclosing one, so the 60 fps
                // scanline in the animation layer below was forcing this whole tile loop to be
                // re-recorded every single frame.
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) {
                    val width = size.width
                    val height = size.height
                    val centerX = width / 2f
                    val centerY = height / 2f

                    val hexRadius = hexRadiusPx
                    val hexWidth = sqrt(3f) * hexRadius
                    val hexHeight = 2f * hexRadius
                    val horizSpacing = hexWidth
                    val vertSpacing = 3f / 4f * hexHeight

                    // Viewport culling. The tile loop used to walk the whole galaxy on every draw
                    // (469 tiles on GIGANTIC) even though a phone shows ~80 of them at normal zoom.
                    // Inverting the layer transform gives the slice of the pre-transform plane that
                    // is actually on screen; everything outside it is skipped.
                    val cullPad = hexRadius * 1.2f
                    val minLocalX = centerX + (0f - centerX - camera.pan.x) / camera.scale - cullPad
                    val maxLocalX = centerX + (width - centerX - camera.pan.x) / camera.scale + cullPad
                    val minLocalY = centerY + (0f - centerY - camera.pan.y) / camera.scale - cullPad
                    val maxLocalY = centerY + (height - centerY - camera.pan.y) / camera.scale + cullPad

                    val drawSectorLabels = camera.scale >= SECTOR_LABEL_MIN_SCALE

                    // Pre-allocate paints for performance
                    val textPaintVisible = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((0.12f * 255f).toInt(), 74, 123, 157) // acier froid
                        textSize = hexRadius * 0.18f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    val textPaintFog = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((0.06f * 255f).toInt(), 74, 123, 157)
                        textSize = hexRadius * 0.18f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.MONOSPACE
                    }

                    gameState.map.tiles.values.forEach { tile ->
                        val q = tile.coord.q
                        val r = tile.coord.r
                        val x = centerX + horizSpacing * (q + r / 2f)
                        val y = centerY + vertSpacing * r
                        if (x < minLocalX || x > maxLocalX || y < minLocalY || y > maxLocalY) return@forEach

                        val isVisible = visibleHexes.contains(tile.coord)
                        val isExplored = exploredHexes.contains(tile.coord)

                        if (isExplored) {
                            val baseColor = mapPalette.terrainColor(tile.terrain)

                            val alpha = if (isVisible) 1f else 0.4f

                            drawHexagonPath(
                                centerX = x, centerY = y, radius = hexRadius,
                                color = baseColor.copy(alpha = alpha), fill = true
                            )

                            drawHexagonPath(
                                centerX = x, centerY = y, radius = hexRadius,
                                // En contraste élevé le contour reste opaque et s'épaissit : c'est
                                // lui qui porte la lisibilité de la grille, surtout sur les tuiles
                                // estompées par le brouillard.
                                color = mapPalette.ink.copy(
                                    alpha = if (displaySettings.highContrast) 1f else alpha * 0.85f
                                ),
                                strokeWidth = if (displaySettings.highContrast) 4f else 2.5f
                            )

                            // Selection-dependent overlays (movement/attack range, targets, selected
                            // outline) and the fleets themselves live in the layers below, so
                            // neither selecting a unit nor moving one invalidates the terrain.

                            // Sector ID (Blueprint style) — one native text draw per tile is the most
                            // expensive thing in this loop, and below ~0.9x the glyphs are sub-pixel
                            // noise, so it is skipped when zoomed out.
                            if (drawSectorLabels) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${tile.coord.q},${tile.coord.r}",
                                    x, y + hexRadius * 0.7f,
                                    if (isVisible) textPaintVisible else textPaintFog
                                )
                            }

                            when (tile.terrain) {
                                TerrainType.PLANET -> drawPlanet(x, y, hexRadius, tile.owner, graphicsConfig, mapPalette)
                                TerrainType.ASTEROIDS -> drawAsteroids(x, y, hexRadius, mapPalette)
                                TerrainType.NEBULA -> drawNebula(x, y, hexRadius, mapPalette)
                                TerrainType.BLACK_HOLE -> drawBlackHole(x, y, hexRadius, mapPalette)
                                TerrainType.WORMHOLE -> drawWormhole(x, y, hexRadius, mapPalette)
                                TerrainType.PLASMA_CLOUD -> drawPlasmaCloud(x, y, hexRadius, mapPalette)
                                TerrainType.ION_STORM -> drawIonStorm(x, y, hexRadius, mapPalette)
                                TerrainType.ANOMALY -> drawAnomaly(x, y, hexRadius, mapPalette)
                                TerrainType.EMPTY -> {}
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

                            // Fleets are drawn by the layer below: keeping them here meant every
                            // single move repainted all of the terrain.
                        } else {
                            drawHexagonPath(x, y, hexRadius, color = mapPalette.unexplored, fill = true)
                            drawHexagonPath(
                                x, y, hexRadius, color = mapPalette.ink, fill = false,
                                strokeWidth = if (displaySettings.highContrast) 4f else 2.5f
                            )
                        }
                    }
                }

                // Fleet + selection layer, with its own graphicsLayer like the terrain above.
                // Splitting it out of the animation layer below matters: the scanline and the idle
                // pulse invalidate on every frame, and every unit sprite is a dozen path fills, so
                // co-locating them meant redrawing the whole fleet at 60 fps for nothing.
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) {
                    val width = size.width
                    val height = size.height
                    val centerX = width / 2f
                    val centerY = height / 2f

                    val hexRadius = hexRadiusPx
                    val hexWidth = sqrt(3f) * hexRadius
                    val hexHeight = 2f * hexRadius
                    val horizSpacing = hexWidth
                    val vertSpacing = 3f / 4f * hexHeight

                    val cullPad = hexRadius * 1.5f
                    val minLocalX = centerX + (0f - centerX - camera.pan.x) / camera.scale - cullPad
                    val maxLocalX = centerX + (width - centerX - camera.pan.x) / camera.scale + cullPad
                    val minLocalY = centerY + (0f - centerY - camera.pan.y) / camera.scale - cullPad
                    val maxLocalY = centerY + (height - centerY - camera.pan.y) / camera.scale + cullPad
                    fun onScreen(hx: Float, hy: Float) =
                        hx >= minLocalX && hx <= maxLocalX && hy >= minLocalY && hy <= maxLocalY

                    // Selection-dependent overlays (moved off the terrain layer — O2). Iterating the
                    // precomputed coord sets is cheaper than scanning every tile, and it keeps the
                    // terrain Canvas from invalidating when the player just selects a unit.
                    fun overlayHex(coord: HexCoord, color: Color, fill: Boolean, strokeWidth: Float = 2f) {
                        // Keep overlays out of the fog of war (matches the pre-O2 in-loop behaviour).
                        if (coord !in exploredHexes) return
                        val hx = centerX + horizSpacing * (coord.q + coord.r / 2f)
                        val hy = centerY + vertSpacing * coord.r
                        if (!onScreen(hx, hy)) return
                        drawHexagonPath(centerX = hx, centerY = hy, radius = hexRadius, color = color, fill = fill, strokeWidth = strokeWidth)
                    }
                    reachableHexes.forEach { overlayHex(it, NeonCyan.copy(alpha = 0.25f), fill = true) }
                    reachableHexes.forEach { overlayHex(it, NeonCyan.copy(alpha = 0.55f), fill = false, strokeWidth = 2f) }
                    attackRangeHexes.forEach { overlayHex(it, NeonRed.copy(alpha = 0.10f), fill = true) }
                    safeTargetCoords.forEach { overlayHex(it, NeonGreen.copy(alpha = 0.85f), fill = false, strokeWidth = 3.5f) }
                    attackableCoords.forEach { if (it !in safeTargetCoords) overlayHex(it, NeonRed.copy(alpha = 0.55f), fill = false, strokeWidth = 3f) }
                    capturableCoords.forEach { overlayHex(it, NeonGold.copy(alpha = 0.85f), fill = false, strokeWidth = 3.5f) }
                    siegeableCoords.forEach { if (it !in capturableCoords) overlayHex(it, NeonOrange.copy(alpha = 0.85f), fill = false, strokeWidth = 3f) }
                    selectedHex?.let { overlayHex(it, NeonCyan, fill = false, strokeWidth = 4f) }

                    // Keyboard / D-pad cursor. Drawn directly rather than through overlayHex so it
                    // stays visible inside the fog of war — losing the cursor is exactly the
                    // failure mode a keyboard player cannot recover from.
                    if (keyboardActive) {
                        cursorHex?.let { cursor ->
                            val cx = centerX + horizSpacing * (cursor.q + cursor.r / 2f)
                            val cy = centerY + vertSpacing * cursor.r
                            if (onScreen(cx, cy)) {
                                drawHexagonPath(
                                    centerX = cx, centerY = cy, radius = hexRadius * 0.9f,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fill = false, strokeWidth = 2.5f
                                )
                            }
                        }
                    }

                    // Fleets. Drawn after the overlays so a sprite is never tinted by a range wash.
                    val animatedUnitIds = movingUnits.mapTo(mutableSetOf()) { it.unit.id }
                    gameState.units.values.forEach { unit ->
                        if (unit.faction != gameState.activeFaction && !visibleHexes.contains(unit.position)) return@forEach
                        // The unit in flight is drawn at its interpolated position by the animation
                        // layer; drawing it here too showed the ship in two places at once for the
                        // 350 ms of the move.
                        if (unit.id in animatedUnitIds) return@forEach
                        val ux = centerX + horizSpacing * (unit.position.q + unit.position.r / 2f)
                        val uy = centerY + vertSpacing * unit.position.r
                        if (!onScreen(ux, uy)) return@forEach
                        drawUnit(ux, uy, unit, hexRadius, mapPalette)
                    }
                }

                // Animation layer — scanline, idle pulse, ghost path, combat FX and the unit in
                // flight. Everything here invalidates every frame by design, so nothing static lives
                // in it. No graphicsLayer: this is the layer doing the invalidating.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    val hexRadius = hexRadiusPx
                    val horizSpacing = sqrt(3f) * hexRadius
                    val vertSpacing = 1.5f * hexRadius

                    // Levée du brouillard : le voile part opaque et s'efface, donc le terrain
                    // dessous — déjà peint par la couche statique — se révèle sans qu'elle bouge.
                    if (revealedHexes.isNotEmpty()) {
                        val fade = 1f - revealProgress.value
                        revealedHexes.forEach { coord ->
                            val rx = centerX + horizSpacing * (coord.q + coord.r / 2f)
                            val ry = centerY + vertSpacing * coord.r
                            drawHexagonPath(
                                centerX = rx, centerY = ry, radius = hexRadius,
                                color = mapPalette.unexplored.copy(alpha = fade), fill = true
                            )
                            // Liseré qui s'allume puis retombe : c'est lui qui dit « ceci vient
                            // d'être découvert », le fondu seul passe inaperçu sur un hex vide.
                            drawHexagonPath(
                                centerX = rx, centerY = ry, radius = hexRadius,
                                color = NeonCyan.copy(alpha = fade * 0.7f), fill = false, strokeWidth = 2.5f
                            )
                        }
                    }

                    // Prise de planète / siège : une onde qui s'écarte et s'éteint.
                    if (planetFlashes.isNotEmpty()) {
                        val t = planetFlashProgress.value
                        planetFlashes.forEach { flash ->
                            if (flash.coord !in exploredHexes) return@forEach
                            val fx = centerX + horizSpacing * (flash.coord.q + flash.coord.r / 2f)
                            val fy = centerY + vertSpacing * flash.coord.r
                            drawCircle(
                                color = flash.color.copy(alpha = (1f - t) * 0.8f),
                                radius = hexRadius * (0.5f + t * 1.3f),
                                center = Offset(fx, fy),
                                style = Stroke(width = 3f + (1f - t) * 4f)
                            )
                            drawHexagonPath(
                                centerX = fx, centerY = fy, radius = hexRadius,
                                color = flash.color.copy(alpha = (1f - t) * 0.35f), fill = true
                            )
                        }
                    }

                    // Blueprint scanline sweep (animation — lives here to avoid terrain redraw).
                    // Purement décoratif : coupé avec les effets holographiques.
                    if (displaySettings.holographicEffects) {
                        val scanlineY = sweepProgress.value * size.height
                        drawLine(
                            color = NeonCyan.copy(alpha = 0.10f),
                            start = Offset(-size.width, scanlineY - size.height / 2f),
                            end = Offset(size.width, scanlineY - size.height / 2f),
                            strokeWidth = 2f
                        )
                    }

                    // Pulsing halo on units that still have actions available this turn
                    val pulseAlpha = 0.25f + pulseProgress.value * 0.45f
                    val pulseStroke = 2f + pulseProgress.value * 2.5f
                    gameState.units.values.forEach { unit ->
                        if (unit.faction == gameState.activeFaction &&
                            FleetActions.hasActionsLeft(gameState, unit)
                        ) {
                            val ux = centerX + horizSpacing * (unit.position.q + unit.position.r / 2f)
                            val uy = centerY + vertSpacing * unit.position.r
                            drawCircle(
                                color = NeonGreen.copy(alpha = pulseAlpha),
                                radius = hexRadius * 0.44f,
                                center = Offset(ux, uy),
                                style = Stroke(width = pulseStroke)
                            )
                        }
                    }

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

                    // Épave — dessinée avant l'explosion, donc en dessous d'elle. Le vaisseau
                    // détruit s'écrasait jusqu'ici sur le frame suivant, sans transition : la
                    // seule trace de sa mort était une phrase dans le journal.
                    wreck?.let { dead ->
                        val wx = centerX + horizSpacing * (dead.coord.q + dead.coord.r / 2f)
                        val wy = centerY + vertSpacing * dead.coord.r
                        // Rétréci jusqu'à zéro plutôt qu'estompé : `drawUnit` peint une dizaine de
                        // couches d'encre, leur appliquer une opacité commune demanderait un
                        // `saveLayer` par frame là où une échelle ne coûte rien.
                        val shrink = 1f - explosionScale.value
                        if (shrink > 0.02f) {
                            withTransform({
                                rotate(degrees = explosionScale.value * 140f, pivot = Offset(wx, wy))
                                scale(scaleX = shrink, scaleY = shrink, pivot = Offset(wx, wy))
                            }) {
                                drawUnit(wx, wy, dead.unit, hexRadius, mapPalette)
                            }
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
                                // Éclaboussure encre Bilal — fumée brune, pas néon
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to mapPalette.explosionCore.copy(alpha = (1f - explosionScale.value) * 0.95f),
                                            0.4f to mapPalette.explosionMid.copy(alpha = (0.7f - explosionScale.value).coerceAtLeast(0f)),
                                            0.8f to mapPalette.explosionEdge.copy(alpha = (0.3f - explosionScale.value).coerceAtLeast(0f)),
                                            1.0f to Color.Transparent
                                        ),
                                        center = Offset(dx, dy),
                                        radius = explosionRadius
                                    ),
                                    radius = explosionRadius,
                                    center = Offset(dx, dy)
                                )
                                drawExplosionShards(
                                    centerX = dx,
                                    centerY = dy,
                                    radius = explosionRadius,
                                    progress = explosionScale.value,
                                    multiplier = graphicsConfig.particleCountMultiplier
                                )
                            }
                        }
                    }

                    // Flottes en vol — dessinées par-dessus tout le reste, à leur position
                    // interpolée le long du chemin réellement emprunté.
                    if (movingUnits.isNotEmpty()) {
                        val t = movingProgress.value
                        movingUnits.forEach { anim ->
                            val points = anim.path.map { coord ->
                                Offset(
                                    centerX + horizSpacing * (coord.q + coord.r / 2f),
                                    centerY + vertSpacing * coord.r
                                )
                            }
                            val position = pointAlongPath(points, t)

                            // Sillage : les segments déjà franchis, puis le bout de segment en
                            // cours. Il dit d'où vient la flotte, ce que la seule position finale
                            // ne raconte pas quand plusieurs bougent en même temps.
                            if (t < 0.95f) {
                                val trailAlpha = 0.5f * (1f - t)
                                val segments = points.size - 1
                                val reached = (t * segments).toInt().coerceAtMost(segments - 1)
                                for (i in 0 until reached) {
                                    drawLine(
                                        color = NeonCyan.copy(alpha = trailAlpha),
                                        start = points[i], end = points[i + 1], strokeWidth = 3f
                                    )
                                }
                                drawLine(
                                    color = NeonCyan.copy(alpha = trailAlpha),
                                    start = points[reached], end = position, strokeWidth = 3f
                                )
                            }
                            drawUnit(position.x, position.y, anim.unit, hexRadius, mapPalette)
                        }
                    }
                }
            }
        }

        // HUD overlay
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenAcademy, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = "Hero Academy", tint = NeonCyan)
                }
                IconButton(onClick = {
                    camera.scale = initScale
                    camera.pan = Offset(
                        -horizSpacingInit * (initCoord.q + initCoord.r / 2f) * initScale,
                        -vertSpacingInit * initCoord.r * initScale
                    )
                }, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset view", tint = NeonCyan)
                }
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo && !isAiThinking,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        // Un éclaireur avance presque toujours dans le brouillard, donc ce bouton
                        // s'éteint souvent. Dire *pourquoi* évite que la règle passe pour un bug.
                        contentDescription = when {
                            canUndo -> "Annuler la dernière action"
                            undoClosedByExploration ->
                                "Annulation impossible : la dernière action a découvert du terrain"
                            else -> "Rien à annuler"
                        },
                        tint = if (canUndo && !isAiThinking) NeonOrange else TextSecondary.copy(alpha = 0.4f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("NOVA CONQUEST", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Credits + income preview
                IndustrialPanel(modifier = Modifier.padding(vertical = 2.dp), backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            // Le compteur défile jusqu'à sa nouvelle valeur : un revenu de fin de
                            // tour ou le prix d'un vaisseau se lisaient jusqu'ici comme un simple
                            // saut de chiffre, impossible à relier à ce qui venait de se passer.
                            Text("${rolledCredits} C", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${if (incomePerTurn >= 0) "+" else ""}$incomePerTurn C/turn",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (incomePerTurn >= 0) NeonGreen else NeonRed
                            )
                        }
                    }
                }

                // Turn
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TURN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(gameState.turn.toString(), style = MaterialTheme.typography.labelLarge, color = NeonCyan)
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

        // Fiche du secteur sélectionné.
        //
        // Sur téléphone elle passe en bas plutôt qu'à droite : 220 dp fixes en CenterEnd
        // recouvrent le milieu droit du plateau — c'est-à-dire l'endroit que le joueur regarde,
        // puisqu'il vient d'y taper. En bas, elle mord sur une bande déjà occupée par le journal
        // de combat et la graine, et laisse le centre libre. Sur tablette la colonne latérale
        // reste préférable : la largeur ne manque pas, et la fiche ne masque rien.
        selectedHex?.let { coord ->
            val tile = gameState.map.getTileAt(coord)
            val unitOnTile = gameState.units[coord]
            if (tile != null && combatPreviewData == null) {
                Column(
                    modifier = Modifier
                        .align(if (isCompactWidth) Alignment.BottomCenter else Alignment.CenterEnd)
                        .then(
                            if (isCompactWidth) Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            else Modifier.padding(end = 32.dp)
                        )
                        .heightIn(max = if (isCompactWidth) 300.dp else 600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndustrialPanel(
                        modifier = (if (isCompactWidth) Modifier.fillMaxWidth() else Modifier.width(220.dp))
                            .padding(bottom = 16.dp)
                    ) {
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
                                tile.specialty?.let { specialty ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Specialty", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                        Text(specialty.displayName, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                                    }
                                }
                            }
                            if (unitOnTile != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                // Unit name + faction color
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(unitOnTile.type.name, style = MaterialTheme.typography.labelLarge, color = getFactionColor(unitOnTile.faction))
                                    if (unitOnTile.faction == gameState.activeFaction) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (unitOnTile.hasMoved) Text("MOVED", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            if (unitOnTile.hasAttacked) Text("FIRED", style = MaterialTheme.typography.labelSmall, color = NeonRed.copy(alpha = 0.8f))
                                        }
                                    } else {
                                        Text(unitOnTile.faction.displayName, style = MaterialTheme.typography.labelSmall, color = getFactionColor(unitOnTile.faction).copy(alpha = 0.8f))
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
                                    StatChip(
                                        "MOV",
                                        "${MovementCalculator.remainingMovement(gameState, unitOnTile)}/" +
                                            "${MovementCalculator.effectiveMovement(gameState, unitOnTile)}"
                                    )
                                }
                            }
                        }
                    }

                    // Carrier transport — LOAD adjacent units or DEPLOY from cargo
                    if (unitOnTile != null && unitOnTile.type == UnitType.CARRIER &&
                        unitOnTile.faction == gameState.activeFaction) {
                        // LOAD buttons: adjacent friendly light units not already in carrier
                        HexCoord.directions.map { coord + it }.forEach { neighbor ->
                            val candidate = gameState.units[neighbor]
                            if (candidate != null && candidate.faction == gameState.activeFaction &&
                                (candidate.type == UnitType.SCOUT || candidate.type == UnitType.FIGHTER) &&
                                unitOnTile.cargo.size < 2) {
                                IndustrialPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("LOAD ${candidate.type.name}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                        IconButton(onClick = { currentOnLoadUnit(coord, neighbor) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Star, contentDescription = "Load", tint = NeonCyan, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        // DEPLOY buttons: one per cargo slot
                        unitOnTile.cargo.forEachIndexed { idx, cargoType ->
                            IndustrialPanel(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("DEPLOY ${cargoType.name}", style = MaterialTheme.typography.labelSmall, color = NeonOrange)
                                    IconButton(
                                        onClick = {
                                            // Deploy to first empty passable adjacent hex
                                            val deployHex = HexCoord.directions.map { coord + it }
                                                .firstOrNull { h ->
                                                    gameState.units[h] == null &&
                                                    gameState.map.tiles[h]?.terrain?.isPassable == true
                                                }
                                            if (deployHex != null) currentOnDeployUnit(coord, deployHex, idx)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Deploy", tint = NeonOrange, modifier = Modifier.size(20.dp))
                                    }
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

                    // Siege / Capture — unit selected with adjacent enemy planet, OR planet selected with adjacent friendly unit
                    run {
                        val attackerUnit = when {
                            unitOnTile != null && unitOnTile.faction == gameState.activeFaction && !unitOnTile.hasAttacked -> unitOnTile
                            else -> null
                        }
                        val attackerCoord: HexCoord?
                        val targetPlanet: com.novaempire.core.domain.models.HexTile?

                        if (attackerUnit != null) {
                            // Unit is selected — look for adjacent enemy planet
                            attackerCoord = coord
                            targetPlanet = HexCoord.directions
                                .map { coord + it }
                                .mapNotNull { gameState.map.tiles[it] }
                                .firstOrNull { it.terrain == TerrainType.PLANET && it.owner != gameState.activeFaction }
                        } else if (tile.terrain == TerrainType.PLANET && tile.owner != gameState.activeFaction) {
                            // Enemy planet is selected — look for adjacent friendly unit that hasn't attacked
                            val adjacentFriendly = HexCoord.directions
                                .map { coord + it }
                                .firstOrNull { neighbor ->
                                    val u = gameState.units[neighbor]
                                    u != null && u.faction == gameState.activeFaction && !u.hasAttacked
                                }
                            attackerCoord = adjacentFriendly
                            targetPlanet = if (adjacentFriendly != null) tile else null
                        } else {
                            attackerCoord = null
                            targetPlanet = null
                        }

                        if (attackerCoord != null && targetPlanet != null) {
                            if (targetPlanet.systemLevel > 0) {
                                IndustrialPanel(modifier = Modifier.size(48.dp)) {
                                    IconButton(
                                        onClick = { siegePreviewData = Triple(attackerCoord, targetPlanet.coord, false) },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Siege Planet", tint = NeonOrange)
                                    }
                                }
                            } else {
                                IndustrialPanel(modifier = Modifier.size(48.dp)) {
                                    IconButton(
                                        onClick = { siegePreviewData = Triple(attackerCoord, targetPlanet.coord, true) },
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
                // Damage ranges come from the shared AttackCalculator — same bonuses/terrain the
                // engine applies — instead of re-deriving them here (which drifted from combat).
                val (minDmg, maxDmg) = AttackCalculator.damageRange(gameState, attackerCoord, defenderCoord)
                val (rawCounterMin, rawCounterMax) = AttackCalculator.damageRange(gameState, defenderCoord, attackerCoord)

                // The counter only happens if the defender survives AND can reach back.
                val defenderInRange = attackerCoord.distanceTo(defenderCoord) <= defender.type.range
                val counterMin = if (minDmg >= defender.currentHp || !defenderInRange) 0 else rawCounterMin
                val counterMax = if (maxDmg >= defender.currentHp || !defenderInRange) 0 else rawCounterMax

                val atkTerrain = gameState.map.tiles[attackerCoord]?.terrain
                val defTerrain = gameState.map.tiles[defenderCoord]?.terrain
                val notes = buildList {
                    if (atkTerrain == TerrainType.BLACK_HOLE) add("BLACK HOLE: -25% ATK")
                    if (defTerrain == TerrainType.NEBULA) add("NEBULA: -20% DMG")
                }

                CombatPreviewScreen(
                    attacker = attacker,
                    defender = defender,
                    minDamage = minDmg,
                    maxDamage = maxDmg,
                    counterMin = counterMin,
                    counterMax = counterMax,
                    terrainNotes = notes,
                    onConfirm = {
                        onAttackUnit(attackerCoord, defenderCoord)
                        combatPreviewData = null
                    },
                    onCancel = {
                        combatPreviewData = null
                    }
                )
            } else {
                combatPreviewData = null
            }
        }

        // Journal de combat (bas-gauche, 6 derniers événements).
        //
        // Il est dessiné après la fiche de secteur, donc au-dessus d'elle. Sur téléphone la fiche
        // occupe désormais ce même bas d'écran : le journal la recouvrirait. Il s'efface donc
        // pendant qu'une case est sélectionnée — c'est de l'information d'ambiance, la fiche est
        // ce que le joueur vient de demander.
        val sectorPanelOccupiesBottom = isCompactWidth && selectedHex != null && combatPreviewData == null
        if (combatLog.isNotEmpty() && !sectorPanelOccupiesBottom) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 8.dp)
                    .widthIn(max = 260.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                combatLog.take(6).forEach { (msg, colorStr) ->
                    val logColor = when (colorStr.uppercase()) {
                        "RED" -> NeonRed
                        "ORANGE" -> NeonOrange
                        "GOLD" -> NeonGold
                        "GREEN" -> NeonGreen
                        else -> NeonCyan
                    }
                    Text(
                        text = "▸ $msg",
                        style = MaterialTheme.typography.labelSmall,
                        color = logColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Terrain tooltip (long-press)
        terrainTooltipCoord?.let { coord ->
            val tile = gameState.map.tiles[coord]
            if (tile != null) {
                TerrainTooltipOverlay(
                    coord = coord,
                    tile = tile,
                    onDismiss = { terrainTooltipCoord = null }
                )
            } else {
                terrainTooltipCoord = null
            }
        }

        // Siege / Capture confirmation overlay
        siegePreviewData?.let { (attackerCoord, planetCoord, isCapture) ->
            val attacker = gameState.units[attackerCoord]
            val tile = gameState.map.tiles[planetCoord]
            if (attacker != null && tile != null) {
                val hasSiegeProtocols = playerState?.techUnlocked?.contains("tech_siege_protocols") == true
                val hasTerraforming = playerState?.techUnlocked?.contains("tech_terraforming") == true
                SiegePreviewOverlay(
                    attackerType = attacker.type,
                    attackerHp = attacker.currentHp,
                    planetLevel = tile.systemLevel,
                    isCapture = isCapture,
                    hasSiegeProtocols = hasSiegeProtocols,
                    hasTerraforming = hasTerraforming,
                    onConfirm = {
                        if (isCapture) onCapturePlanet(attackerCoord, planetCoord)
                        else onSiegePlanet(attackerCoord, planetCoord)
                        siegePreviewData = null
                    },
                    onCancel = { siegePreviewData = null }
                )
            } else {
                siegePreviewData = null
            }
        }

        // Seed readout (A5) — lets a galaxy be identified / replayed. Unobtrusive corner label.
        if (gameState.map.seed != 0L && !sectorPanelOccupiesBottom) {
            Text(
                text = "SEED ${gameState.map.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
fun SiegePreviewOverlay(
    attackerType: UnitType,
    attackerHp: Int,
    planetLevel: Int,
    isCapture: Boolean,
    hasSiegeProtocols: Boolean,
    hasTerraforming: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val siegeDamage = (if (attackerType == UnitType.BATTLESHIP || attackerType == UnitType.DREADNOUGHT) 2 else 1) +
        (if (hasSiegeProtocols) 1 else 0)
    val retaliation = planetLevel * 2
    val hpAfter = maxOf(0, attackerHp - retaliation)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        IndustrialPanel(modifier = Modifier.width(320.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isCapture) "CAPTURE PLANÉTAIRE" else "ASSAUT ORBITAL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isCapture) NeonGreen else NeonOrange
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isCapture) {
                    Text(
                        "Planète sans défense (niveau 0). Elle rejoint votre empire.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Niveau de départ", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text("${if (hasTerraforming) 2 else 1}", style = MaterialTheme.typography.bodyLarge, color = NeonCyan)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dégâts à la planète", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text("-$siegeDamage niveaux", style = MaterialTheme.typography.bodyLarge, color = NeonOrange)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Représailles orbitales", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text("-$retaliation PV", style = MaterialTheme.typography.bodyLarge, color = NeonRed)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PV de votre vaisseau", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text(
                            "$attackerHp → $hpAfter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hpAfter <= 0) NeonRed else NeonCyan
                        )
                    }
                    if (hpAfter <= 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("⚠ Vaisseau détruit par les défenses", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IndustrialButton(
                        text = "ANNULER",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        color = TextSecondary
                    )
                    IndustrialButton(
                        text = "CONFIRMER",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        isPrimary = true,
                        color = if (isCapture) NeonGreen else NeonOrange
                    )
                }
            }
        }
    }
}

private const val BASE_EXPLOSION_SHARDS = 10

/**
 * Éclats d'encre projetés par une explosion — le système que `particleCountMultiplier` pilote.
 *
 * Le réglage existait dans les JSON de thème et dans le guide (« 2.0 pour des explosions
 * massives ») mais n'avait aucun système à commander : l'explosion n'était qu'un dégradé radial.
 *
 * Les angles et longueurs sont dérivés de l'indice de l'éclat, pas d'un tirage aléatoire : un
 * `Random` par passe de dessin ferait scintiller les éclats à chaque frame de l'animation.
 */
fun DrawScope.drawExplosionShards(
    centerX: Float,
    centerY: Float,
    radius: Float,
    progress: Float,
    multiplier: Float
) {
    val count = (BASE_EXPLOSION_SHARDS * multiplier).roundToInt()
    if (count <= 0) return

    val fade = (1f - progress).coerceIn(0f, 1f)
    if (fade <= 0f) return

    for (i in 0 until count) {
        // Angle d'or : répartition régulière quel que soit le nombre d'éclats, sans motif visible.
        val angle = i * 2.399963f
        val dirX = cos(angle)
        val dirY = sin(angle)
        // Longueur variable mais stable d'une frame à l'autre.
        val reach = 0.75f + ((i * 37) % 50) / 100f
        val inner = radius * 0.45f
        val outer = radius * reach

        drawLine(
            color = BrunEncre.copy(alpha = fade * 0.75f),
            start = Offset(centerX + dirX * inner, centerY + dirY * inner),
            end = Offset(centerX + dirX * outer, centerY + dirY * outer),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

fun DrawScope.drawPlanet(
    x: Float,
    y: Float,
    hexRadius: Float,
    owner: Faction?,
    graphicsConfig: com.novaempire.core.domain.theme.GraphicsConfig,
    palette: MapPalette
) {
    val planetColor = owner?.let { getFactionColor(it) } ?: NeonGreen
    val inkBlack = palette.ink

    // Disque planète — gradient mat, pas de glow électrique
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to planetColor.copy(alpha = 0.55f),
                0.6f to planetColor.copy(alpha = 0.25f),
                1.0f to Color.Transparent
            ),
            center = Offset(x - hexRadius * 0.1f, y - hexRadius * 0.1f),
            radius = hexRadius * 0.58f
        ),
        radius = hexRadius * 0.58f,
        center = Offset(x, y)
    )

    // Ombrage par hachures (Cross-hatching style Graphic Noir)
    clipPath(
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(x - hexRadius * 0.52f, y - hexRadius * 0.52f, x + hexRadius * 0.52f, y + hexRadius * 0.52f)) }
    ) {
        for (i in 0 until 15) {
            val hY = y + hexRadius * 0.1f + i * 4f
            if (hY < y + hexRadius * 0.52f) {
                drawLine(
                    // `planetShadowAlpha` existait dans les JSON et dans le guide de thème, mais
                    // n'était lu nulle part : la valeur était figée à 0.6 ici même.
                    color = inkBlack.copy(alpha = graphicsConfig.planetShadowAlpha),
                    start = Offset(x - hexRadius * 0.5f, hY),
                    end = Offset(x + hexRadius * 0.5f, hY - hexRadius * 0.3f),
                    strokeWidth = 1f
                )
            }
        }
    }

    // Contour encre épaisse BD avec style plus texturé
    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.52f,
        center = Offset(x, y),
        style = Stroke(width = graphicsConfig.outlineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawCircle(
        color = planetColor.copy(alpha = 0.7f),
        radius = hexRadius * 0.52f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // Anneau orbital discret
    val ringPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(
            Offset(x - hexRadius * 0.75f, y - hexRadius * 0.18f),
            Size(hexRadius * 1.5f, hexRadius * 0.36f)
        ))
    }
    drawPath(path = ringPath, color = planetColor.copy(alpha = 0.28f), style = Stroke(width = 1f))

    // Glint planète
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(x - hexRadius * 0.25f, y - hexRadius * 0.38f),
        end   = Offset(x + hexRadius * 0.05f, y - hexRadius * 0.18f),
        strokeWidth = 1.5f
    )

    // Détails urbanisation (3 points)
    for (i in 0 until 3) {
        val angle = (i * 120f + 20f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.2f
        drawCircle(
            color = planetColor.copy(alpha = 0.8f),
            radius = 2.5f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }

    // Tirets possession — pointillés discrets si propriétaire
    if (owner != null) {
        drawCircle(
            color = planetColor.copy(alpha = 0.35f),
            radius = hexRadius * 0.42f,
            center = Offset(x, y),
            style = Stroke(width = 1.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        )
    }
}

fun DrawScope.drawUnit(x: Float, y: Float, unit: GameUnit, hexRadius: Float, palette: MapPalette) {
    val factionColor = getFactionColor(unit.faction)
    val inkBlack = palette.ink
    // Proportional to the hex (25/60 of the historical radius) so sprites scale with the board.
    val size = hexRadius * 0.42f

    // Helper: apply Bilal layers to a path — black fill → tinted fill → black outline → color outline
    fun applyBilalLayers(path: Path) {
        drawPath(path, color = inkBlack, style = Fill)
        drawPath(path, color = factionColor.copy(alpha = 0.28f), style = Fill)
        drawPath(path, color = inkBlack, style = Stroke(width = 4.5f))
        drawPath(path, color = factionColor, style = Stroke(width = 1.5f))
    }

    when (unit.type) {
        UnitType.CRUISER -> {
            val path = Path().apply {
                moveTo(x + size, y)
                lineTo(x - size * 0.5f, y - size * 0.7f)
                lineTo(x - size * 0.8f, y - size * 0.4f)
                lineTo(x - size * 0.8f, y + size * 0.4f)
                lineTo(x - size * 0.5f, y + size * 0.7f)
                close()
            }
            applyBilalLayers(path)
            drawLine(factionColor.copy(alpha = 0.7f), Offset(x + size * 0.2f, y - size * 0.5f), Offset(x + size * 0.2f, y - size * 1.1f), strokeWidth = 1.5f)
            drawCircle(factionColor, radius = 2f, center = Offset(x + size * 0.2f, y - size * 1.1f))
        }
        UnitType.BATTLESHIP -> {
            val path = Path().apply {
                moveTo(x + size * 1.1f, y)
                lineTo(x + size * 0.4f, y - size * 0.4f)
                lineTo(x - size * 0.6f, y - size * 0.5f)
                lineTo(x - size * 0.9f, y - size * 0.2f)
                lineTo(x - size * 0.9f, y + size * 0.2f)
                lineTo(x - size * 0.6f, y + size * 0.5f)
                lineTo(x + size * 0.4f, y + size * 0.4f)
                close()
            }
            applyBilalLayers(path)
            // Two turrets for battleship
            drawCircle(factionColor.copy(alpha = 0.8f), radius = 2f, center = Offset(x + size * 0.1f, y - size * 0.2f))
            drawLine(factionColor.copy(alpha = 0.6f), Offset(x + size * 0.1f, y - size * 0.2f), Offset(x + size * 0.5f, y - size * 0.6f), strokeWidth = 1.5f)

            drawCircle(factionColor.copy(alpha = 0.8f), radius = 2f, center = Offset(x - size * 0.3f, y - size * 0.2f))
            drawLine(factionColor.copy(alpha = 0.6f), Offset(x - size * 0.3f, y - size * 0.2f), Offset(x + size * 0.1f, y - size * 0.6f), strokeWidth = 1.5f)
        }
        UnitType.FIGHTER -> {
            val path = Path().apply {
                moveTo(x + size * 0.7f, y)
                lineTo(x - size * 0.7f, y - size * 0.6f)
                lineTo(x - size * 0.4f, y)
                lineTo(x - size * 0.7f, y + size * 0.6f)
                close()
            }
            applyBilalLayers(path)
            drawRect(factionColor.copy(alpha = 0.7f), Offset(x - size * 0.8f, y - size * 0.4f), Size(7f, 3f))
            drawRect(factionColor.copy(alpha = 0.7f), Offset(x - size * 0.8f, y + size * 0.3f), Size(7f, 3f))
        }
        UnitType.SCOUT -> {
            val path = Path().apply {
                moveTo(x + size * 0.8f, y)
                lineTo(x, y - size * 0.4f)
                lineTo(x - size * 0.8f, y)
                lineTo(x, y + size * 0.4f)
                close()
            }
            applyBilalLayers(path)
            drawArc(
                color = factionColor.copy(alpha = 0.6f),
                startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(x - size * 0.3f, y - size * 0.3f),
                size = Size(size * 0.6f, size * 0.6f),
                style = Stroke(width = 1.5f)
            )
        }
        UnitType.CARRIER -> {
            val path = Path().apply {
                moveTo(x + size, y - size * 0.4f)
                lineTo(x + size, y + size * 0.4f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size, y - size * 0.6f)
                close()
            }
            applyBilalLayers(path)
            drawLine(factionColor.copy(alpha = 0.5f), Offset(x - size * 0.5f, y), Offset(x + size * 0.5f, y), strokeWidth = 1f)
        }
        UnitType.DREADNOUGHT -> {
            val path = Path().apply {
                moveTo(x + size * 1.2f, y)
                lineTo(x - size * 0.2f, y - size * 0.8f)
                lineTo(x - size, y - size * 0.6f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size * 0.2f, y + size * 0.8f)
                close()
            }
            applyBilalLayers(path)
            for (i in 0 until 3) {
                val tx = x - size * 0.5f + (i * size * 0.4f)
                drawCircle(factionColor.copy(alpha = 0.8f), radius = 2.5f, center = Offset(tx, y))
                drawLine(factionColor.copy(alpha = 0.6f), Offset(tx, y), Offset(tx, y - size * 0.28f), strokeWidth = 1.5f)
            }
        }
        UnitType.DEFENSE_PLATFORM -> {
            val path = Path().apply {
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (kotlin.math.PI / 180f)
                    val r = size * 0.7f
                    val px = x + cos(angle).toFloat() * r
                    val py = y + sin(angle).toFloat() * r
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            applyBilalLayers(path)
            drawCircle(factionColor.copy(alpha = 0.35f), radius = size * 0.28f, center = Offset(x, y), style = Stroke(width = 1.5f))
            for (i in 0 until 4) {
                val angle = (i * 90f + 22.5f) * (kotlin.math.PI / 180f)
                val rx = x + cos(angle).toFloat() * size * 0.9f
                val ry = y + sin(angle).toFloat() * size * 0.9f
                drawRect(inkBlack, Offset(rx - 4f, ry - 4f), Size(8f, 8f))
                drawRect(factionColor, Offset(rx - 3f, ry - 3f), Size(6f, 6f))
            }
        }
    }

    // Glint blanc diagonal — signature reflet Bilal
    drawLine(
        color = Color.White.copy(alpha = 0.28f),
        start = Offset(x - size * 0.32f, y - size * 0.52f),
        end   = Offset(x + size * 0.08f, y - size * 0.22f),
        strokeWidth = 1.5f
    )

    // Barre HP — métal mat, pas gris numérique
    val hpPercent = unit.currentHp.toFloat() / unit.type.maxHp
    val barWidth = hexRadius * 0.67f
    val barHeight = 3.5f
    val barTop = y + size + 9f
    drawRect(palette.healthBarBackground, Offset(x - barWidth / 2, barTop), Size(barWidth, barHeight))
    drawRect(factionColor.copy(alpha = 0.85f), Offset(x - barWidth / 2, barTop), Size(barWidth * hpPercent, barHeight))
    drawRect(inkBlack, Offset(x - barWidth / 2, barTop), Size(barWidth, barHeight), style = Stroke(width = 1f))
}

fun DrawScope.drawAsteroids(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val offsets = listOf(
        Offset(-15f, -20f), Offset(10f, -25f), Offset(20f, 10f),
        Offset(-25f, 15f), Offset(0f, 25f), Offset(-5f, 0f)
    )
    val sizes = listOf(8f, 12f, 10f, 14f, 6f, 16f)
    val inkBlack = palette.ink

    offsets.forEachIndexed { index, offset ->
        val ax = x + offset.x
        val ay = y + offset.y
        val r = sizes[index]

        val path = Path()
        path.moveTo(ax, ay - r)
        path.lineTo(ax + r * 0.8f, ay - r * 0.3f)
        path.lineTo(ax + r, ay + r * 0.4f)
        path.lineTo(ax + r * 0.2f, ay + r)
        path.lineTo(ax - r * 0.6f, ay + r * 0.8f)
        path.lineTo(ax - r, ay)
        path.close()

        drawPath(path, color = palette.asteroidRock, style = Fill)           // roche sombre
        drawPath(path, color = inkBlack, style = Stroke(width = 2.5f))    // encre épaisse
        drawPath(path, color = NeonOrange.copy(alpha = 0.5f), style = Stroke(width = 1f))  // rouille
    }
}

fun DrawScope.drawNebula(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    // Nuage violet-brume Bilal — pas de violet électrique
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.nebulaHaze.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x - 10f, y - 10f),
            radius = hexRadius * 0.72f
        ),
        radius = hexRadius * 0.72f,
        center = Offset(x - 10f, y - 10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.2f), Color.Transparent),
            center = Offset(x + 14f, y + 8f),
            radius = hexRadius * 0.55f
        ),
        radius = hexRadius * 0.55f,
        center = Offset(x + 14f, y + 8f)
    )
}

/**
 * Unit-circle vertices of a pointy-top hexagon, resolved once at class-init.
 *
 * Every hexagon on the board is the same shape, yet [drawHexagonPath] was recomputing twelve
 * trigonometric functions per call — and it is called at least twice per tile per draw, which on
 * a GIGANTIC galaxy came to ~11 000 cos/sin per frame just to rebuild an identical outline.
 */
private val HEX_VERTEX_COS = FloatArray(6) { cos(PI / 180.0 * (60.0 * it - 30.0)).toFloat() }
private val HEX_VERTEX_SIN = FloatArray(6) { sin(PI / 180.0 * (60.0 * it - 30.0)).toFloat() }

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
        val px = centerX + radius * HEX_VERTEX_COS[i]
        val py = centerY + radius * HEX_VERTEX_SIN[i]
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

/** French label for a terrain, matching the wording of the long-press terrain sheet. */
private fun terrainLabel(terrain: TerrainType): String = when (terrain) {
    TerrainType.EMPTY -> "espace vide"
    TerrainType.PLANET -> "planète"
    TerrainType.ASTEROIDS -> "champ d'astéroïdes, infranchissable"
    TerrainType.NEBULA -> "nébuleuse"
    TerrainType.BLACK_HOLE -> "trou noir"
    TerrainType.WORMHOLE -> "ver de l'espace"
    TerrainType.PLASMA_CLOUD -> "nuage de plasma"
    TerrainType.ION_STORM -> "champ ionique"
    TerrainType.ANOMALY -> "anomalie galactique"
}

/**
 * What TalkBack announces for the hex under the keyboard cursor.
 *
 * The board is drawn into a [Canvas], which carries no semantics of its own: without this a
 * screen-reader user hears nothing at all from the map. Beyond naming the hex, it states **what
 * Enter would do from here** — the two-step "pick a fleet, then pick its target" flow is
 * otherwise impossible to follow by ear. The branches mirror `activateHex` deliberately; if one
 * changes, so must the other.
 */
private fun describeHexForAccessibility(
    state: GameState,
    coord: HexCoord?,
    selected: HexCoord?,
    reachable: Set<HexCoord>
): String {
    if (coord == null) {
        return "Carte tactique. Flèches pour déplacer le curseur, Entrée pour agir."
    }
    val tile = state.map.tiles[coord]
        ?: return "Secteur ${coord.q}, ${coord.r}, hors de la galaxie."
    val player = state.playerStates[state.activeFaction]
    if (coord !in (player?.exploredHexes ?: emptySet())) {
        return "Secteur ${coord.q}, ${coord.r}, inexploré."
    }

    val parts = mutableListOf("Secteur ${coord.q}, ${coord.r}", terrainLabel(tile.terrain))
    if (tile.terrain == TerrainType.PLANET) {
        parts += tile.owner?.let { "contrôlée par ${it.displayName}" } ?: "neutre"
        parts += "niveau ${tile.systemLevel}"
    }

    val unit = state.units[coord]
    val unitVisible = unit != null &&
        (unit.faction == state.activeFaction || coord in (player?.visibleHexes ?: emptySet()))
    if (unit != null && unitVisible) {
        parts += "${unit.type.name}, ${unit.faction.displayName}, " +
            "${unit.currentHp} points de vie sur ${unit.type.maxHp}"
        if (unit.faction == state.activeFaction) {
            val left = MovementCalculator.remainingMovement(state, unit)
            parts += when {
                unit.hasMoved && unit.hasAttacked -> "tour terminé"
                unit.hasMoved -> "plus de mouvement"
                unit.hasAttacked -> "a déjà tiré"
                left < MovementCalculator.effectiveMovement(state, unit) ->
                    "disponible, $left points de mouvement restants"
                else -> "disponible"
            }
        }
    }

    val selectedUnit = selected?.let { state.units[it] }
    parts += when {
        selected == coord -> "Entrée pour désélectionner"
        selected != null && selectedUnit != null && selectedUnit.faction == state.activeFaction -> when {
            unit != null && unitVisible && unit.faction != state.activeFaction &&
                !selectedUnit.hasAttacked && selected.distanceTo(coord) <= selectedUnit.type.range ->
                "Entrée pour attaquer"
            unit == null && !selectedUnit.hasAttacked && tile.terrain == TerrainType.PLANET &&
                tile.owner != null && tile.owner != state.activeFaction &&
                selected.distanceTo(coord) == 1 ->
                if (tile.systemLevel <= 0) "Entrée pour capturer la planète"
                else "Entrée pour assiéger la planète"
            unit == null && !selectedUnit.hasMoved && coord in reachable ->
                "Entrée pour déplacer la flotte ici"
            else -> "Entrée pour sélectionner"
        }
        else -> "Entrée pour sélectionner"
    }
    return parts.joinToString(". ")
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
    }
}

@Composable
fun TerrainTooltipOverlay(
    coord: HexCoord,
    tile: HexTile,
    onDismiss: () -> Unit
) {
    val description = when (tile.terrain) {
        TerrainType.EMPTY -> "Espace vide. Aucun effet spécial."
        TerrainType.PLANET -> "Planète habitée. Génère des crédits. Peut être capturée ou assiégée."
        TerrainType.ASTEROIDS -> "Champ d'astéroïdes. Impassable."
        TerrainType.NEBULA -> "Nébuleuse. Bloque la vision. Les flottes peuvent la traverser."
        TerrainType.BLACK_HOLE -> "Trou noir. Danger extrême — un vaisseau qui y stationne perd 3 PV en fin de tour et attaque à -25%."
        TerrainType.WORMHOLE -> "Ver de l'espace. Permet des déplacements longue distance."
        TerrainType.PLASMA_CLOUD -> "Nuage de plasma. Bloque la vision et ralentit les flottes (coût de déplacement x2)."
        TerrainType.ION_STORM -> "Champ ionique stationnaire. Bloque la vision et ralentit les flottes (coût de déplacement x2)."
        TerrainType.ANOMALY -> "Anomalie galactique. Chaque fin de tour, un vaisseau qui y stationne subit une impulsion imprévisible (soin ou dégâts)."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        IndustrialPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tile.terrain.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                    Text(
                        "SECTEUR ${coord.q},${coord.r}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (tile.terrain == TerrainType.PLANET) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val income = 5 + tile.systemLevel * 2
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Niveau ${tile.systemLevel}", style = MaterialTheme.typography.labelSmall, color = NeonOrange)
                        Text("+$income C/tour", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                        val owner = tile.owner
                        if (owner != null) {
                            Text(owner.name, style = MaterialTheme.typography.labelSmall, color = getFactionColor(owner))
                        } else {
                            Text("NEUTRE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Appuyez pour fermer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

fun DrawScope.drawBlackHole(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink
    val eventHorizonColor = palette.blackHole

    // Accretion disk (distortion effect via gradient)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonOrange.copy(alpha = 0.8f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.8f
        ),
        radius = hexRadius * 0.8f,
        center = Offset(x, y)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonRed.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.95f
        ),
        radius = hexRadius * 0.95f,
        center = Offset(x, y)
    )

    // Event Horizon (the black hole itself)
    drawCircle(
        color = eventHorizonColor,
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Fill
    )

    // Thick comic book ink outline
    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Stroke(width = 3.5f)
    )

    // Inner glow / edge of the void
    drawCircle(
        color = NeonOrange.copy(alpha = 0.9f),
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // White glint for stylistic consistency, distorted slightly
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(x - hexRadius * 0.2f, y - hexRadius * 0.2f),
        end   = Offset(x - hexRadius * 0.05f, y - hexRadius * 0.1f),
        strokeWidth = 1.5f
    )
}

fun DrawScope.drawWormhole(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink
    val wormholeColor = palette.wormhole

    // Spiral arms simulation with overlapping rotated ellipses
    for (i in 0 until 4) {
        val angle = (i * 45f)
        withTransform({
            rotate(angle, Offset(x, y))
        }) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(x, y),
                    radius = hexRadius * 0.7f
                ),
                topLeft = Offset(x - hexRadius * 0.7f, y - hexRadius * 0.2f),
                size = Size(hexRadius * 1.4f, hexRadius * 0.4f)
            )
            drawOval(
                color = NeonCyan.copy(alpha = 0.6f),
                topLeft = Offset(x - hexRadius * 0.7f, y - hexRadius * 0.2f),
                size = Size(hexRadius * 1.4f, hexRadius * 0.4f),
                style = Stroke(width = 1f)
            )
        }
    }

    // Central rift
    drawCircle(
        color = wormholeColor,
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Fill
    )

    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Stroke(width = 3f)
    )

    drawCircle(
        color = NeonCyan.copy(alpha = 0.9f),
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // Glint
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(x - hexRadius * 0.15f, y - hexRadius * 0.15f),
        end   = Offset(x + hexRadius * 0.05f, y - hexRadius * 0.05f),
        strokeWidth = 1.5f
    )
}

fun DrawScope.drawPlasmaCloud(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Rust/orange turbulent cloud
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonOrange.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(x - 5f, y + 10f),
            radius = hexRadius * 0.8f
        ),
        radius = hexRadius * 0.8f,
        center = Offset(x - 5f, y + 10f)
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonRed.copy(alpha = 0.4f), Color.Transparent),
            center = Offset(x + 12f, y - 12f),
            radius = hexRadius * 0.6f
        ),
        radius = hexRadius * 0.6f,
        center = Offset(x + 12f, y - 12f)
    )

    // Plasma arcs (jagged lines)
    val path = Path().apply {
        moveTo(x - hexRadius * 0.4f, y - hexRadius * 0.2f)
        lineTo(x - hexRadius * 0.1f, y - hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.1f, y - hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.4f, y - hexRadius * 0.3f)
    }
    drawPath(path, color = NeonOrange.copy(alpha = 0.8f), style = Stroke(width = 2f))

    val path2 = Path().apply {
        moveTo(x - hexRadius * 0.3f, y + hexRadius * 0.3f)
        lineTo(x, y + hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.2f, y + hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.5f, y + hexRadius * 0.2f)
    }
    drawPath(path2, color = NeonRed.copy(alpha = 0.7f), style = Stroke(width = 1.5f))

    // Dark matter specks in the plasma
    for (i in 0 until 5) {
        val angle = (i * 72f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.4f
        drawCircle(
            color = inkBlack,
            radius = 3f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }
}

fun DrawScope.drawIonStorm(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Heavy grey-blue cloud base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.ionStorm.copy(alpha = 0.7f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.85f
        ),
        radius = hexRadius * 0.85f,
        center = Offset(x, y)
    )

    // Energetic cyan flashes
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(x - 15f, y - 5f),
            radius = hexRadius * 0.5f
        ),
        radius = hexRadius * 0.5f,
        center = Offset(x - 15f, y - 5f)
    )

    // Lightning strikes
    val lightning = Path().apply {
        moveTo(x - hexRadius * 0.2f, y - hexRadius * 0.5f)
        lineTo(x - hexRadius * 0.05f, y - hexRadius * 0.1f)
        lineTo(x - hexRadius * 0.2f, y)
        lineTo(x + hexRadius * 0.1f, y + hexRadius * 0.4f)
        lineTo(x, y + hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.15f, y)
        close()
    }
    drawPath(lightning, color = NeonCyan.copy(alpha = 0.9f), style = Fill)
    drawPath(lightning, color = inkBlack, style = Stroke(width = 1.5f))

    val lightning2 = Path().apply {
        moveTo(x + hexRadius * 0.3f, y - hexRadius * 0.3f)
        lineTo(x + hexRadius * 0.1f, y)
        lineTo(x + hexRadius * 0.2f, y + hexRadius * 0.1f)
        lineTo(x, y + hexRadius * 0.3f)
    }
    drawPath(lightning2, color = NeonCyan.copy(alpha = 0.7f), style = Stroke(width = 2f))
}

fun DrawScope.drawAnomaly(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Strange green-brown base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.anomaly.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.75f
        ),
        radius = hexRadius * 0.75f,
        center = Offset(x, y)
    )

    // Unnatural geometry
    val path = Path().apply {
        moveTo(x, y - hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.35f, y - hexRadius * 0.15f)
        lineTo(x + hexRadius * 0.35f, y + hexRadius * 0.15f)
        lineTo(x, y + hexRadius * 0.4f)
        lineTo(x - hexRadius * 0.35f, y + hexRadius * 0.15f)
        lineTo(x - hexRadius * 0.35f, y - hexRadius * 0.15f)
        close()
    }

    // Distorted fill and thick ink stroke
    drawPath(path, color = NeonGreen.copy(alpha = 0.2f), style = Fill)
    drawPath(path, color = inkBlack, style = Stroke(width = 3f))
    drawPath(path, color = NeonGreen.copy(alpha = 0.8f), style = Stroke(width = 1.5f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))

    // Glitching inner lines
    drawLine(
        color = NeonGreen.copy(alpha = 0.9f),
        start = Offset(x - hexRadius * 0.2f, y),
        end = Offset(x + hexRadius * 0.2f, y),
        strokeWidth = 2f
    )
    drawLine(
        color = NeonGreen.copy(alpha = 0.9f),
        start = Offset(x, y - hexRadius * 0.2f),
        end = Offset(x, y + hexRadius * 0.2f),
        strokeWidth = 2f
    )

    // Artifact nodes
    for (i in 0 until 4) {
        val angle = (i * 90f + 45f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.25f
        drawCircle(
            color = NeonOrange.copy(alpha = 0.9f),
            radius = 2.5f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }
}
