package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
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
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

@Composable
fun StarSystemManagementScreen(
    coord: HexCoord,
    gameState: GameState,
    onBuildUnit: (UnitType, HexCoord) -> Unit,
    onClose: () -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0
    val systemLevel = 2 // Mock value

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
                .padding(32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NeonCyan)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("SECTOR ${coord.q},${coord.r}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("SYSTEM MANAGEMENT INTERFACE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    }
                }
                IndustrialPanel(modifier = Modifier.height(48.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TREASURY", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${credits} C", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Left Column: Infrastructure & Defense
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IndustrialPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SYSTEM INFRASTRUCTURE", style = MaterialTheme.typography.labelLarge)
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Development Level", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                                Text("LEVEL $systemLevel", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                            }

                            // Progress bar
                            Row(modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 2.dp).background(MaterialTheme.colorScheme.surface)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.6f).background(NeonCyan))
                            }
                            Text("Upgrade Progress: 60%", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                                Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
                                    Column {
                                        Text("Credit Prod.", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                        Text("+340 / turn", style = MaterialTheme.typography.bodyLarge, color = NeonCyan)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
                                    Column {
                                        Text("Ore Mining", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                        Text("+120 / turn", style = MaterialTheme.typography.bodyLarge, color = NeonOrange)
                                    }
                                }
                            }

                            IndustrialButton(
                                text = "UPGRADE SYSTEM",
                                onClick = {},
                                icon = { Icon(Icons.Default.Menu, contentDescription = null) }
                            )
                        }
                    }

                    IndustrialPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = NeonRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PLANETARY DEFENSE", style = MaterialTheme.typography.labelLarge)
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Shield Integrity", style = MaterialTheme.typography.bodyLarge)
                                Text("85%", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                            }
                            Row(modifier = Modifier.fillMaxWidth().height(6.dp).padding(bottom = 8.dp).background(MaterialTheme.colorScheme.surface)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).background(NeonCyan))
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Hull Armor", style = MaterialTheme.typography.bodyLarge)
                                Text("40%", style = MaterialTheme.typography.labelLarge, color = NeonRed)
                            }
                            Row(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surface)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.4f).background(NeonRed))
                            }
                        }
                    }
                }

                // Right Column: Shipyard
                IndustrialPanel(modifier = Modifier.weight(1.5f).fillMaxHeight()) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SHIPYARD PRODUCTION", style = MaterialTheme.typography.labelLarge)
                        }
                        HeaderLine(modifier = Modifier.padding(bottom = 16.dp))

                        Text("AVAILABLE BLUEPRINTS", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                            BlueprintCard(
                                name = "Scout",
                                cost = UnitType.SCOUT.cost,
                                level = "LVL 1",
                                icon = Icons.Default.Menu,
                                onClick = { onBuildUnit(UnitType.SCOUT, coord) },
                                modifier = Modifier.weight(1f)
                            )
                            BlueprintCard(
                                name = "Fighter",
                                cost = UnitType.FIGHTER.cost,
                                level = "LVL 2",
                                icon = Icons.Default.Menu,
                                onClick = { onBuildUnit(UnitType.FIGHTER, coord) },
                                modifier = Modifier.weight(1f)
                            )
                            BlueprintCard(
                                name = "Cruiser",
                                cost = UnitType.CRUISER.cost,
                                level = "LVL 4",
                                icon = Icons.Default.Menu,
                                onClick = { onBuildUnit(UnitType.CRUISER, coord) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, contentDescription = null, tint = TextSecondary)
                Text(level, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(name.uppercase(), style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.weight(1f))

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
