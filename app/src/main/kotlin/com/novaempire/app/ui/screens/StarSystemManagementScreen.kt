package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState

@Composable
fun StarSystemManagementScreen(
    gameState: GameState,
    onBuildUnit: (UnitType) -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "\${gameState.activeFaction.name} CAPITAL",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonCyan
                )
                Text(
                    text = "PRODUCTION HUB | CREDITS: \$credits C",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upgrade Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Placeholder for progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(1f)
                            .fillMaxHeight()
                            .background(NeonCyan)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialButton(
                    text = "SYSTEM FULLY UPGRADED",
                    onClick = { },
                    color = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PRODUCTION QUEUE",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Production Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(UnitType.values()) { unitType ->
                UnitProductionCard(
                    unitType = unitType,
                    canAfford = credits >= unitType.cost,
                    onBuild = { onBuildUnit(unitType) }
                )
            }
        }
    }
}

@Composable
fun UnitProductionCard(unitType: UnitType, canAfford: Boolean, onBuild: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (canAfford) NeonCyan else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = unitType.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (canAfford) NeonCyan else TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "HP: \${unitType.maxHp}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "ATK: \${unitType.attack}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            IndustrialButton(
                text = "BUILD (\${unitType.cost} C)",
                onClick = onBuild,
                color = if (canAfford) NeonCyan else NeonRed
            )
        }
    }
}
