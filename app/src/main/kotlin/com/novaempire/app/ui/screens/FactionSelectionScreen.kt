package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.Faction

@Composable
fun FactionSelectionScreen(
    onStartGameClick: (Faction) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedFaction by remember { mutableStateOf(Faction.DOMINION) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "SELECT FACTION",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp, top = 32.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val factions = Faction.values().filter { it != Faction.ANCIENT_NPC }
            items(factions) { faction ->
                FactionCard(
                    faction = faction,
                    isSelected = faction == selectedFaction,
                    onClick = { selectedFaction = faction }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Faction Details Panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = selectedFaction.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = getFactionColor(selectedFaction)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Faction attributes and lore will be displayed here.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IndustrialButton(
                text = "BACK",
                onClick = onBackClick,
                modifier = Modifier.weight(1f),
                color = TextSecondary
            )
            IndustrialButton(
                text = "START GAME",
                onClick = { onStartGameClick(selectedFaction) },
                modifier = Modifier.weight(2f)
            )
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

    Surface(
        modifier = Modifier
            .width(120.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = faction.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) getFactionColor(faction) else TextPrimary
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
        Faction.XYLAR -> Color.Cyan // fallback
        Faction.ANCIENT_NPC -> Color.Magenta // fallback
    }
}
