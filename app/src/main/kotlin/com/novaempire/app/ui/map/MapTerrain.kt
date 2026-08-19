package com.novaempire.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.novaempire.app.ui.screens.getFactionColor
import com.novaempire.app.ui.theme.MapPalette
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonGreen
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.core.domain.models.Faction
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Les huit terrains de la carte, un par fonction.
//
// Chacune reçoit la palette du thème actif : c'est ce qui permet à un thème saisonnier de
// repeindre le plateau sans toucher une ligne de ce fichier.

fun DrawScope.drawPlanet(
    x: Float,
    y: Float,
    hexRadius: Float,
    owner: Faction?,
    graphicsConfig: com.novaempire.core.domain.theme.GraphicsConfig,
    palette: MapPalette
) {
    val planetColor = owner?.let { getFactionColor(it) } ?: NeonGreen
    val inkBlack = palette.ink

    // Disque planète — gradient mat, pas de glow électrique
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to planetColor.copy(alpha = 0.55f),
                0.6f to planetColor.copy(alpha = 0.25f),
                1.0f to Color.Transparent
            ),
            center = Offset(x - hexRadius * 0.1f, y - hexRadius * 0.1f),
            radius = hexRadius * 0.58f
        ),
        radius = hexRadius * 0.58f,
        center = Offset(x, y)
    )

    // Ombrage par hachures (Cross-hatching style Graphic Noir)
    clipPath(
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(x - hexRadius * 0.52f, y - hexRadius * 0.52f, x + hexRadius * 0.52f, y + hexRadius * 0.52f)) }
    ) {
        for (i in 0 until 15) {
            val hY = y + hexRadius * 0.1f + i * 4f
            if (hY < y + hexRadius * 0.52f) {
                drawLine(
                    // `planetShadowAlpha` existait dans les JSON et dans le guide de thème, mais
                    // n'était lu nulle part : la valeur était figée à 0.6 ici même.
                    color = inkBlack.copy(alpha = graphicsConfig.planetShadowAlpha),
                    start = Offset(x - hexRadius * 0.5f, hY),
                    end = Offset(x + hexRadius * 0.5f, hY - hexRadius * 0.3f),
                    strokeWidth = 1f
                )
            }
        }
    }

    // Contour encre épaisse BD avec style plus texturé
    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.52f,
        center = Offset(x, y),
        style = Stroke(width = graphicsConfig.outlineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawCircle(
        color = planetColor.copy(alpha = 0.7f),
        radius = hexRadius * 0.52f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // Anneau orbital discret
    val ringPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(
            Offset(x - hexRadius * 0.75f, y - hexRadius * 0.18f),
            Size(hexRadius * 1.5f, hexRadius * 0.36f)
        ))
    }
    drawPath(path = ringPath, color = planetColor.copy(alpha = 0.28f), style = Stroke(width = 1f))

    // Glint planète
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(x - hexRadius * 0.25f, y - hexRadius * 0.38f),
        end   = Offset(x + hexRadius * 0.05f, y - hexRadius * 0.18f),
        strokeWidth = 1.5f
    )

    // Détails urbanisation (3 points)
    for (i in 0 until 3) {
        val angle = (i * 120f + 20f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.2f
        drawCircle(
            color = planetColor.copy(alpha = 0.8f),
            radius = 2.5f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }

    // Tirets possession — pointillés discrets si propriétaire
    if (owner != null) {
        drawCircle(
            color = planetColor.copy(alpha = 0.35f),
            radius = hexRadius * 0.42f,
            center = Offset(x, y),
            style = Stroke(width = 1.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        )
    }
}

fun DrawScope.drawAsteroids(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val offsets = listOf(
        Offset(-15f, -20f), Offset(10f, -25f), Offset(20f, 10f),
        Offset(-25f, 15f), Offset(0f, 25f), Offset(-5f, 0f)
    )
    val sizes = listOf(8f, 12f, 10f, 14f, 6f, 16f)
    val inkBlack = palette.ink

    offsets.forEachIndexed { index, offset ->
        val ax = x + offset.x
        val ay = y + offset.y
        val r = sizes[index]

        val path = Path()
        path.moveTo(ax, ay - r)
        path.lineTo(ax + r * 0.8f, ay - r * 0.3f)
        path.lineTo(ax + r, ay + r * 0.4f)
        path.lineTo(ax + r * 0.2f, ay + r)
        path.lineTo(ax - r * 0.6f, ay + r * 0.8f)
        path.lineTo(ax - r, ay)
        path.close()

        drawPath(path, color = palette.asteroidRock, style = Fill)           // roche sombre
        drawPath(path, color = inkBlack, style = Stroke(width = 2.5f))    // encre épaisse
        drawPath(path, color = NeonOrange.copy(alpha = 0.5f), style = Stroke(width = 1f))  // rouille
    }
}

fun DrawScope.drawNebula(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    // Nuage violet-brume Bilal — pas de violet électrique
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.nebulaHaze.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x - 10f, y - 10f),
            radius = hexRadius * 0.72f
        ),
        radius = hexRadius * 0.72f,
        center = Offset(x - 10f, y - 10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.2f), Color.Transparent),
            center = Offset(x + 14f, y + 8f),
            radius = hexRadius * 0.55f
        ),
        radius = hexRadius * 0.55f,
        center = Offset(x + 14f, y + 8f)
    )
}

fun DrawScope.drawBlackHole(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink
    val eventHorizonColor = palette.blackHole

    // Accretion disk (distortion effect via gradient)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonOrange.copy(alpha = 0.8f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.8f
        ),
        radius = hexRadius * 0.8f,
        center = Offset(x, y)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonRed.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.95f
        ),
        radius = hexRadius * 0.95f,
        center = Offset(x, y)
    )

    // Event Horizon (the black hole itself)
    drawCircle(
        color = eventHorizonColor,
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Fill
    )

    // Thick comic book ink outline
    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Stroke(width = 3.5f)
    )

    // Inner glow / edge of the void
    drawCircle(
        color = NeonOrange.copy(alpha = 0.9f),
        radius = hexRadius * 0.35f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // White glint for stylistic consistency, distorted slightly
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(x - hexRadius * 0.2f, y - hexRadius * 0.2f),
        end   = Offset(x - hexRadius * 0.05f, y - hexRadius * 0.1f),
        strokeWidth = 1.5f
    )
}

fun DrawScope.drawWormhole(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink
    val wormholeColor = palette.wormhole

    // Spiral arms simulation with overlapping rotated ellipses
    for (i in 0 until 4) {
        val angle = (i * 45f)
        withTransform({
            rotate(angle, Offset(x, y))
        }) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(x, y),
                    radius = hexRadius * 0.7f
                ),
                topLeft = Offset(x - hexRadius * 0.7f, y - hexRadius * 0.2f),
                size = Size(hexRadius * 1.4f, hexRadius * 0.4f)
            )
            drawOval(
                color = NeonCyan.copy(alpha = 0.6f),
                topLeft = Offset(x - hexRadius * 0.7f, y - hexRadius * 0.2f),
                size = Size(hexRadius * 1.4f, hexRadius * 0.4f),
                style = Stroke(width = 1f)
            )
        }
    }

    // Central rift
    drawCircle(
        color = wormholeColor,
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Fill
    )

    drawCircle(
        color = inkBlack,
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Stroke(width = 3f)
    )

    drawCircle(
        color = NeonCyan.copy(alpha = 0.9f),
        radius = hexRadius * 0.25f,
        center = Offset(x, y),
        style = Stroke(width = 1.5f)
    )

    // Glint
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(x - hexRadius * 0.15f, y - hexRadius * 0.15f),
        end   = Offset(x + hexRadius * 0.05f, y - hexRadius * 0.05f),
        strokeWidth = 1.5f
    )
}

fun DrawScope.drawPlasmaCloud(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Rust/orange turbulent cloud
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonOrange.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(x - 5f, y + 10f),
            radius = hexRadius * 0.8f
        ),
        radius = hexRadius * 0.8f,
        center = Offset(x - 5f, y + 10f)
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonRed.copy(alpha = 0.4f), Color.Transparent),
            center = Offset(x + 12f, y - 12f),
            radius = hexRadius * 0.6f
        ),
        radius = hexRadius * 0.6f,
        center = Offset(x + 12f, y - 12f)
    )

    // Plasma arcs (jagged lines)
    val path = Path().apply {
        moveTo(x - hexRadius * 0.4f, y - hexRadius * 0.2f)
        lineTo(x - hexRadius * 0.1f, y - hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.1f, y - hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.4f, y - hexRadius * 0.3f)
    }
    drawPath(path, color = NeonOrange.copy(alpha = 0.8f), style = Stroke(width = 2f))

    val path2 = Path().apply {
        moveTo(x - hexRadius * 0.3f, y + hexRadius * 0.3f)
        lineTo(x, y + hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.2f, y + hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.5f, y + hexRadius * 0.2f)
    }
    drawPath(path2, color = NeonRed.copy(alpha = 0.7f), style = Stroke(width = 1.5f))

    // Dark matter specks in the plasma
    for (i in 0 until 5) {
        val angle = (i * 72f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.4f
        drawCircle(
            color = inkBlack,
            radius = 3f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }
}

fun DrawScope.drawIonStorm(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Heavy grey-blue cloud base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.ionStorm.copy(alpha = 0.7f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.85f
        ),
        radius = hexRadius * 0.85f,
        center = Offset(x, y)
    )

    // Energetic cyan flashes
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.3f), Color.Transparent),
            center = Offset(x - 15f, y - 5f),
            radius = hexRadius * 0.5f
        ),
        radius = hexRadius * 0.5f,
        center = Offset(x - 15f, y - 5f)
    )

    // Lightning strikes
    val lightning = Path().apply {
        moveTo(x - hexRadius * 0.2f, y - hexRadius * 0.5f)
        lineTo(x - hexRadius * 0.05f, y - hexRadius * 0.1f)
        lineTo(x - hexRadius * 0.2f, y)
        lineTo(x + hexRadius * 0.1f, y + hexRadius * 0.4f)
        lineTo(x, y + hexRadius * 0.1f)
        lineTo(x + hexRadius * 0.15f, y)
        close()
    }
    drawPath(lightning, color = NeonCyan.copy(alpha = 0.9f), style = Fill)
    drawPath(lightning, color = inkBlack, style = Stroke(width = 1.5f))

    val lightning2 = Path().apply {
        moveTo(x + hexRadius * 0.3f, y - hexRadius * 0.3f)
        lineTo(x + hexRadius * 0.1f, y)
        lineTo(x + hexRadius * 0.2f, y + hexRadius * 0.1f)
        lineTo(x, y + hexRadius * 0.3f)
    }
    drawPath(lightning2, color = NeonCyan.copy(alpha = 0.7f), style = Stroke(width = 2f))
}

fun DrawScope.drawAnomaly(x: Float, y: Float, hexRadius: Float, palette: MapPalette) {
    val inkBlack = palette.ink

    // Strange green-brown base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.anomaly.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x, y),
            radius = hexRadius * 0.75f
        ),
        radius = hexRadius * 0.75f,
        center = Offset(x, y)
    )

    // Unnatural geometry
    val path = Path().apply {
        moveTo(x, y - hexRadius * 0.4f)
        lineTo(x + hexRadius * 0.35f, y - hexRadius * 0.15f)
        lineTo(x + hexRadius * 0.35f, y + hexRadius * 0.15f)
        lineTo(x, y + hexRadius * 0.4f)
        lineTo(x - hexRadius * 0.35f, y + hexRadius * 0.15f)
        lineTo(x - hexRadius * 0.35f, y - hexRadius * 0.15f)
        close()
    }

    // Distorted fill and thick ink stroke
    drawPath(path, color = NeonGreen.copy(alpha = 0.2f), style = Fill)
    drawPath(path, color = inkBlack, style = Stroke(width = 3f))
    drawPath(path, color = NeonGreen.copy(alpha = 0.8f), style = Stroke(width = 1.5f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))

    // Glitching inner lines
    drawLine(
        color = NeonGreen.copy(alpha = 0.9f),
        start = Offset(x - hexRadius * 0.2f, y),
        end = Offset(x + hexRadius * 0.2f, y),
        strokeWidth = 2f
    )
    drawLine(
        color = NeonGreen.copy(alpha = 0.9f),
        start = Offset(x, y - hexRadius * 0.2f),
        end = Offset(x, y + hexRadius * 0.2f),
        strokeWidth = 2f
    )

    // Artifact nodes
    for (i in 0 until 4) {
        val angle = (i * 90f + 45f) * (kotlin.math.PI / 180f)
        val dist = hexRadius * 0.25f
        drawCircle(
            color = NeonOrange.copy(alpha = 0.9f),
            radius = 2.5f,
            center = Offset(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist)
        )
    }
}