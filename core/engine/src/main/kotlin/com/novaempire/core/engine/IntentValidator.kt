package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord

object IntentValidator {

    fun unitAt(state: GameState, coord: HexCoord, label: String = "unit"): String? =
        if (state.units[coord] == null) "No $label at selected position." else null

    fun ownedByActive(unit: GameUnit, activeFaction: Faction): String? =
        if (unit.faction != activeFaction) "You cannot use this unit." else null

    fun notMoved(unit: GameUnit): String? =
        if (unit.hasMoved) "This unit has already moved this turn." else null

    fun notAttacked(unit: GameUnit): String? =
        if (unit.hasAttacked) "Unit already used its action." else null

    fun canAfford(player: PlayerState, cost: Int): String? =
        if (player.credits < cost) "Not enough credits." else null

    fun isPlanet(state: GameState, coord: HexCoord): String? {
        val tile = state.map.tiles[coord]
        return if (tile == null || tile.terrain != TerrainType.PLANET) "Target is not a planet." else null
    }
}
