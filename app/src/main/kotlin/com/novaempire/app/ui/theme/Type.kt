package com.novaempire.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Note: swap FontFamily.Default with Libre Baskerville Bold + Space Mono once TTFs are in res/font
val RajdhaniFamily = FontFamily.Monospace
val InterFamily    = FontFamily.Monospace

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
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        letterSpacing = 4.sp,          // titre grande affiche BD
        color = colorScheme.onBackground
    ),
    headlineLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 3.sp,
        color = colorScheme.onBackground
    ),
    headlineMedium = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
        color = colorScheme.onBackground
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
        color = colorScheme.onBackground
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = if (highContrast) colorScheme.onBackground else colorScheme.onSurfaceVariant
    ),
    labelLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,          // uppercase espacé style gazette
        color = colorScheme.onBackground
    )
)
