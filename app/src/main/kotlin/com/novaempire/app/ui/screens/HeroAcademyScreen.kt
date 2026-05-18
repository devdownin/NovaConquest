package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.*
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.Hero
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.state.GameState

@Composable
fun HeroAcademyScreen(
    gameState: GameState,
    onRecruitClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0
    val recruitedHeroes = playerState?.recruitedHeroes ?: emptySet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HERO ACADEMY",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "\$credits C",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(HeroRegistry.ALL_HEROES.size) { index ->
                val hero = HeroRegistry.ALL_HEROES[index]
                val isRecruited = recruitedHeroes.contains(hero.id)
                val canAfford = credits >= hero.cost

                HeroCard(
                    hero = hero,
                    isRecruited = isRecruited,
                    canAfford = canAfford,
                    onRecruit = { onRecruitClick(hero.id) }
                )
            }
        }
    }
}

@Composable
fun HeroCard(hero: Hero, isRecruited: Boolean, canAfford: Boolean, onRecruit: () -> Unit) {
    val color = when (hero.targetFaction) {
        Faction.DOMINION -> NeonRed
        Faction.TRADERS -> NeonOrange
        Faction.SYNTH -> NeonCyan
        Faction.ANCIENT_NPC -> Color(0xFFB026FF)
        else -> NeonCyan
    }

    val alpha = if (isRecruited) 0.5f else 1.0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f * alpha),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f * alpha))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CutCornerShape(8.dp),
                border = BorderStroke(1.dp, color.copy(alpha = alpha))
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hero.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = "FACTION: \${hero.targetFaction.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = color.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BONUS: \${hero.bonusDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary.copy(alpha = alpha)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (isRecruited) {
                Text(
                    text = "RECRUITED",
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
            } else {
                IndustrialButton(
                    text = "RECRUIT (\${hero.cost} C)",
                    onClick = onRecruit,
                    color = if (canAfford) color else NeonRed,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
