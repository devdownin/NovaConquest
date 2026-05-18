package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

import com.novaempire.core.hex.HexCoord

@Serializable
data class HexTile(
    val coord: HexCoord,
    val terrain: TerrainType = TerrainType.EMPTY,
    val systemLevel: Int = 0, // 1-5 if TerrainType is PLANET
    val owner: Faction? = null
)
