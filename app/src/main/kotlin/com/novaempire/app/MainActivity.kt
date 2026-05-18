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
    GAME
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

                    when (currentScreen) {
                        AppScreen.MAIN_MENU -> {
                            MainMenuScreen(
                                onNewGameClick = { currentScreen = AppScreen.FACTION_SELECTION },
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
                                }
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
    onMoveUnit: (com.novaempire.core.hex.HexCoord, com.novaempire.core.hex.HexCoord) -> Unit
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
                GameTab.MAP -> TacticalMapScreen(gameState = gameState, onEndTurnClick = onEndTurn, onMoveUnit = onMoveUnit)
                GameTab.SYSTEM -> StarSystemManagementScreen()
                GameTab.TECH -> TechTreeScreen()
                GameTab.INTEL -> DiplomacyIntelScreen()
            }
        }
    }
}
