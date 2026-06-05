package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.HalftoneBackground
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.NoiseOverlay
import com.novaempire.app.ui.components.HeaderLine
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

@Composable
fun StarSystemManagementScreen(
    coord: HexCoord,
    gameState: GameState,
    onBuildUnit: (UnitType, HexCoord) -> Unit,
    onUpgradeSystem: (HexCoord) -> Unit,
    onClose: () -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0
    val tile = gameState.map.tiles[coord]
    val systemLevel = tile?.systemLevel ?: 0
    val isOwnPlanet = tile?.owner == gameState.activeFaction
    val planetIncome = 5 + systemLevel * 2
    val upgradeCost = (systemLevel + 1) * 15
    val canUpgrade = isOwnPlanet && systemLevel < 5 && credits >= upgradeCost

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HalftoneBackground(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.05f))
        NoiseOverlay(modifier = Modifier.fillMaxSize(), alpha = 0.05f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NeonCyan)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SECTOR ${coord.q},${coord.r}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("SYSTEM MANAGEMENT", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TREASURY", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${credits} C", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp

                if (isCompact) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfrastructurePanel(
                            systemLevel = systemLevel,
                            planetIncome = planetIncome,
                            upgradeCost = upgradeCost,
                            canUpgrade = canUpgrade,
                            onUpgrade = { onUpgradeSystem(coord) }
                        )
                        ShipyardPanel(coord, onBuildUnit)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            InfrastructurePanel(
                                systemLevel = systemLevel,
                                planetIncome = planetIncome,
                                upgradeCost = upgradeCost,
                                canUpgrade = canUpgrade,
                                onUpgrade = { onUpgradeSystem(coord) }
                            )
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            ShipyardPanel(coord, onBuildUnit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfrastructurePanel(
    systemLevel: Int,
    planetIncome: Int,
    upgradeCost: Int,
    canUpgrade: Boolean,
    onUpgrade: () -> Unit
) {
    IndustrialPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(Icons.Default.Build, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SYSTEM INFRASTRUCTURE", style = MaterialTheme.typography.labelLarge)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Development Level", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                Text("LEVEL $systemLevel / 5", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            }

            // Level bar
            Row(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surface)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(systemLevel / 5f).background(NeonCyan))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
                    Column {
                        Text("Credit Income", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Text("+$planetIncome / turn", style = MaterialTheme.typography.bodyLarge, color = NeonCyan)
                    }
                }
                Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
                    Column {
                        Text("Upgrade Cost", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Text(
                            text = if (systemLevel >= 5) "MAX LEVEL" else "$upgradeCost C",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeonOrange
                        )
                    }
                }
            }

            if (systemLevel < 5) {
                IndustrialButton(
                    text = if (canUpgrade) "UPGRADE SYSTEM" else "UPGRADE ($upgradeCost C)",
                    onClick = onUpgrade,
                    isPrimary = canUpgrade,
                    color = if (canUpgrade) NeonCyan else TextSecondary,
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShipyardPanel(coord: HexCoord, onBuildUnit: (UnitType, HexCoord) -> Unit) {
    IndustrialPanel(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SHIPYARD PRODUCTION", style = MaterialTheme.typography.labelLarge)
            }
            HeaderLine(modifier = Modifier.padding(bottom = 16.dp))

            Text("AVAILABLE BLUEPRINTS", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BlueprintCard(
                    name = "Scout",
                    cost = UnitType.SCOUT.cost,
                    level = "LVL 1",
                    icon = Icons.Default.Menu,
                    onClick = { onBuildUnit(UnitType.SCOUT, coord) },
                    modifier = Modifier.widthIn(min = 160.dp).weight(1f, fill = false)
                )
                BlueprintCard(
                    name = "Fighter",
                    cost = UnitType.FIGHTER.cost,
                    level = "LVL 2",
                    icon = Icons.Default.Menu,
                    onClick = { onBuildUnit(UnitType.FIGHTER, coord) },
                    modifier = Modifier.widthIn(min = 160.dp).weight(1f, fill = false)
                )
                BlueprintCard(
                    name = "Cruiser",
                    cost = UnitType.CRUISER.cost,
                    level = "LVL 4",
                    icon = Icons.Default.Menu,
                    onClick = { onBuildUnit(UnitType.CRUISER, coord) },
                    modifier = Modifier.widthIn(min = 160.dp).weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
fun BlueprintCard(
    name: String,
    cost: Int,
    level: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, contentDescription = null, tint = TextSecondary)
                Text(level, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(name.uppercase(), style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(NeonCyan, shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$cost Credits", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }

            IndustrialButton(
                text = "PRODUCE",
                onClick = onClick,
                color = NeonCyan,
                isPrimary = true,
                icon = { Icon(Icons.Default.Menu, contentDescription = null) }
            )
        }
    }
}
