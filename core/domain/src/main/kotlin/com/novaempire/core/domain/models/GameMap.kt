package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

import com.novaempire.core.hex.HexCoord

@Serializable
data class GameMap(
    val tiles: Map<HexCoord, HexTile> = emptyMap(),
    val radius: Int = 0
) {
    fun getTileAt(coord: HexCoord): HexTile? {
        return tiles[coord]
    }
}
