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
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

@Composable
fun StarSystemManagementScreen(
    coord: HexCoord,
    gameState: GameState,
    onBuildUnit: (UnitType, HexCoord) -> Unit,
    onUpgradeSystem: (HexCoord) -> Unit,
    onCancelBuild: (HexCoord) -> Unit = {},
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
    val upgradeDisabledReason: String? = when {
        !isOwnPlanet -> "Planète non contrôlée"
        systemLevel >= 5 -> null
        credits < upgradeCost -> "Crédits insuffisants (besoin : $upgradeCost C, disponible : $credits C)"
        else -> null
    }

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
                            disabledReason = upgradeDisabledReason,
                            onUpgrade = { onUpgradeSystem(coord) }
                        )
                        ShipyardPanel(coord, credits, playerState?.buildQueue ?: emptyList(), onBuildUnit, onCancelBuild)
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
                                disabledReason = upgradeDisabledReason,
                                onUpgrade = { onUpgradeSystem(coord) }
                            )
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            ShipyardPanel(coord, credits, playerState?.buildQueue ?: emptyList(), onBuildUnit, onCancelBuild)
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
    disabledReason: String? = null,
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
                    enabled = canUpgrade,
                    color = if (canUpgrade) NeonCyan else TextSecondary,
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }
                )
                if (!canUpgrade && disabledReason != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = disabledReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonRed.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShipyardPanel(coord: HexCoord, credits: Int, buildQueue: List<BuildOrder>, onBuildUnit: (UnitType, HexCoord) -> Unit, onCancelBuild: (HexCoord) -> Unit = {}) {
    val activeOrder = buildQueue.firstOrNull { it.planetCoord == coord }
    IndustrialPanel(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SHIPYARD PRODUCTION", style = MaterialTheme.typography.labelLarge)
            }
            HeaderLine(modifier = Modifier.padding(bottom = 16.dp))

            if (activeOrder != null) {
                // Show active production order
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("IN PRODUCTION", style = MaterialTheme.typography.labelLarge, color = NeonOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(activeOrder.unitType.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "${activeOrder.turnsRemaining} TURN${if (activeOrder.turnsRemaining > 1) "S" else ""} LEFT",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonOrange
                            )
                        }
                        // P5: an order that finished but had nowhere to place the ship retries every
                        // turn. Say so, instead of showing a countdown that never reaches zero.
                        if (activeOrder.blocked) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚠ PRODUCTION BLOQUÉE — aucune case libre autour du système. " +
                                    "Dégagez les environs ou annulez l'ordre.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonRed
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                IndustrialButton(
                    text = "CANCEL (${activeOrder.unitType.cost / 2} C REFUND)",
                    onClick = { onCancelBuild(coord) },
                    color = com.novaempire.app.ui.theme.NeonRed,
                    isPrimary = false
                )
            } else {
                Text("AVAILABLE BLUEPRINTS", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))

                // Driven by UnitType so every buildable ship is actually offered. Only Scout,
                // Fighter and Cruiser used to be listed — the player could never field a Carrier
                // (making the map's LOAD/DEPLOY controls unreachable), a Battleship or Dreadnought
                // (their siege bonus was AI-only), nor a Defense Platform.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UnitType.values().sortedBy { it.cost }.forEach { type ->
                        BlueprintCard(
                            type = type,
                            canAfford = credits >= type.cost,
                            onClick = { onBuildUnit(type, coord) },
                            modifier = Modifier.widthIn(min = 160.dp).weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlueprintCard(
    type: UnitType,
    canAfford: Boolean = true,
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
                Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary)
                // Real stats replace the old decorative "LVL n" tag, which mapped to nothing.
                Text("${type.upkeepCost} C/tour", style = MaterialTheme.typography.labelLarge, color = NeonOrange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(type.name.replace('_', ' '), style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    "ATQ ${type.attack} · PV ${type.maxHp} · PORTÉE ${type.range} · MOUV ${type.movement}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(NeonCyan, shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${type.cost} Credits", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }

            IndustrialButton(
                text = "PRODUCE",
                onClick = onClick,
                color = if (canAfford) NeonCyan else TextSecondary,
                isPrimary = canAfford,
                enabled = canAfford,
                icon = { Icon(Icons.Default.Menu, contentDescription = null) }
            )
            if (!canAfford) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crédits insuffisants (${type.cost} C)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}
