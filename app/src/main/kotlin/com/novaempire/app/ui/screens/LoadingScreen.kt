package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.TextSecondary

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(150.dp),
                shape = CircleShape,
                color = NeonCyan.copy(alpha = 0.2f)
            ) {
                // Spinning emblem placeholder
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "NOVA EMPIRE",
                style = MaterialTheme.typography.displayLarge,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(64.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight()
                        .background(NeonCyan)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "INITIALIZING RENDERING PLUGINS... 75%",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonCyan
            )
        }

        Text(
            text = "TACTICAL TIP: Asteroid fields block large ships but provide cover for fighters.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}
