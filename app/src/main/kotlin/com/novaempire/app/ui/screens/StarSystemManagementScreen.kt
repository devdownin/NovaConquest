package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

@Composable
fun StarSystemManagementScreen(
    gameState: GameState,
    onBuildUnit: (UnitType, HexCoord) -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0

    val ownedPlanets = gameState.map.tiles.values.filter { it.owner == gameState.activeFaction }
    var selectedPlanet by remember { mutableStateOf(ownedPlanets.firstOrNull()) }

    // If active faction changes, ensure we select one of their planets
    LaunchedEffect(gameState.activeFaction) {
        if (selectedPlanet?.owner != gameState.activeFaction) {
            selectedPlanet = gameState.map.tiles.values.firstOrNull { it.owner == gameState.activeFaction }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (ownedPlanets.isEmpty() || selectedPlanet == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO PLANETS OWNED", style = MaterialTheme.typography.displayLarge, color = NeonRed)
            }
            return
        }

        // Top Section: Faction Treasury
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\${gameState.activeFaction.name} EMPIRE",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonCyan
            )
            Text(
                text = "TREASURY: \$credits C",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan
            )
        }

        // Planet Selector
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ownedPlanets) { planet ->
                val isSelected = planet.coord == selectedPlanet?.coord
                Surface(
                    modifier = Modifier
                        .height(60.dp)
                        .clickable { selectedPlanet = planet },
                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) NeonCyan else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val title = if (planet.coord == playerState?.capitalCoord) "CAPITAL" else "SYSTEM [\${planet.coord.q},\${planet.coord.r}]"
                        Text(text = title, style = MaterialTheme.typography.labelLarge, color = if (isSelected) NeonCyan else TextSecondary)
                        Text(text = "LEVEL \${planet.systemLevel}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

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
                val upgradeCost = selectedPlanet!!.systemLevel * 5
                val canUpgrade = credits >= upgradeCost && selectedPlanet!!.systemLevel < 5

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SYSTEM LEVEL: \${selectedPlanet!!.systemLevel}/5", style = MaterialTheme.typography.bodyLarge)
                    Text("INCOME: +\${selectedPlanet!!.systemLevel * 2} C/TURN", style = MaterialTheme.typography.bodyLarge, color = NeonCyan)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedPlanet!!.systemLevel < 5) {
                    IndustrialButton(
                        text = "UPGRADE SYSTEM (COST: \$upgradeCost C)",
                        onClick = { /* TODO dispatch UpgradeSystem intent */ },
                        color = if (canUpgrade) NeonOrange else NeonRed
                    )
                } else {
                    IndustrialButton(
                        text = "SYSTEM FULLY UPGRADED",
                        onClick = { },
                        color = NeonCyan
                    )
                }
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
                    onBuild = { onBuildUnit(unitType, selectedPlanet!!.coord) }
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
