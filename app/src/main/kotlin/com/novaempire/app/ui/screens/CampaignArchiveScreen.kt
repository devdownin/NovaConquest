package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.theme.*

data class ArchiveEntry(val title: String, val faction: String, val status: String, val score: Int, val timestamp: String, val color: Color)

@Composable
fun CampaignArchiveScreen() {
    val entries = listOf(
        ArchiveEntry("Operation Starfall", "DOMINION", "VICTORY", 12450, "CYCLE 42", NeonRed),
        ArchiveEntry("The Outer Rim Expedition", "TRADERS", "DEFEAT", 3200, "CYCLE 38", TextSecondary),
        ArchiveEntry("Protocol Zero", "SYNTH", "VICTORY", 15100, "CYCLE 45", NeonCyan)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "DATA LOGS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(entries.size) { index ->
                ArchiveCard(entry = entries[index])
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "GLOBAL ACHIEVEMENTS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("100 Systems Conquered", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("All Tech Unlocked", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ArchiveCard(entry: ArchiveEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, entry.color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.title.uppercase(),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FACTION: \${entry.faction}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "STATUS: \${entry.status}",
                    style = MaterialTheme.typography.labelLarge,
                    color = entry.color
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SCORE: \${entry.score}",
                style = MaterialTheme.typography.headlineMedium,
                color = entry.color
            )
        }
    }
}
