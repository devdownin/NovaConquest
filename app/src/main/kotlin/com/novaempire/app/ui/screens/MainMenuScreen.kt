package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.HalftoneBackground
import com.novaempire.app.ui.components.HeaderLine
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.NoiseOverlay
import com.novaempire.app.ui.theme.NeonCyan

@Composable
fun MainMenuScreen(
    hasSavedGame: Boolean,
    onNewGameClick: () -> Unit,
    onResumeGameClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background effects
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
                IconButton(onClick = { /* Menu */ }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = NeonCyan)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "ASTRA_COMMAND",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
            }
            Icon(Icons.Default.Menu, contentDescription = "Wallet Placeholder", tint = NeonCyan) // Using Menu as placeholder for AccountBalanceWallet based on rules
        }

        // Main Content Panel
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            IndustrialPanel(
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(0.9f),
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "NOVA EMPIRE",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        HeaderLine(modifier = Modifier.padding(top = 8.dp))
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IndustrialButton(
                            text = "NEW CAMPAIGN",
                            onClick = onNewGameClick,
                            isPrimary = true,
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan) }
                        )

                        val resumeColor = if (hasSavedGame) MaterialTheme.colorScheme.onSurfaceVariant else com.novaempire.app.ui.theme.TextSecondary
                        IndustrialButton(
                            text = "RESUME COMMAND",
                            onClick = if (hasSavedGame) onResumeGameClick else { {} },
                            color = resumeColor,
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = resumeColor) }
                        )

                        IndustrialButton(
                            text = "SYSTEM SETTINGS",
                            onClick = onSettingsClick,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                }
            }
        }
    }
}
