package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.state.PlayerState
import kotlin.math.max

object CostCalculator {

    fun techCost(
        techId: String,
        unlockedTechs: Set<String>,
        playerState: PlayerState? = null,
        activeEvent: GalacticEvent = GalacticEvent.NONE
    ): Int {
        var cost = TechRegistry.baseCost(techId, unlockedTechs)
        val discountPct = BonusRegistry.sum(BonusType.TECH_COST_PERCENT, playerState, activeEvent)
        if (discountPct > 0) {
            cost = max(1, (cost * (1f - discountPct / 100f)).toInt())
        }
        return max(1, cost)
    }
}
