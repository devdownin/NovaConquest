package com.novaempire.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Note: swap FontFamily.Default with Libre Baskerville Bold + Space Mono once TTFs are in res/font
val RajdhaniFamily = FontFamily.Default
val InterFamily    = FontFamily.Default

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        letterSpacing = 4.sp,          // titre grande affiche BD
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 3.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,          // uppercase espacé style gazette
        color = TextPrimary
    )
)
