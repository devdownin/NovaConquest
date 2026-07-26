package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonGold
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.VictoryChecker

/** How the game actually ended. A draw has no winner, and must not be dressed up as a victory. */
enum class GameOutcome { VICTORY, DEFEAT, DRAW }

@Composable
fun VictoryScreen(
    gameState: GameState,
    outcome: GameOutcome = GameOutcome.VICTORY,
    onMainMenuClick: () -> Unit
) {
    val isDefeat = outcome == GameOutcome.DEFEAT
    val accentColor = when (outcome) {
        GameOutcome.VICTORY -> NeonCyan
        GameOutcome.DEFEAT -> NeonRed
        GameOutcome.DRAW -> NeonGold
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (outcome) {
                    GameOutcome.VICTORY -> "VICTORY ACHIEVED"
                    GameOutcome.DEFEAT -> "DEFEAT"
                    GameOutcome.DRAW -> "MATCH NUL"
                },
                style = MaterialTheme.typography.displayLarge,
                color = accentColor
            )
            Text(
                text = gameState.victoryReason ?: if (isDefeat) "Eliminated" else "Domination",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (outcome) {
                        GameOutcome.DEFEAT -> VictoryStat("Winner", gameState.winner?.displayName ?: "Unknown")
                        GameOutcome.VICTORY -> VictoryStat("Faction", gameState.winner?.displayName ?: "Unknown")
                        GameOutcome.DRAW -> VictoryStat("Winner", "—")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    VictoryStat("Cycles Elapsed", gameState.turn.toString())
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "FINAL SCORE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary
                    )
                    // The same composite score that decides the turn-100 winner (credits +
                    // territory + fleet + research). Showing raw credits here contradicted the
                    // rule the game actually applies.
                    Text(
                        text = gameState.playerStates[gameState.humanFaction]
                            ?.let { VictoryChecker.empireScore(gameState, it).toString() } ?: "0",
                        style = MaterialTheme.typography.displayLarge,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IndustrialButton(text = "MAIN MENU", onClick = onMainMenuClick, color = TextSecondary)
            }
        }
    }
}

@Composable
fun VictoryStat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}
