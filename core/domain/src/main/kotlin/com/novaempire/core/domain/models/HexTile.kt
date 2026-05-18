package com.novaempire.core.domain.models

import com.novaempire.core.hex.HexCoord

data class HexTile(
    val coord: HexCoord,
    val terrain: TerrainType = TerrainType.EMPTY,
    val systemLevel: Int = 0 // 1-5 if TerrainType is PLANET
)
