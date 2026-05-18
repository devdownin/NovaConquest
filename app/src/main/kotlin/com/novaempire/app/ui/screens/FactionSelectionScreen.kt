package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.HalftoneBackground
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.NoiseOverlay
import com.novaempire.app.ui.components.HeaderLine
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapSize
import com.novaempire.core.domain.models.MapArchetype

@Composable
fun FactionSelectionScreen(
    onStartGameClick: (Faction, MapSize, MapArchetype) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedFaction by remember { mutableStateOf(Faction.DOMINION) }
    var selectedMapSize by remember { mutableStateOf(MapSize.MEDIUM) }
    var selectedArchetype by remember { mutableStateOf(MapArchetype.STANDARD) }
    var commanderCallsign by remember { mutableStateOf("CMD_VALERIUS") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HalftoneBackground(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.05f))
        NoiseOverlay(modifier = Modifier.fillMaxSize(), alpha = 0.05f)

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Back", tint = NeonCyan)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "NOVA CONQUEST",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
            }
            Icon(Icons.Default.Menu, contentDescription = null, tint = NeonCyan) // Using Menu as placeholder
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column (8/12 roughly represented by weight 2f)
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "SELECT FACTION",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HeaderLine(color = NeonCyan, modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        text = "Choose your alignment. Your choice will dictate initial resources and tactical advantages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Faction Grid
                IndustrialPanel {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "AVAILABLE FACTIONS",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val factions = Faction.values().filter { it != Faction.ANCIENT_NPC }
                            items(factions) { faction ->
                                FactionCard(
                                    faction = faction,
                                    isSelected = faction == selectedFaction,
                                    onClick = { selectedFaction = faction }
                                )
                            }
                        }
                    }
                }

                // Faction Details
                IndustrialPanel(
                    borderColor = getFactionColor(selectedFaction)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = selectedFaction.name.uppercase(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = getFactionColor(selectedFaction)
                                )
                                Text(
                                    text = "Militaristic Hegemony", // Should be derived from faction
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = null,
                                tint = getFactionColor(selectedFaction).copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "A rigid, martial society focused on overwhelming firepower and fortified defenses. The Dominion believes peace is only achieved through absolute control of space.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)).padding(8.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Bonus Badge
                            Box(modifier = Modifier.weight(1f).padding(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                Column {
                                    Text("FACTION BONUS", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    Text("+15% Hull Integrity, -10% Ship Build Time", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            // Units Badge
                            Box(modifier = Modifier.weight(1f).padding(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                Column {
                                    Text("CORE FLEET", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                    Text("Dreadnoughts, Heavy Cruisers, Flak Frigates", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Right Column (4/12 roughly represented by weight 1f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IndustrialPanel {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "SECTOR CONFIGURATION",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HeaderLine(color = TextSecondary, modifier = Modifier.padding(bottom = 24.dp))

                        // Map Archetype
                        Text("GALAXY ARCHETYPE", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            MapArchetype.values().forEach { archetype ->
                                val isSelected = archetype == selectedArchetype
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedArchetype = archetype }
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = archetype.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }

                        // Map Size
                        Text("SECTOR SIZE", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        ) {
                            MapSize.values().forEach { mapSize ->
                                val isSelected = mapSize == selectedMapSize
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedMapSize = mapSize }
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mapSize.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        }

                        // Commander Name
                        Text("COMMANDER CALLSIGN", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                        TextField(
                            value = commanderCallsign,
                            onValueChange = { commanderCallsign = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = NeonCyan,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        // Start Button
                        IndustrialButton(
                            text = "INITIALIZE",
                            onClick = { onStartGameClick(selectedFaction, selectedMapSize, selectedArchetype) },
                            isPrimary = true,
                            color = getFactionColor(selectedFaction),
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FactionCard(
    faction: Faction,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) getFactionColor(faction) else MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .size(140.dp)
            .clickable(onClick = onClick)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(1.dp), // placeholder for border
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                tint = if (isSelected) getFactionColor(faction) else TextSecondary,
                modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
            )
            Text(
                text = faction.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else TextSecondary
            )
        }
    }
}

fun getFactionColor(faction: Faction): Color {
    return when (faction) {
        Faction.DOMINION -> NeonRed
        Faction.TRADERS -> NeonGold
        Faction.SYNTH -> NeonCyan
        Faction.NOMADS -> NeonOrange
        Faction.KAELEN -> NeonGreen
        Faction.XYLAR -> Color.Cyan
        Faction.ANCIENT_NPC -> Color.Magenta
    }
}
