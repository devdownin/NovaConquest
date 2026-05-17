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

@Composable
fun DiplomacyIntelScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                text = "TURN 42",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Factions List
        Text(
            text = "KNOWN FACTIONS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        FactionIntelCard(
            name = "Traders",
            status = "Neutral",
            accentColor = NeonGold,
            intel = "FLEET STRENGTH: WEAK\\nCAPITAL: SERENITY STATION"
        )
        Spacer(modifier = Modifier.height(16.dp))
        FactionIntelCard(
            name = "Xylar",
            status = "Hostile",
            accentColor = NeonCyan,
            intel = "FLEET STRENGTH: UNKNOWN\\nCAPITAL: CRYSTAL SPIRE"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Scoreboard
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
                ScoreRow(rank = 1, name = "DOMINION", score = 450, color = NeonRed)
                ScoreRow(rank = 2, name = "SYNTH", score = 320, color = NeonCyan)
                ScoreRow(rank = 3, name = "TRADERS", score = 210, color = NeonGold)
            }
        }
    }
}

@Composable
fun FactionIntelCard(name: String, status: String, accentColor: androidx.compose.ui.graphics.Color, intel: String) {
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
                    text = "STATUS: ${status.uppercase()}",
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
                IndustrialButton(text = "PROPOSE ALLIANCE", onClick = { }, modifier = Modifier.weight(1f))
                IndustrialButton(text = "DECLARE WAR", onClick = { }, color = NeonRed, modifier = Modifier.weight(1f))
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
        Text(text = "$rank. $name", style = MaterialTheme.typography.bodyLarge, color = color)
        Text(text = score.toString(), style = MaterialTheme.typography.bodyLarge)
    }
}
