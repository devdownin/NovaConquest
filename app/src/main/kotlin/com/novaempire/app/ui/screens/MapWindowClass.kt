package com.novaempire.app.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Measured map width, including split screen and resizable tablet windows. */
enum class MapWindowClass { COMPACT, MEDIUM, EXPANDED }

fun mapWindowClass(width: Dp): MapWindowClass = when {
    width < 400.dp -> MapWindowClass.COMPACT
    width < 840.dp -> MapWindowClass.MEDIUM
    else -> MapWindowClass.EXPANDED
}
