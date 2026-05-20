package com.novaempire.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.VoidBlack
import com.novaempire.app.ui.theme.TextSecondary
val OutlineColor = Color(0xFF8F9094)

// 1. Boulon
@Composable
fun MountingBolt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(4.dp)
            .background(OutlineColor, shape = RoundedCornerShape(50))
    )
}

// 2. Panneau Industriel avec verre dépoli simulé et boulons
@Composable
fun IndustrialPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    shape: Shape = RoundedCornerShape(4.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = shape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()

            // Mounting Bolts in corners
            MountingBolt(Modifier.padding(6.dp).align(Alignment.TopStart))
            MountingBolt(Modifier.padding(6.dp).align(Alignment.TopEnd))
            MountingBolt(Modifier.padding(6.dp).align(Alignment.BottomStart))
            MountingBolt(Modifier.padding(6.dp).align(Alignment.BottomEnd))
        }
    }
}

// 3. Bouton Industriel avec coins coupés
@Composable
fun IndustrialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    isPrimary: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val backgroundColor = if (isPrimary) color.copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isPrimary) color else MaterialTheme.colorScheme.surfaceVariant
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
        }

        if (isPrimary) {
            MountingBolt(Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-8).dp))
            MountingBolt(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 8.dp))
        }
    }
}

// 4. Ligne de titre lumineuse
@Composable
fun HeaderLine(color: Color = NeonCyan, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.3f)
            .height(2.dp)
            .background(color)
    )
}
