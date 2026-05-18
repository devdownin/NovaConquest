package com.novaempire.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.theme.NeonCyan

class ClippedCornerShape(private val cornerSize: Float = 20f) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(cornerSize, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - cornerSize)
            lineTo(size.width - cornerSize, size.height)
            lineTo(0f, size.height)
            lineTo(0f, cornerSize)
            close()
        }
        return Outline.Generic(path)
    }
}

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ClippedCornerShape(16f))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(1.dp) // Space for border
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ClippedCornerShape(16f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isPrimary) 0.1f else 0.8f))
                .padding(vertical = 12.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }

            // Add bolts on primary buttons
            if (isPrimary) {
                /* Bolt removed */
                /* Bolt removed */
            }
        }

        // Draw custom border to match clipped shape
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val path = Path().apply {
                val cornerSize = 16f
                moveTo(cornerSize, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height - cornerSize)
                lineTo(size.width - cornerSize, size.height)
                lineTo(0f, size.height)
                lineTo(0f, cornerSize)
                close()
            }
            drawPath(
                path = path,
                color = borderColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}
