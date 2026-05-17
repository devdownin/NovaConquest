package com.novaempire.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.novaempire.app.ui.screens.FactionSelectionScreen
import com.novaempire.app.ui.screens.MainMenuScreen
import com.novaempire.app.ui.screens.TacticalMapScreen
import com.novaempire.app.ui.theme.NovaEmpireTheme
import com.novaempire.core.engine.GameEngine
import com.novaempire.core.engine.GameIntent

enum class Screen {
    MAIN_MENU,
    FACTION_SELECTION,
    TACTICAL_MAP
}

class MainActivity : ComponentActivity() {
    private val gameEngine = GameEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaEmpireTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.MAIN_MENU) }
                    var gameState by remember { mutableStateOf(gameEngine.state) }

                    when (currentScreen) {
                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                onNewGameClick = { currentScreen = Screen.FACTION_SELECTION },
                                onSettingsClick = {}
                            )
                        }
                        Screen.FACTION_SELECTION -> {
                            FactionSelectionScreen(
                                onStartGameClick = { faction ->
                                    // Initialize game with faction
                                    currentScreen = Screen.TACTICAL_MAP
                                },
                                onBackClick = { currentScreen = Screen.MAIN_MENU }
                            )
                        }
                        Screen.TACTICAL_MAP -> {
                            TacticalMapScreen(
                                gameState = gameState,
                                onEndTurnClick = {
                                    gameEngine.processIntent(GameIntent.EndTurn)
                                    gameState = gameEngine.state
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
