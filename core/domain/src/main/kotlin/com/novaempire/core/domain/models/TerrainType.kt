package com.novaempire.core.domain.models

enum class TerrainType(val blocksVision: Boolean = false, val isPassable: Boolean = true) {
    EMPTY,
    PLANET,
    NEBULA(blocksVision = true),
    ASTEROIDS(isPassable = false),
    BLACK_HOLE,
    WORMHOLE,
    PLASMA_CLOUD(blocksVision = true),
    ION_STORM(blocksVision = true),
    ANOMALY
}
