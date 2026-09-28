package com.novaempire.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novaempire.app.R

// Local assets keep the display and body hierarchy stable offline and across Android versions.
val RajdhaniFamily = FontFamily(Font(R.font.rajdhani_bold, weight = FontWeight.Bold))
val InterFamily = FontFamily(
    Font(R.font.inter_regular, weight = FontWeight.Normal),
    Font(R.font.inter_semibold, weight = FontWeight.SemiBold)
)

/**
 * La typographie tire ses couleurs du [ColorScheme] actif au lieu de les figer sur la palette
 * DEFAULT. Un `color` posé dans un [TextStyle] gagne sur `LocalContentColor`, donc figer
 * `TextPrimary` ici rendait tout le texte de l'application sépia même en thème WINTER ou
 * HALLOWEEN — le thème changeait les fonds sans jamais changer l'encre.
 *
 * Le mapping conserve exactement l'apparence du thème DEFAULT :
 * `onBackground` y vaut `TextPrimary` et `onSurfaceVariant` vaut `TextSecondary`.
 *
 * @param highContrast remonte le texte secondaire à la couleur du texte principal. C'est le seul
 *   texte volontairement estompé de l'application, donc le seul levier de lisibilité à ce niveau.
 */
fun novaTypography(colorScheme: ColorScheme, highContrast: Boolean = false): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 1.sp,
        color = colorScheme.onBackground
    ),
    headlineLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.7.sp,
        color = colorScheme.onBackground
    ),
    headlineMedium = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.4.sp,
        color = colorScheme.onBackground
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = colorScheme.onBackground
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = if (highContrast) colorScheme.onBackground else colorScheme.onSurfaceVariant
    ),
    labelLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
        color = colorScheme.onBackground
    )
)
