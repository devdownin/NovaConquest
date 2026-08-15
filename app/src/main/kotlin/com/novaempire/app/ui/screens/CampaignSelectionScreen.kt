package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.novaempire.core.domain.models.CampaignObjective
import com.novaempire.core.domain.models.CampaignObjectiveType
import com.novaempire.core.domain.models.GloryPerk
import com.novaempire.core.domain.models.GloryRegistry
import com.novaempire.core.domain.models.MissionSetup
import com.novaempire.core.domain.models.ObjectiveMode
import com.novaempire.core.domain.models.TechRegistry

@Composable
fun CampaignSelectionScreen(
    onStartMission: (CampaignMission, Set<String>) -> Unit,
    onBackClick: () -> Unit,
    /** Missions already completed, so the list can show progress instead of looking untouched. */
    completedMissions: Set<String> = emptySet(),
    /** Glory banked so far — the budget for the perks below. */
    gloryPoints: Int = 0
) {
    var selectedMission by remember { mutableStateOf<CampaignMission?>(null) }
    var selectedPerks by remember { mutableStateOf(emptySet<String>()) }
    val missions = CampaignRegistry.MISSIONS
    val spent = GloryRegistry.totalCost(selectedPerks)
    val remaining = gloryPoints - spent

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
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "GLOIRE $remaining / $gloryPoints",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else NeonCyan
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
                                isCompleted = mission.id in completedMissions,
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
                            // Scrollable: the perk list grows with the registry, and a fixed panel
                            // would push the launch button off-screen on a short device.
                            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
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
                                val mission = selectedMission!!
                                Text(
                                    // The mode is the whole point of a list: a checklist and a
                                    // choice of routes look identical without it.
                                    text = when (mission.objectiveMode) {
                                        ObjectiveMode.ALL -> "Objectifs — tous requis :"
                                        ObjectiveMode.ANY -> "Objectifs — un seul suffit :"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                mission.objectives.forEach { obj ->
                                    Text(
                                        text = "• ${describeObjective(obj)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (mission.bonusObjectives.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Objectifs secondaires (facultatifs) :",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    mission.bonusObjectives.forEach { bonus ->
                                        Text(
                                            text = "• ${describeObjective(bonus.objective)} → +${bonus.gloryReward} gloire",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Sans cela, le joueur choisit ses perks sans savoir ce que la
                                // mission lui donne déjà — et peut acheter un croiseur alors qu'il
                                // en reçoit trois.
                                val opening = describeSetup(mission.setup)
                                if (opening.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Départ imposé :",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    opening.forEach {
                                        Text(
                                            text = "• $it",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                if (selectedMission!!.turnLimit > 0) {
                                    Text(
                                        text = "• Deadline: ${selectedMission!!.turnLimit} tours",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedMission!!.gloryReward > 0) {
                                    Text(
                                        text = "• Gloire: +${selectedMission!!.gloryReward}" +
                                            if (selectedMission!!.id in completedMissions) " (déjà perçue)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Glory perks:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GloryRegistry.ALL_PERKS.forEach { perk ->
                                    val isTaken = perk.id in selectedPerks
                                    PerkItem(
                                        perk = perk,
                                        isTaken = isTaken,
                                        // Affordable means "affordable on top of what is already
                                        // chosen" — otherwise the player can build a basket the
                                        // engine will refuse, and the refusal arrives at launch.
                                        canAfford = isTaken || perk.cost <= remaining,
                                        onToggle = {
                                            selectedPerks =
                                                if (isTaken) selectedPerks - perk.id else selectedPerks + perk.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            IndustrialButton(
                                text = if (spent > 0) "LAUNCH MISSION — $spent GLOIRE" else "LAUNCH MISSION",
                                onClick = { onStartMission(selectedMission!!, selectedPerks) },
                                isPrimary = true,
                                enabled = remaining >= 0,
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

/**
 * Plain-language label for an objective.
 *
 * Purely descriptive — whether an objective is *met* is `VictoryChecker`'s business alone. This
 * formats what the mission asks for; it must never re-derive the rule, which is how the map screen
 * and the engine drifted apart in earlier audits.
 */
private fun describeObjective(objective: CampaignObjective): String = when (objective.type) {
    CampaignObjectiveType.SURVIVE_TURNS -> "Survivre ${objective.targetValue} tours"
    CampaignObjectiveType.ACCUMULATE_CREDITS -> "Détenir ${objective.targetValue} crédits"
    CampaignObjectiveType.DEFEAT_FACTION -> "Éliminer la faction ennemie"
    CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET -> "Capturer le monde en ${objective.targetString}"
}

/**
 * Lines describing a mission's scripted opening, empty when it starts the standard way.
 *
 * Descriptive only, like [describeObjective]: what the mission grants is `applyLoadout`'s business.
 * Ships are grouped by type so a squadron reads as "3 × CRUISER" rather than three identical lines.
 */
private fun describeSetup(setup: MissionSetup): List<String> = buildList {
    setup.startingCredits?.let { add("Trésor de départ : $it crédits") }
    if (setup.startingFleet.isNotEmpty()) {
        val fleet = setup.startingFleet.groupingBy { it }.eachCount()
            .entries.joinToString(", ") { (type, n) -> "$n × ${type.name}" }
        add("Flotte : $fleet")
    }
    if (setup.startingTechs.isNotEmpty()) {
        val names = setup.startingTechs.map { id -> TechRegistry.ALL_TECHS.find { it.id == id }?.name ?: id }
        add("Technologies acquises : ${names.joinToString(", ")}")
    }
    if (setup.startingPlanets.isNotEmpty()) {
        add("Mondes déjà tenus : ${setup.startingPlanets.joinToString(", ")}")
    }
}

/**
 * One buyable perk. Unaffordable entries stay visible but inert: hiding them would make the glory
 * economy invisible to a player who has not banked enough yet to see what it is for.
 */
@Composable
fun PerkItem(
    perk: GloryPerk,
    isTaken: Boolean,
    canAfford: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isTaken) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = canAfford, onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = perk.name,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    isTaken -> NeonCyan
                    canAfford -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            Text(
                text = perk.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = if (canAfford) 1f else 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${perk.cost}",
            style = MaterialTheme.typography.titleMedium,
            color = if (canAfford) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun MissionItem(
    mission: CampaignMission,
    isSelected: Boolean,
    isCompleted: Boolean = false,
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
                text = if (isCompleted) "Mission ${mission.id} — TERMINÉE" else "Mission ${mission.id}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
