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
    val cargoHp: List<Int> = emptyList(),
    /**
     * Movement points already spent this turn.
     *
     * A move used to be all-or-nothing: stepping one hex burned a SCOUT's entire allowance of
     * three. Tracking the spend lets a fleet move, look, and move again within its budget.
     * [hasMoved] now means "no movement left" — it flips only once the budget is exhausted, or
     * when combat ends the ship's turn outright.
     *
     * Defaulted, and appended rather than inserted, so existing saves still decode (there is no
     * schema migration layer) and positional constructor calls keep compiling.
     */
    val movementUsed: Int = 0
)
