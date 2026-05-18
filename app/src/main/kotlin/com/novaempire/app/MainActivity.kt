package com.novaempire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaempire.app.ui.screens.*
import com.novaempire.app.ui.theme.NovaEmpireTheme
import com.novaempire.app.ui.viewmodels.GameViewModel
import com.novaempire.core.engine.GameIntent

enum class AppScreen {
    MAIN_MENU,
    FACTION_SELECTION,
    GAME,
    VICTORY,
    HERO_ACADEMY // Added screen navigation
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
                                    gameViewModel.startNewGame()
                                    currentScreen = AppScreen.FACTION_SELECTION
                                },
                                onResumeGameClick = {
                                    if (gameViewModel.loadGame()) {
                                        currentScreen = AppScreen.GAME
                                    }
                                },
                                onSettingsClick = {}
                            )
                        }
                        AppScreen.FACTION_SELECTION -> {
                            FactionSelectionScreen(
                                onStartGameClick = { faction ->
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
                                onResearchTech = { techId ->
                                    gameViewModel.dispatch(GameIntent.ResearchTech(techId))
                                },
                                onBuildUnit = { unitType ->
                                    gameViewModel.dispatch(GameIntent.BuildUnit(unitType))
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
                    }
                }
            }
        }
    }
}

@Composable
fun GameContainer(
    gameState: com.novaempire.core.domain.state.GameState,
    onEndTurn: () -> Unit,
    onMoveUnit: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onAttackUnit: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit,
    onResearchTech: (String) -> Unit,
    onBuildUnit: (com.novaempire.core.domain.models.UnitType) -> Unit,
    onChangeRelation: (com.novaempire.core.domain.models.Faction, com.novaempire.core.domain.models.DiplomaticRelation) -> Unit,
    onOpenAcademy: () -> Unit
) {
    var currentTab by remember { mutableStateOf(GameTab.MAP) }

    Scaffold(
        bottomBar = {
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
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (currentTab) {
                GameTab.MAP -> TacticalMapScreen(
                    gameState = gameState,
                    onEndTurnClick = onEndTurn,
                    onMoveUnit = onMoveUnit,
                    onAttackUnit = onAttackUnit,
                    onOpenAcademy = onOpenAcademy
                )
                GameTab.SYSTEM -> StarSystemManagementScreen(
                    gameState = gameState,
                    onBuildUnit = onBuildUnit
                )
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
