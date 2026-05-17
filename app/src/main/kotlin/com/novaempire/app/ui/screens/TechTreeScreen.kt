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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.TextSecondary

enum class TechState { UNLOCKED, AVAILABLE, LOCKED }

data class TechNode(val name: String, val cost: Int, val state: TechState)

@Composable
fun TechTreeScreen() {
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
                text = "124 C",
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
        ) {
            TechBranch(
                title = "MILITARY",
                nodes = listOf(
                    TechNode("Hull Plating", 0, TechState.UNLOCKED),
                    TechNode("Plasma Weapons", 8, TechState.AVAILABLE),
                    TechNode("Siege Protocols", 15, TechState.LOCKED)
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            TechBranch(
                title = "EXPANSION",
                nodes = listOf(
                    TechNode("Deep Scanners", 0, TechState.UNLOCKED),
                    TechNode("Terraforming", 15, TechState.AVAILABLE),
                    TechNode("Wormhole Navigation", 20, TechState.LOCKED)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TechBranch(title: String, nodes: List<TechNode>, modifier: Modifier = Modifier) {
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
            TechNodeCard(node = node)
            if (index < nodes.size - 1) {
                // Connection Line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(if (node.state == TechState.UNLOCKED) NeonCyan else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

@Composable
fun TechNodeCard(node: TechNode) {
    val borderColor = when (node.state) {
        TechState.UNLOCKED -> Color.Transparent
        TechState.AVAILABLE -> NeonCyan
        TechState.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val alpha = if (node.state == TechState.LOCKED) 0.5f else 1.0f

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (node.state == TechState.UNLOCKED) TextSecondary else MaterialTheme.colorScheme.onSurface
            )
            if (node.state == TechState.AVAILABLE) {
                Spacer(modifier = Modifier.height(8.dp))
                IndustrialButton(
                    text = "RESEARCH (${node.cost} C)",
                    onClick = { }
                )
            }
        }
    }
}
