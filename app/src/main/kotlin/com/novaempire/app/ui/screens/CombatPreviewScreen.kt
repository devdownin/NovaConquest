package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonGold
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit

@Composable
fun CombatPreviewScreen(
    attacker: GameUnit,
    defender: GameUnit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val attackerColor = getFactionColor(attacker.faction)
    val defenderColor = getFactionColor(defender.faction)

    // Simplified damage prediction
    val predictedDamage = attacker.type.attack
    val predictedCounter = if (predictedDamage >= defender.currentHp) 0 else defender.type.attack
    val targetDestroyed = predictedDamage >= defender.currentHp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)), // Overlay background
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "COMBAT PREVIEW",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonRed
                )
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attacker
                    CombatEntity(
                        name = "\${attacker.faction.name} \${attacker.type.name}",
                        hp = "\${attacker.currentHp}/\${attacker.type.maxHp}",
                        atk = "\${attacker.type.attack}",
                        color = attackerColor
                    )
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // Defender
                    CombatEntity(
                        name = "\${defender.faction.name} \${defender.type.name}",
                        hp = "\${defender.currentHp}/\${defender.type.maxHp}",
                        atk = "\${defender.type.attack}",
                        color = defenderColor
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "ESTIMATED DAMAGE: \$predictedDamage",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonRed
                )
                if (targetDestroyed) {
                    Text(
                        text = "TARGET DESTROYED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonRed
                    )
                }
                Text(
                    text = "COUNTER-ATTACK: \$predictedCounter",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IndustrialButton(
                        text = "CANCEL",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        color = TextSecondary
                    )
                    IndustrialButton(
                        text = "CONFIRM ATTACK",
                        onClick = onConfirm,
                        modifier = Modifier.weight(2f),
                        color = NeonRed
                    )
                }
            }
        }
    }
}

@Composable
fun CombatEntity(name: String, hp: String, atk: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge, color = color)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.size(80.dp),
            color = color.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, color)
        ) {}
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "HP: \$hp", style = MaterialTheme.typography.labelLarge)
        Text(text = "ATK: \$atk", style = MaterialTheme.typography.labelLarge)
    }
}
