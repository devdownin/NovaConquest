package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState

@Composable
fun DiplomacyIntelScreen(
    gameState: GameState,
    onChangeRelation: (Faction, DiplomaticRelation) -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val relations = playerState?.relations ?: emptyMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DIPLOMACY & INTEL",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "TURN \${gameState.turn}",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "KNOWN FACTIONS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val otherFactions = Faction.values().filter { it != gameState.activeFaction }

        otherFactions.forEach { faction ->
            val relation = relations[faction] ?: DiplomaticRelation.NEUTRAL
            val accentColor = when (relation) {
                DiplomaticRelation.ALLIANCE -> NeonCyan
                DiplomaticRelation.WAR -> NeonRed
                DiplomaticRelation.NEUTRAL -> NeonGold
            }

            FactionIntelCard(
                name = faction.name,
                status = relation.name,
                accentColor = accentColor,
                intel = "FLEET STRENGTH: UNKNOWN\\nCAPITAL: UNKNOWN",
                onProposeAlliance = { onChangeRelation(faction, DiplomaticRelation.ALLIANCE) },
                onDeclareWar = { onChangeRelation(faction, DiplomaticRelation.WAR) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "GALACTIC SCOREBOARD",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val sortedPlayers = gameState.playerStates.values.sortedByDescending { it.credits }
                sortedPlayers.forEachIndexed { index, player ->
                    val color = if (player.faction == gameState.activeFaction) NeonCyan else TextSecondary
                    ScoreRow(rank = index + 1, name = player.faction.name, score = player.credits, color = color)
                }
            }
        }
    }
}

@Composable
fun FactionIntelCard(
    name: String,
    status: String,
    accentColor: androidx.compose.ui.graphics.Color,
    intel: String,
    onProposeAlliance: () -> Unit,
    onDeclareWar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor
                )
                Text(
                    text = "STATUS: \${status.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = intel,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (status != "ALLIANCE") {
                    IndustrialButton(text = "PROPOSE ALLIANCE", onClick = onProposeAlliance, modifier = Modifier.weight(1f))
                }
                if (status != "WAR") {
                    IndustrialButton(text = "DECLARE WAR", onClick = onDeclareWar, color = NeonRed, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ScoreRow(rank: Int, name: String, score: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "\$rank. \$name", style = MaterialTheme.typography.bodyLarge, color = color)
        Text(text = score.toString(), style = MaterialTheme.typography.bodyLarge)
    }
}
