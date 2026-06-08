package com.novaempire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.screens.*
import com.novaempire.app.ui.theme.*
import com.novaempire.app.ui.viewmodels.GameViewModel
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.GameIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EndTurnSummary(
    val turn: Int,
    val incomeGained: Int,
    val completedBuilds: List<String>,
    val techInProgress: String?,
    val idleFleets: Int
)

fun computeEndTurnSummary(state: GameState): EndTurnSummary {
    val ps = state.playerStates[state.activeFaction]
    var income = 10 + state.activeFaction.bonusCredits
    val ownedPlanets = state.map.tiles.values.filter {
        it.terrain == TerrainType.PLANET && it.owner == state.activeFaction
    }
    income += ownedPlanets.sumOf { 5 + it.systemLevel * 2 }
    if (ps?.recruitedHeroes?.contains(HeroRegistry.ELARA) == true) income += (income * 0.10).toInt() + 2
    if (state.activeEvent == GalacticEvent.ECONOMIC_BOOM) income += 3

    val completedBuilds = ps?.buildQueue
        ?.filter { it.turnsRemaining <= 1 }
        ?.map { it.unitType.name }
        ?: emptyList()

    val techInProgress = ps?.researchInProgress?.techId

    val idleFleets = state.units.values.count {
        it.faction == state.activeFaction && !it.hasMoved && !it.hasAttacked
    }

    return EndTurnSummary(
        turn = state.turn,
        incomeGained = income,
        completedBuilds = completedBuilds,
        techInProgress = techInProgress,
        idleFleets = idleFleets
    )
}

enum class AppScreen {
    MAIN_MENU,
    FACTION_SELECTION,
    GAME,
    VICTORY,
    HERO_ACADEMY,
    SETTINGS
}

enum class GameTab {
    MAP,
    SYSTEM,
    TECH,
    INTEL
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioManager.init(this)
        setContent {
            NovaEmpireTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val gameViewModel: GameViewModel = viewModel()
                    val gameState by gameViewModel.gameState.collectAsState()
                    var currentScreen by remember { mutableStateOf(AppScreen.MAIN_MENU) }
                    val snackbarHostState = remember { SnackbarHostState() }
                    val coroutineScope = rememberCoroutineScope()

                    LaunchedEffect(gameState.winner) {
                        if (gameState.winner != null) {
                            currentScreen = AppScreen.VICTORY
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        AppScreen.MAIN_MENU -> {
                            val hasSave = gameViewModel.hasSavedGame()
                            MainMenuScreen(
                                hasSavedGame = hasSave,
                                onNewGameClick = {
                                    currentScreen = AppScreen.FACTION_SELECTION
                                },
                                onResumeGameClick = {
                                    gameViewModel.loadGame { success, error ->
                                        if (success) currentScreen = AppScreen.GAME
                                        else if (error != null) coroutineScope.launch {
                                            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Long)
                                        }
                                    }
                                },
                                onSettingsClick = { currentScreen = AppScreen.SETTINGS }
                            )
                        }
                        AppScreen.FACTION_SELECTION -> {
                            FactionSelectionScreen(
                                onStartGameClick = { faction, mapSize, archetype ->
                                    gameViewModel.startNewGame(mapSize, archetype)
                                    gameViewModel.dispatch(GameIntent.SelectFaction(faction))
                                    currentScreen = AppScreen.GAME
                                },
                                onBackClick = { currentScreen = AppScreen.MAIN_MENU }
                            )
                        }
                        AppScreen.GAME -> {
                            GameContainer(
                                gameState = gameState,
                                onEndTurn = {
                                    gameViewModel.dispatch(GameIntent.EndTurn)
                                },
                                onMoveUnit = { from, to ->
                                    gameViewModel.dispatch(GameIntent.MoveUnit(from, to))
                                },
                                onAttackUnit = { from, to ->
                                    gameViewModel.dispatch(GameIntent.AttackUnit(from, to))
                                },
                                onSiegePlanet = { from, to ->
                                    gameViewModel.dispatch(GameIntent.SiegePlanet(from, to))
                                },
                                onCapturePlanet = { from, to ->
                                    gameViewModel.dispatch(GameIntent.CapturePlanet(from, to))
                                },
                                onResearchTech = { techId ->
                                    gameViewModel.dispatch(GameIntent.ResearchTech(techId))
                                },
                                onBuildUnit = { unitType, location ->
                                    gameViewModel.dispatch(GameIntent.BuildUnit(unitType, location))
                                },
                                onChangeRelation = { faction, relation ->
                                    gameViewModel.dispatch(GameIntent.ChangeRelation(faction, relation))
                                },
                                onOpenAcademy = {
                                    currentScreen = AppScreen.HERO_ACADEMY
                                }
                            )
                        }
                        AppScreen.HERO_ACADEMY -> {
                            HeroAcademyScreen(
                                gameState = gameState,
                                onRecruitClick = { heroId ->
                                    gameViewModel.dispatch(GameIntent.RecruitHero(heroId))
                                },
                                onBackClick = { currentScreen = AppScreen.GAME }
                            )
                        }
                        AppScreen.VICTORY -> {
                            VictoryScreen(
                                gameState = gameState,
                                isDefeat = gameState.winner != null && gameState.winner != gameState.humanFaction,
                                onMainMenuClick = { currentScreen = AppScreen.MAIN_MENU }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                onBackClick = { currentScreen = AppScreen.MAIN_MENU }
                            )
                        }
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    } // Box
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        AudioManager.release()
    }
}

@Composable
fun GameContainer(
    gameState: com.novaempire.core.domain.state.GameState,
    onEndTurn: () -> Unit,
    onMoveUnit: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onAttackUnit: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onSiegePlanet: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onCapturePlanet: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onResearchTech: (String) -> Unit,
    onBuildUnit: (com.novaempire.core.domain.models.UnitType, com.novaempire.core.hex.HexCoord) -> Unit,
    onChangeRelation: (com.novaempire.core.domain.models.Faction, com.novaempire.core.domain.models.DiplomaticRelation) -> Unit,
    onOpenAcademy: () -> Unit
) {
    var currentTab by remember { mutableStateOf(GameTab.MAP) }
    var selectedCoord by remember {
        mutableStateOf<com.novaempire.core.hex.HexCoord?>(null)
    }

    val gameViewModel: GameViewModel = viewModel()
    val isAiThinking by gameViewModel.isAiThinking.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        launch {
            gameViewModel.errors.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
        launch {
            gameViewModel.notifications.collect { (message, _) ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    }

    val activePlayer = gameState.playerStates[gameState.activeFaction]
    val visibleHexes = activePlayer?.visibleHexes ?: emptySet()
    val fallbackSystemCoord = activePlayer?.capitalCoord
        ?: gameState.map.tiles.values.firstOrNull { it.terrain == com.novaempire.core.domain.models.TerrainType.PLANET && it.owner == gameState.activeFaction }?.coord
        ?: gameState.map.tiles.keys.firstOrNull()

    val idleFleetCount = gameState.units.values.count {
        it.faction == gameState.activeFaction && !it.hasMoved && !it.hasAttacked
    }
    val idleFleets = gameState.units.values
        .filter { it.faction == gameState.activeFaction && !it.hasMoved && !it.hasAttacked }
        .sortedBy { it.id }
    var smartFocusIdx by remember { mutableStateOf(0) }
    var centerRequestCounter by remember { mutableStateOf(0) }
    var centerRequest by remember { mutableStateOf<Pair<com.novaempire.core.hex.HexCoord, Int>?>(null) }
    var endTurnSummary by remember { mutableStateOf<EndTurnSummary?>(null) }
    val combatLog = remember { mutableStateListOf<Pair<String, String>>() }
    LaunchedEffect(Unit) {
        gameViewModel.notifications.collect { entry ->
            combatLog.add(0, entry)
            if (combatLog.size > 8) combatLog.removeAt(combatLog.size - 1)
        }
    }
    val currentOnEndTurn by rememberUpdatedState(onEndTurn)
    LaunchedEffect(endTurnSummary) {
        if (endTurnSummary != null) {
            delay(2500)
            currentOnEndTurn()
            endTurnSummary = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentTab == GameTab.MAP) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SMART FOCUS — cycles to next idle fleet when tapped
                        Column(
                            modifier = if (idleFleets.isNotEmpty()) Modifier.clickable {
                                val idx = smartFocusIdx % idleFleets.size
                                val coord = idleFleets[idx].position
                                centerRequestCounter++
                                centerRequest = Pair(coord, centerRequestCounter)
                                smartFocusIdx = (smartFocusIdx + 1) % idleFleets.size
                            } else Modifier
                        ) {
                            Text("SMART FOCUS", style = MaterialTheme.typography.labelSmall, color = if (idleFleets.isNotEmpty()) NeonCyan else TextSecondary)
                            Text(
                                text = if (idleFleetCount == 1) "1 IDLE FLEET" else "$idleFleetCount IDLE FLEETS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idleFleetCount > 0) NeonOrange else TextSecondary
                            )
                        }
                        // Turn indicator
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TURN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(gameState.turn.toString(), style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        }
                        IndustrialButton(
                            text = when {
                                isAiThinking -> "AI THINKING..."
                                endTurnSummary != null -> "ENDING TURN..."
                                else -> "END TURN"
                            },
                            onClick = {
                                if (!isAiThinking && endTurnSummary == null) {
                                    endTurnSummary = computeEndTurnSummary(gameState)
                                }
                            },
                            isPrimary = true,
                            color = NeonOrange,
                            icon = { Icon(Icons.Default.Check, contentDescription = null) },
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    NavigationBarItem(
                        selected = currentTab == GameTab.MAP,
                        onClick = { currentTab = GameTab.MAP },
                        icon = { Text("MAP") }
                    )
                    NavigationBarItem(
                        selected = currentTab == GameTab.SYSTEM,
                        onClick = { currentTab = GameTab.SYSTEM },
                        icon = { Text("SYSTEM") }
                    )
                    NavigationBarItem(
                        selected = currentTab == GameTab.TECH,
                        onClick = { currentTab = GameTab.TECH },
                        icon = { Text("TECH") }
                    )
                    NavigationBarItem(
                        selected = currentTab == GameTab.INTEL,
                        onClick = { currentTab = GameTab.INTEL },
                        icon = { Text("INTEL") }
                    )
                }
            }
        }
    ) { paddingValues ->
        val tabs = GameTab.values()
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .then(
                    // Swipe left/right to change tabs on non-map screens; on the map
                    // screen the map's own pan gesture takes precedence.
                    if (currentTab != GameTab.MAP) Modifier.pointerInput(currentTab) {
                        var accumulated = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val idx = tabs.indexOf(currentTab)
                                when {
                                    accumulated < -80f && idx < tabs.size - 1 -> currentTab = tabs[idx + 1]
                                    accumulated > 80f && idx > 0 -> currentTab = tabs[idx - 1]
                                }
                                accumulated = 0f
                            },
                            onDragCancel = { accumulated = 0f }
                        ) { _, dragAmount -> accumulated += dragAmount }
                    } else Modifier
                )
        ) {
            when (currentTab) {
                GameTab.MAP -> {
                    TacticalMapScreen(
                        isAiThinking = isAiThinking,
                        gameState = gameState,
                        onEndTurnClick = onEndTurn,
                        onMoveUnit = onMoveUnit,
                        onAttackUnit = onAttackUnit,
                        onSiegePlanet = onSiegePlanet,
                        onCapturePlanet = onCapturePlanet,
                        visibleHexes = visibleHexes,
                        onHexClick = { selectedCoord = it },
                        onOpenSystemManagement = { coord ->
                            selectedCoord = coord
                            currentTab = GameTab.SYSTEM
                        },
                        onClearSelection = { selectedCoord = null },
                        onOpenAcademy = onOpenAcademy,
                        centerRequest = centerRequest,
                        initialSelectedHex = selectedCoord,
                        combatLog = combatLog
                    )
                }
                GameTab.SYSTEM -> {
                    val coordForSystem = selectedCoord ?: fallbackSystemCoord
                    if (coordForSystem != null) {
                        StarSystemManagementScreen(
                            coord = coordForSystem,
                            onClose = { currentTab = GameTab.MAP },
                            gameState = gameState,
                            onBuildUnit = onBuildUnit,
                            onUpgradeSystem = { coord ->
                                gameViewModel.dispatch(GameIntent.UpgradeSystem(coord))
                            },
                            onCancelBuild = { coord ->
                                gameViewModel.dispatch(GameIntent.CancelBuild(coord))
                            }
                        )
                    }
                }
                GameTab.TECH -> TechTreeScreen(
                    gameState = gameState,
                    onResearchTech = onResearchTech
                )
                GameTab.INTEL -> DiplomacyIntelScreen(
                    gameState = gameState,
                    onChangeRelation = onChangeRelation
                )
            }

            endTurnSummary?.let { summary ->
                EndTurnSummaryOverlay(summary = summary)
            }
        }
    }
}

@Composable
fun EndTurnSummaryOverlay(summary: EndTurnSummary) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        IndustrialPanel(modifier = Modifier.width(340.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "FIN DU TOUR ${summary.turn}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Revenus perçus", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("+${summary.incomeGained} C", style = MaterialTheme.typography.bodyLarge, color = NeonGreen)
                }

                if (summary.completedBuilds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Production terminée", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text(summary.completedBuilds.joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = NeonOrange)
                    }
                }

                if (summary.techInProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Recherche en cours", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text(summary.techInProgress, style = MaterialTheme.typography.bodyLarge, color = NeonCyan)
                    }
                }

                if (summary.idleFleets > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Flottes inactives", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text("${summary.idleFleets}", style = MaterialTheme.typography.bodyLarge, color = NeonOrange)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Passage au tour suivant...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
