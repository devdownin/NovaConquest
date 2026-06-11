package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
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
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.components.IndustrialPanel
import com.novaempire.app.ui.components.NoiseOverlay
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.Hero
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.state.GameState

@Composable
fun HeroAcademyScreen(
    gameState: GameState,
    onRecruitClick: (String) -> Unit,
    onUseAbility: (String) -> Unit = {},
    onBackClick: () -> Unit
) {
    val playerState = gameState.playerStates[gameState.activeFaction]
    val credits = playerState?.credits ?: 0
    val recruitedHeroes = playerState?.recruitedHeroes ?: emptyList()

    val heroAbilitiesUsed = playerState?.heroAbilitiesUsed ?: emptySet()
    val allHeroes = listOf(
        Hero("hero_vance", "Commander Vance", Faction.DOMINION, 1500, "+15% Raw Damage Output"),
        Hero("hero_kael", "Architect Kael", Faction.SYNTH, 1200, "-10% Tech Research Cost"),
        Hero("hero_nix", "High Seer Nix", Faction.XYLAR, 2000, "+1 HP Fleet Regen / Turn"),
        Hero("hero_elara", "Admiral Elara", Faction.DOMINION, 1800, "+10% Income + 2C flat bonus")
    )
    val recruitedHeroObjects = allHeroes.filter { it.id in recruitedHeroes }
    val availableHeroes = allHeroes.filter { it.id !in recruitedHeroes }
    val heroAbilityDescriptions = mapOf(
        HeroRegistry.VANCE to "Frappe de Suppression — all fleet units may fire again this turn",
        HeroRegistry.KAEL to "Prototype — complete current research instantly",
        HeroRegistry.NIX to "Refuge Stellaire — fully heal all friendly units",
        HeroRegistry.ELARA to "Convoi Commercial — gain +80 Credits immediately"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HalftoneBackground(modifier = Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.05f))
        NoiseOverlay(modifier = Modifier.fillMaxSize(), alpha = 0.05f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NeonCyan)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("HERO ACADEMY", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                IndustrialPanel(modifier = Modifier.height(48.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CREDITS", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${credits} C", style = MaterialTheme.typography.headlineMedium, color = NeonCyan)
                    }
                }
            }

            if (recruitedHeroObjects.isNotEmpty()) {
                Text(
                    text = "YOUR COMMANDERS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                recruitedHeroObjects.forEach { hero ->
                    val abilityUsed = heroAbilitiesUsed.contains(hero.id)
                    IndustrialPanel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), borderColor = NeonCyan.copy(alpha = 0.4f)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hero.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                                Text(
                                    heroAbilityDescriptions[hero.id] ?: hero.bonusDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (abilityUsed) TextSecondary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            IndustrialButton(
                                text = if (abilityUsed) "USED" else "USE ABILITY",
                                onClick = { if (!abilityUsed) onUseAbility(hero.id) },
                                isPrimary = !abilityUsed,
                                color = if (abilityUsed) TextSecondary else NeonOrange,
                                modifier = Modifier.widthIn(min = 120.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "RECRUIT COMMANDERS",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(availableHeroes) { hero ->
                    HeroCard(
                        hero = hero,
                        canAfford = credits >= hero.cost,
                        onRecruit = { onRecruitClick(hero.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCard(
    hero: Hero,
    canAfford: Boolean,
    onRecruit: () -> Unit
) {
    val accentColor = NeonCyan
    val icon = Icons.Default.Menu

    IndustrialPanel(borderColor = accentColor.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Portrait placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = hero.name.uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "SIGNATURE ABILITY", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Text(text = hero.bonusDescription, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "${hero.cost} C", style = MaterialTheme.typography.headlineMedium, color = if (canAfford) NeonCyan else NeonRed)
                IndustrialButton(
                    text = "RECRUIT",
                    onClick = onRecruit,
                    isPrimary = canAfford,
                    color = if (canAfford) NeonCyan else TextSecondary,
                    modifier = Modifier.widthIn(min = 120.dp)
                )
            }
        }
    }
}
