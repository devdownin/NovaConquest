package com.novaempire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.screens.*
import com.novaempire.app.ui.theme.*
import com.novaempire.app.ui.viewmodels.GameViewModel
import com.novaempire.core.engine.GameIntent
import kotlinx.coroutines.launch

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

                    LaunchedEffect(gameState.winner) {
                        if (gameState.winner != null) {
                            currentScreen = AppScreen.VICTORY
                        }
                    }

                    when (currentScreen) {
                        AppScreen.MAIN_MENU -> {
                            val hasSave = gameViewModel.hasSavedGame()
                            MainMenuScreen(
                                hasSavedGame = hasSave,
                                onNewGameClick = {
                                    currentScreen = AppScreen.FACTION_SELECTION
                                },
                                onResumeGameClick = {
                                    gameViewModel.loadGame { success ->
                                        if (success) currentScreen = AppScreen.GAME
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
                                onMainMenuClick = { currentScreen = AppScreen.MAIN_MENU }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                onBackClick = { currentScreen = AppScreen.MAIN_MENU }
                            )
                        }
                    }
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
                        Column {
                            Text("SMART FOCUS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = if (idleFleetCount == 1) "1 IDLE FLEET" else "$idleFleetCount IDLE FLEETS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idleFleetCount > 0) NeonOrange else TextSecondary
                            )
                        }
                        IndustrialButton(
                            text = if (isAiThinking) "AI THINKING..." else "END TURN",
                            onClick = {
                                if (!isAiThinking) {
                                    AudioManager.playSound(SoundType.END_TURN)
                                    onEndTurn()
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
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
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
                        onOpenAcademy = onOpenAcademy
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
        }
    }
}
