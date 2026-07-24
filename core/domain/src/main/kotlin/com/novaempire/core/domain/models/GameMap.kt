package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

import com.novaempire.core.hex.HexCoord

@Serializable
data class GameMap(
    val tiles: Map<HexCoord, HexTile> = emptyMap(),
    val radius: Int = 0,
    val archetype: MapArchetype = MapArchetype.STANDARD,
    val zodiacPlanets: Set<HexCoord> = emptySet(),
    /** Seed the map was generated from — lets a galaxy be replayed/shared. 0 for legacy saves. */
    val seed: Long = 0L
) {
    fun getTileAt(coord: HexCoord): HexTile? {
        return tiles[coord]
    }
}
