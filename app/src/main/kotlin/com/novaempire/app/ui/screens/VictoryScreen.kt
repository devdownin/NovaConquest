package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.novaempire.app.ui.theme.TextSecondary

@Composable
fun VictoryScreen(onMainMenuClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "VICTORY ACHIEVED",
                style = MaterialTheme.typography.displayLarge,
                color = NeonCyan
            )
            Text(
                text = "Technological Dominance",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VictoryStat("Faction", "Xylar")
                    Spacer(modifier = Modifier.height(16.dp))
                    VictoryStat("Cycles Elapsed", "64")
                    Spacer(modifier = Modifier.height(16.dp))
                    VictoryStat("Systems Controlled", "12/24")
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "FINAL SCORE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "18,450",
                        style = MaterialTheme.typography.displayLarge,
                        color = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IndustrialButton(text = "VIEW ARCHIVE", onClick = { })
                IndustrialButton(text = "MAIN MENU", onClick = onMainMenuClick, color = TextSecondary)
            }
        }
    }
}

@Composable
fun VictoryStat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}
