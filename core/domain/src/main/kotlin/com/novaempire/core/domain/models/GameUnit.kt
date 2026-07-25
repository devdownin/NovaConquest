package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

import com.novaempire.core.hex.HexCoord
import java.util.UUID

@Serializable
data class GameUnit(
    val id: String = UUID.randomUUID().toString(),
    val type: UnitType,
    val faction: Faction,
    val position: HexCoord,
    val currentHp: Int,
    val hasMoved: Boolean = false,
    val hasAttacked: Boolean = false,
    val cargo: List<UnitType> = emptyList(),
    /**
     * Current HP of each embarked unit, parallel to [cargo]. Kept as a separate defaulted list
     * rather than folding it into [cargo] so existing saves (which store cargo as plain type names)
     * still decode; a legacy entry with no recorded HP simply deploys at full health.
     */
    val cargoHp: List<Int> = emptyList()
)
