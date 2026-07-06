package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.HalftoneBackground
import com.novaempire.app.ui.components.HeaderLine
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.InkWashOverlay
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.core.domain.models.CampaignRegistry
import com.novaempire.core.domain.models.CampaignMission

@Composable
fun CampaignSelectionScreen(
    onStartMission: (CampaignMission) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedMission by remember { mutableStateOf<CampaignMission?>(null) }
    val missions = CampaignRegistry.MISSIONS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HalftoneBackground(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.03f))
        InkWashOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "CAMPAIGN MISSIONS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
            }

            // Main Content
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // List of missions
                IndustrialPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(missions) { mission ->
                            MissionItem(
                                mission = mission,
                                isSelected = mission == selectedMission,
                                onClick = { selectedMission = mission }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Mission details
                IndustrialPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    if (selectedMission != null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = selectedMission!!.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = NeonCyan
                                )
                                HeaderLine(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = selectedMission!!.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Objectives:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val obj = selectedMission!!.objective
                                Text(
                                    text = "• Type: ${obj.type.name}\n• Target: ${if (obj.targetString.isNotEmpty()) obj.targetString else obj.targetValue}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IndustrialButton(
                                text = "LAUNCH MISSION",
                                onClick = { onStartMission(selectedMission!!) },
                                isPrimary = true,
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "SELECT A MISSION",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionItem(
    mission: CampaignMission,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = mission.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) NeonCyan else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mission ${mission.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
