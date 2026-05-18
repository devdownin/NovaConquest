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
    val hasAttacked: Boolean = false
)
