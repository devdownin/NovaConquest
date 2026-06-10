package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState

interface VisionBonusProvider {
    fun rangeBonus(faction: Faction): Int
    fun scoutRangeBonus(faction: Faction): Int
    fun rangeMult(): Float
}

class GameStateVisionBonus(private val state: GameState) : VisionBonusProvider {
    override fun rangeBonus(faction: Faction): Int =
        BonusRegistry.sum(BonusType.VISION_RANGE, state.playerStates[faction], state.activeEvent)

    override fun scoutRangeBonus(faction: Faction): Int =
        BonusRegistry.sum(BonusType.SCOUT_VISION_RANGE, state.playerStates[faction], state.activeEvent)

    override fun rangeMult(): Float {
        val multPct = BonusRegistry.sum(BonusType.VISION_RANGE_MULT_PCT, null, state.activeEvent)
        return if (multPct > 0) multPct / 100f else 1f
    }
}
