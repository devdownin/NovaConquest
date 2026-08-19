package com.novaempire.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.novaempire.app.ui.screens.getFactionColor
import com.novaempire.app.ui.theme.BrunEncre
import com.novaempire.app.ui.theme.MapPalette
import com.novaempire.app.ui.theme.NeonGreen
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.CombatHit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Dessin de la carte : la grille, les vaisseaux, les éclats d'explosion.
//
// Sorti de `TacticalMapScreen`, qui approchait les 2 800 lignes en portant à la fois la caméra,
// les gestes, le clavier, l'état d'animation, le HUD, les panneaux — et ces helpers. Ce sont des
// fonctions pures : elles reçoivent la palette en paramètre plutôt que de la lire dans un
// `CompositionLocal`, parce qu'un `DrawScope` n'en a pas. C'est exactement ce qui les rend
// déplaçables sans rien changer d'autre.

/** Nombre d'éclats projetés par une explosion, avant le multiplicateur du thème. */
private const val BASE_EXPLOSION_SHARDS = 10
/**
 * Unit-circle vertices of a pointy-top hexagon, resolved once at class-init.
 *
 * Every hexagon on the board is the same shape, yet [drawHexagonPath] was recomputing twelve
 * trigonometric functions per call — and it is called at least twice per tile per draw, which on
 * a GIGANTIC galaxy came to ~11 000 cos/sin per frame just to rebuild an identical outline.
 */
private val HEX_VERTEX_COS = FloatArray(6) { cos(PI / 180.0 * (60.0 * it - 30.0)).toFloat() }

private val HEX_VERTEX_SIN = FloatArray(6) { sin(PI / 180.0 * (60.0 * it - 30.0)).toFloat() }

fun DrawScope.drawHexagonPath(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color = Color.Unspecified,
    brush: Brush? = null,
    fill: Boolean = false,
    strokeWidth: Float = 2f
) {
    val path = Path()
    for (i in 0..5) {
        val px = centerX + radius * HEX_VERTEX_COS[i]
        val py = centerY + radius * HEX_VERTEX_SIN[i]
        if (i == 0) {
            path.moveTo(px, py)
        } else {
            path.lineTo(px, py)
        }
    }
    path.close()

    if (fill) {
        if (brush != null) drawPath(path = path, brush = brush)
        else drawPath(path = path, color = color, style = Fill)
    } else {
        if (brush != null) drawPath(path = path, brush = brush, style = Stroke(width = strokeWidth))
        else drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
    }
}

fun DrawScope.drawUnit(x: Float, y: Float, unit: GameUnit, hexRadius: Float, palette: MapPalette) {
    val factionColor = getFactionColor(unit.faction)
    val inkBlack = palette.ink
    // Proportional to the hex (25/60 of the historical radius) so sprites scale with the board.
    val size = hexRadius * 0.42f

    // Helper: apply Bilal layers to a path — black fill → tinted fill → black outline → color outline
    fun applyBilalLayers(path: Path) {
        drawPath(path, color = inkBlack, style = Fill)
        drawPath(path, color = factionColor.copy(alpha = 0.28f), style = Fill)
        drawPath(path, color = inkBlack, style = Stroke(width = 4.5f))
        drawPath(path, color = factionColor, style = Stroke(width = 1.5f))
    }

    when (unit.type) {
        UnitType.CRUISER -> {
            val path = Path().apply {
                moveTo(x + size, y)
                lineTo(x - size * 0.5f, y - size * 0.7f)
                lineTo(x - size * 0.8f, y - size * 0.4f)
                lineTo(x - size * 0.8f, y + size * 0.4f)
                lineTo(x - size * 0.5f, y + size * 0.7f)
                close()
            }
            applyBilalLayers(path)
            drawLine(factionColor.copy(alpha = 0.7f), Offset(x + size * 0.2f, y - size * 0.5f), Offset(x + size * 0.2f, y - size * 1.1f), strokeWidth = 1.5f)
            drawCircle(factionColor, radius = 2f, center = Offset(x + size * 0.2f, y - size * 1.1f))
        }
        UnitType.BATTLESHIP -> {
            val path = Path().apply {
                moveTo(x + size * 1.1f, y)
                lineTo(x + size * 0.4f, y - size * 0.4f)
                lineTo(x - size * 0.6f, y - size * 0.5f)
                lineTo(x - size * 0.9f, y - size * 0.2f)
                lineTo(x - size * 0.9f, y + size * 0.2f)
                lineTo(x - size * 0.6f, y + size * 0.5f)
                lineTo(x + size * 0.4f, y + size * 0.4f)
                close()
            }
            applyBilalLayers(path)
            // Two turrets for battleship
            drawCircle(factionColor.copy(alpha = 0.8f), radius = 2f, center = Offset(x + size * 0.1f, y - size * 0.2f))
            drawLine(factionColor.copy(alpha = 0.6f), Offset(x + size * 0.1f, y - size * 0.2f), Offset(x + size * 0.5f, y - size * 0.6f), strokeWidth = 1.5f)

            drawCircle(factionColor.copy(alpha = 0.8f), radius = 2f, center = Offset(x - size * 0.3f, y - size * 0.2f))
            drawLine(factionColor.copy(alpha = 0.6f), Offset(x - size * 0.3f, y - size * 0.2f), Offset(x + size * 0.1f, y - size * 0.6f), strokeWidth = 1.5f)
        }
        UnitType.FIGHTER -> {
            val path = Path().apply {
                moveTo(x + size * 0.7f, y)
                lineTo(x - size * 0.7f, y - size * 0.6f)
                lineTo(x - size * 0.4f, y)
                lineTo(x - size * 0.7f, y + size * 0.6f)
                close()
            }
            applyBilalLayers(path)
            drawRect(factionColor.copy(alpha = 0.7f), Offset(x - size * 0.8f, y - size * 0.4f), Size(7f, 3f))
            drawRect(factionColor.copy(alpha = 0.7f), Offset(x - size * 0.8f, y + size * 0.3f), Size(7f, 3f))
        }
        UnitType.SCOUT -> {
            val path = Path().apply {
                moveTo(x + size * 0.8f, y)
                lineTo(x, y - size * 0.4f)
                lineTo(x - size * 0.8f, y)
                lineTo(x, y + size * 0.4f)
                close()
            }
            applyBilalLayers(path)
            drawArc(
                color = factionColor.copy(alpha = 0.6f),
                startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(x - size * 0.3f, y - size * 0.3f),
                size = Size(size * 0.6f, size * 0.6f),
                style = Stroke(width = 1.5f)
            )
        }
        UnitType.CARRIER -> {
            val path = Path().apply {
                moveTo(x + size, y - size * 0.4f)
                lineTo(x + size, y + size * 0.4f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size, y - size * 0.6f)
                close()
            }
            applyBilalLayers(path)
            drawLine(factionColor.copy(alpha = 0.5f), Offset(x - size * 0.5f, y), Offset(x + size * 0.5f, y), strokeWidth = 1f)
        }
        UnitType.DREADNOUGHT -> {
            val path = Path().apply {
                moveTo(x + size * 1.2f, y)
                lineTo(x - size * 0.2f, y - size * 0.8f)
                lineTo(x - size, y - size * 0.6f)
                lineTo(x - size, y + size * 0.6f)
                lineTo(x - size * 0.2f, y + size * 0.8f)
                close()
            }
            applyBilalLayers(path)
            for (i in 0 until 3) {
                val tx = x - size * 0.5f + (i * size * 0.4f)
                drawCircle(factionColor.copy(alpha = 0.8f), radius = 2.5f, center = Offset(tx, y))
                drawLine(factionColor.copy(alpha = 0.6f), Offset(tx, y), Offset(tx, y - size * 0.28f), strokeWidth = 1.5f)
            }
        }
        UnitType.DEFENSE_PLATFORM -> {
            val path = Path().apply {
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (kotlin.math.PI / 180f)
                    val r = size * 0.7f
                    val px = x + cos(angle).toFloat() * r
                    val py = y + sin(angle).toFloat() * r
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            applyBilalLayers(path)
            drawCircle(factionColor.copy(alpha = 0.35f), radius = size * 0.28f, center = Offset(x, y), style = Stroke(width = 1.5f))
            for (i in 0 until 4) {
                val angle = (i * 90f + 22.5f) * (kotlin.math.PI / 180f)
                val rx = x + cos(angle).toFloat() * size * 0.9f
                val ry = y + sin(angle).toFloat() * size * 0.9f
                drawRect(inkBlack, Offset(rx - 4f, ry - 4f), Size(8f, 8f))
                drawRect(factionColor, Offset(rx - 3f, ry - 3f), Size(6f, 6f))
            }
        }
    }

    // Glint blanc diagonal — signature reflet Bilal
    drawLine(
        color = Color.White.copy(alpha = 0.28f),
        start = Offset(x - size * 0.32f, y - size * 0.52f),
        end   = Offset(x + size * 0.08f, y - size * 0.22f),
        strokeWidth = 1.5f
    )

    // Barre HP — métal mat, pas gris numérique
    val hpPercent = unit.currentHp.toFloat() / unit.type.maxHp
    val barWidth = hexRadius * 0.67f
    val barHeight = 3.5f
    val barTop = y + size + 9f
    drawRect(palette.healthBarBackground, Offset(x - barWidth / 2, barTop), Size(barWidth, barHeight))
    drawRect(factionColor.copy(alpha = 0.85f), Offset(x - barWidth / 2, barTop), Size(barWidth * hpPercent, barHeight))
    drawRect(inkBlack, Offset(x - barWidth / 2, barTop), Size(barWidth, barHeight), style = Stroke(width = 1f))
}

/**
 * Éclats d'encre projetés par une explosion — le système que `particleCountMultiplier` pilote.
 *
 * Le réglage existait dans les JSON de thème et dans le guide (« 2.0 pour des explosions
 * massives ») mais n'avait aucun système à commander : l'explosion n'était qu'un dégradé radial.
 *
 * Les angles et longueurs sont dérivés de l'indice de l'éclat, pas d'un tirage aléatoire : un
 * `Random` par passe de dessin ferait scintiller les éclats à chaque frame de l'animation.
 */
fun DrawScope.drawExplosionShards(
    centerX: Float,
    centerY: Float,
    radius: Float,
    progress: Float,
    multiplier: Float
) {
    val count = (BASE_EXPLOSION_SHARDS * multiplier).roundToInt()
    if (count <= 0) return

    val fade = (1f - progress).coerceIn(0f, 1f)
    if (fade <= 0f) return

    for (i in 0 until count) {
        // Angle d'or : répartition régulière quel que soit le nombre d'éclats, sans motif visible.
        val angle = i * 2.399963f
        val dirX = cos(angle)
        val dirY = sin(angle)
        // Longueur variable mais stable d'une frame à l'autre.
        val reach = 0.75f + ((i * 37) % 50) / 100f
        val inner = radius * 0.45f
        val outer = radius * reach

        drawLine(
            color = BrunEncre.copy(alpha = fade * 0.75f),
            start = Offset(centerX + dirX * inner, centerY + dirY * inner),
            end = Offset(centerX + dirX * outer, centerY + dirY * outer),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

/** Fraction de la course pendant laquelle le chiffre reste opaque, avant de s'effacer. */
private const val READOUT_OPAQUE_FRACTION = 0.6f

/**
 * Ce qu'un camp vient d'encaisser : le chiffre qui s'élève, et la barre de vie qui se vide.
 *
 * Jusqu'ici le joueur voyait un tir et une explosion, mais jamais *combien* : le résultat d'un
 * échange ne se lisait qu'en comparant deux fois les points de vie dans le panneau latéral. Le
 * chiffre le dit sur place, la barre dit ce qu'il en reste.
 *
 * [barProgress] vide la barre de `hpBefore` vers `hpAfter` ; [textProgress] fait monter le chiffre
 * et l'efface. Deux paramètres et non un, parce que le mouvement réduit veut la barre à son état
 * final tout de suite mais le chiffre immobile et lisible — voir plus bas.
 */
fun DrawScope.drawCombatReadout(
    x: Float,
    y: Float,
    hit: CombatHit,
    barProgress: Float,
    textProgress: Float,
    hexRadius: Float,
    palette: MapPalette
) {
    val t = barProgress.coerceIn(0f, 1f)

    // Barre de vie, sous le vaisseau.
    val barWidth = hexRadius * 1.05f
    val barHeight = hexRadius * 0.13f
    val barLeft = x - barWidth / 2f
    val barTop = y + hexRadius * 0.52f
    val ratioBefore = if (hit.maxHp > 0) hit.hpBefore.toFloat() / hit.maxHp else 0f
    val ratioAfter = if (hit.maxHp > 0) hit.hpAfter.toFloat() / hit.maxHp else 0f
    val ratio = ratioBefore + (ratioAfter - ratioBefore) * t

    drawRect(
        color = palette.ink.copy(alpha = 0.75f),
        topLeft = Offset(barLeft, barTop),
        size = Size(barWidth, barHeight)
    )
    if (ratio > 0f) {
        drawRect(
            // Vert tant que la coque tient, orange à mi-vie, rouge quand un tir de plus suffit.
            color = when {
                ratio > 0.6f -> NeonGreen
                ratio > 0.3f -> NeonOrange
                else -> NeonRed
            },
            topLeft = Offset(barLeft, barTop),
            size = Size(barWidth * ratio, barHeight)
        )
    }

    // Chiffre des dégâts, qui s'élève et s'efface.
    //
    // Piloté à part de la barre : en mouvement réduit l'appelant fige `textProgress` à 0, ce qui
    // donne un chiffre immobile et pleinement opaque. Couper les animations doit retirer le
    // mouvement, pas l'information — et un chiffre qui n'a pas le temps d'être lu n'en est plus une.
    val tt = textProgress.coerceIn(0f, 1f)
    val fade = if (tt < READOUT_OPAQUE_FRACTION) 1f
               else ((1f - tt) / (1f - READOUT_OPAQUE_FRACTION)).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val textY = y - hexRadius * (0.25f + 0.75f * tt)
    val alpha = (fade * 255f).toInt().coerceIn(0, 255)

    // Deux passes : un contour d'encre puis le remplissage. Un chiffre rouge posé sur une
    // explosion rouge serait illisible sans ce liseré.
    val outline = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = hexRadius * 0.5f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = hexRadius * 0.09f
        color = android.graphics.Color.argb(alpha, 19, 15, 10)
    }
    val fill = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = hexRadius * 0.5f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.argb(alpha, 255, 90, 74)
    }
    val label = "-${hit.damage}"
    drawContext.canvas.nativeCanvas.drawText(label, x, textY, outline)
    drawContext.canvas.nativeCanvas.drawText(label, x, textY, fill)
}
