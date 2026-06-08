package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.TechBranch
import com.novaempire.core.domain.models.TechDefinition
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.CostCalculator

enum class TechNodeState { UNLOCKED, RESEARCHING, AVAILABLE, LOCKED }

data class UiTechNode(
    val id: String,
    val name: String,
    val description: String = "",
    val cost: Int,
    val state: TechNodeState,
    val canAfford: Boolean,
    val turnsRemaining: Int = 0
)

@Composable
fun TechTreeScreen(
    gameState: GameState,
    onResearchTech: (String) -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0
    val unlockedTechs = playerState?.techUnlocked ?: emptySet()

    val research = playerState?.researchInProgress

    fun buildUiNode(tech: TechDefinition): UiTechNode {
        val isUnlocked = unlockedTechs.contains(tech.id)
        val isResearching = research?.techId == tech.id
        val isAvailable = !isUnlocked && !isResearching &&
            research == null &&
            (tech.requiresTechId == null || unlockedTechs.contains(tech.requiresTechId))
        val hasKael = playerState?.recruitedHeroes?.contains("hero_kael") == true
        val cost = CostCalculator.techCost(tech.id, unlockedTechs, hasKael)

        val nodeState = when {
            isUnlocked -> TechNodeState.UNLOCKED
            isResearching -> TechNodeState.RESEARCHING
            isAvailable -> TechNodeState.AVAILABLE
            else -> TechNodeState.LOCKED
        }

        return UiTechNode(
            id = tech.id,
            name = tech.name,
            description = tech.description,
            cost = cost,
            state = nodeState,
            canAfford = credits >= cost,
            turnsRemaining = if (isResearching) research!!.turnsRemaining else 0
        )
    }

    val militaryNodes = TechRegistry.ALL_TECHS.filter { it.branch == TechBranch.MILITARY }.sortedBy { it.tier }.map(::buildUiNode)
    val expansionNodes = TechRegistry.ALL_TECHS.filter { it.branch == TechBranch.EXPANSION }.sortedBy { it.tier }.map(::buildUiNode)
    val explorationNodes = TechRegistry.ALL_TECHS.filter { it.branch == TechBranch.EXPLORATION }.sortedBy { it.tier }.map(::buildUiNode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RESEARCH & DEVELOPMENT",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "$credits C",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tech Branches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            TechBranchView(
                title = "MILITARY",
                nodes = militaryNodes,
                onResearchClick = onResearchTech,
                modifier = Modifier.width(300.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            TechBranchView(
                title = "EXPANSION",
                nodes = expansionNodes,
                onResearchClick = onResearchTech,
                modifier = Modifier.width(300.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            TechBranchView(
                title = "EXPLORATION",
                nodes = explorationNodes,
                onResearchClick = onResearchTech,
                modifier = Modifier.width(300.dp)
            )
        }
    }
}

@Composable
fun TechBranchView(title: String, nodes: List<UiTechNode>, onResearchClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        nodes.forEachIndexed { index, node ->
            TechNodeCard(node = node, onResearchClick = { onResearchClick(node.id) })
            if (index < nodes.size - 1) {
                val connectorColor = if (node.state == TechNodeState.UNLOCKED) NeonCyan else MaterialTheme.colorScheme.surfaceVariant
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(2.dp).height(12.dp).background(connectorColor))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = connectorColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.width(2.dp).height(12.dp).background(connectorColor))
                }
            }
        }
    }
}

@Composable
fun TechNodeCard(node: UiTechNode, onResearchClick: () -> Unit) {
    val borderColor = when (node.state) {
        TechNodeState.UNLOCKED -> Color.Transparent
        TechNodeState.RESEARCHING -> NeonOrange
        TechNodeState.AVAILABLE -> if (node.canAfford) NeonCyan else NeonRed
        TechNodeState.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val alpha = if (node.state == TechNodeState.LOCKED) 0.5f else 1.0f

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (node.state == TechNodeState.UNLOCKED) NeonCyan else MaterialTheme.colorScheme.onSurface
            )
            if (node.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = node.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            when (node.state) {
                TechNodeState.AVAILABLE -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    IndustrialButton(
                        text = "RESEARCH (${node.cost} C)",
                        onClick = onResearchClick,
                        color = if (node.canAfford) NeonCyan else NeonRed
                    )
                }
                TechNodeState.RESEARCHING -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RESEARCHING — ${node.turnsRemaining} TURN${if (node.turnsRemaining > 1) "S" else ""} LEFT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonOrange
                    )
                }
                TechNodeState.UNLOCKED -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("UNLOCKED", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                }
                TechNodeState.LOCKED -> {}
            }
        }
    }
}
