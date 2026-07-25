package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.PlanetSpecialty
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import kotlin.math.max

/**
 * Single source of truth for per-turn credit income, so [TurnManager] (which actually grants it)
 * and the HUD preview agree.
 *
 * The HUD used to re-derive this by hand and drifted badly: a different base (10 vs 6), Elara's
 * bonus hard-coded instead of read from [BonusRegistry], galactic events applied without checking
 * `eventTargetFaction` (showing another faction's boom), and TRADE_POST / UPKEEP_MODIFIER ignored
 * entirely.
 */
object IncomeCalculator {

    /** Net credits [faction] gains at the start of its turn (income minus fleet upkeep). */
    fun perTurn(state: GameState, faction: Faction): Int {
        val playerState = state.playerStates[faction] ?: return 0

        val ownedPlanets = state.map.tiles.values.filter {
            it.terrain == TerrainType.PLANET && it.owner == faction
        }
        var income = 6 + ownedPlanets.sumOf { 5 + it.systemLevel * 2 }

        val incomePct = BonusRegistry.sum(BonusType.INCOME_PERCENT, playerState, state.activeEvent, state.eventTargetFaction)
        val incomeFlat = BonusRegistry.sum(BonusType.INCOME_FLAT, playerState, state.activeEvent, state.eventTargetFaction)
        income += (income * incomePct / 100.0).toInt() + incomeFlat
        income += ownedPlanets.count { it.specialty == PlanetSpecialty.TRADE_POST } * 8

        val upkeepMod = BonusRegistry.sum(BonusType.UPKEEP_MODIFIER, playerState, state.activeEvent)
        val upkeep = state.units.values.filter { it.faction == faction }
            .sumOf { max(0, it.type.upkeepCost + upkeepMod) }

        return income - upkeep
    }
}
