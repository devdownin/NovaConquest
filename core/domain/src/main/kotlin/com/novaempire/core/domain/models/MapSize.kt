package com.novaempire.core.domain.models

enum class MapSize(val radius: Int, val displayName: String) {
    SMALL(3, "SMALL"),
    MEDIUM(5, "MEDIUM"),
    LARGE(8, "LARGE"),
    GIGANTIC(12, "GIGANTIC")
}
