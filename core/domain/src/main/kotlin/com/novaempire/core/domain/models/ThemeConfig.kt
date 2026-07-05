package com.novaempire.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeType {
    DEFAULT,
    HALLOWEEN,
    WINTER
}

@Serializable
data class ThemeConfig(
    val currentTheme: ThemeType = ThemeType.DEFAULT
)
