package com.novaempire.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.theme.BrunEncre
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.SurfaceDark
import com.novaempire.app.ui.theme.TextSecondary

val OutlineColor = Color(0xFF3D3428)   // rouille sombre, pas gris froid

// 1. Boulon rouillé
@Composable
fun MountingBolt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(4.dp)
            .background(OutlineColor, shape = RoundedCornerShape(50))
    )
}

// 2. Panneau industriel Bilal — double bordure encre, hachures diagonales
@Composable
fun BilalPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)  // asymétrique BD
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceDark)
            .border(BorderStroke(2.dp, accentColor.copy(alpha = 0.55f)), shape)
            .padding(1.dp)
            .border(BorderStroke(1.dp, BrunEncre), shape)
    ) {
        // Hachures diagonales — texture métal oxydé
        Canvas(modifier = Modifier.matchParentSize()) {
            val pitch = 14f
            var startX = -size.height
            while (startX < size.width + size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(startX, 0f),
                    end = Offset(startX + size.height, size.height),
                    strokeWidth = 1f
                )
                startX += pitch
            }
        }
        content()
    }
}

// 3. Panneau industriel classique (compatible ancien code)
@Composable
fun IndustrialPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = OutlineColor,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    shape: Shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = shape,
        border = BorderStroke(2.dp, borderColor)  // 2dp encre épaisse
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
            MountingBolt(Modifier.padding(5.dp).align(Alignment.TopStart))
            MountingBolt(Modifier.padding(5.dp).align(Alignment.TopEnd))
            MountingBolt(Modifier.padding(5.dp).align(Alignment.BottomStart))
            MountingBolt(Modifier.padding(5.dp).align(Alignment.BottomEnd))
        }
    }
}

// 4. Bouton Industriel — coins coupés asymétriques
@Composable
fun IndustrialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    isPrimary: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val backgroundColor = if (isPrimary) color.copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isPrimary) color else OutlineColor
    val shape = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(2.dp, borderColor, shape)   // 2dp encre
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 22.dp),
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

// 5. Ligne de titre — trait encre horizontal
@Composable
fun HeaderLine(color: Color = NeonCyan, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.4f)
            .height(2.dp)
            .background(color)
    )
}
