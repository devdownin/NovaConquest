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

data class HeroData(val name: String, val faction: String, val bonus: String, val cost: Int, val color: Color)

@Composable
fun HeroAcademyScreen() {
    val heroes = listOf(
        HeroData("Commander Vance", "DOMINION", "+15% Fleet Attack", 50, NeonRed),
        HeroData("Captain Elara", "TRADERS", "+10% Trade Income", 40, NeonOrange),
        HeroData("High Seer Nix", "ANCIENTS", "Passive Fleet Healing", 75, Color(0xFFB026FF)),
        HeroData("Architect Kael", "SYNTH", "-10% Tech Cost", 60, NeonCyan)
    )

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
                text = "124 C",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(heroes.size) { index ->
                HeroCard(hero = heroes[index])
            }
        }
    }
}

@Composable
fun HeroCard(hero: HeroData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, hero.color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for avatar
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CutCornerShape(8.dp),
                border = BorderStroke(1.dp, hero.color)
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hero.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "FACTION: \${hero.faction}",
                    style = MaterialTheme.typography.labelLarge,
                    color = hero.color
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BONUS: \${hero.bonus}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            IndustrialButton(
                text = "RECRUIT (\${hero.cost} C)",
                onClick = { },
                color = hero.color,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}
