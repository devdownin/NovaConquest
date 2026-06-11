package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

import com.novaempire.core.hex.HexCoord

@Serializable
data class HexTile(
    val coord: HexCoord,
    val terrain: TerrainType = TerrainType.EMPTY,
    val systemLevel: Int = 0,
    val owner: Faction? = null,
    val specialty: PlanetSpecialty? = null
)
