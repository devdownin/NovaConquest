package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

data class CosmeticItem(val name: String, val type: String, val price: Int, val color: Color)

@Composable
fun BlackMarketScreen() {
    val items = listOf(
        CosmeticItem("Obsidian Stealth", "Cruiser Skin", 1200, NeonRed),
        CosmeticItem("Solar Flare", "Fighter Skin", 800, NeonOrange),
        CosmeticItem("Void Walker", "Battleship Skin", 2000, Color(0xFFB026FF))
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
                text = "BLACK MARKET",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFFB026FF)
            )
            Text(
                text = "4,500 Crystals",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Daily Deal Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, NeonGold)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DAILY DEAL: NEBULA CAMO",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonGold
                )
                Text(text = "50% OFF - 500 Crystals", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items.size) { index ->
                CosmeticCard(item = items[index])
            }
        }
    }
}

@Composable
fun CosmeticCard(item: CosmeticItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, item.color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = item.color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, item.color)
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = item.color
                )
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            IndustrialButton(
                text = "PURCHASE (\${item.price})",
                onClick = { },
                color = item.color,
                modifier = Modifier.width(140.dp)
            )
        }
    }
}
