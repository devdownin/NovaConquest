package com.novaempire.core.engine

import com.novaempire.core.domain.models.TechRegistry
import kotlin.math.max

object CostCalculator {

    fun techCost(
        techId: String,
        unlockedTechs: Set<String>,
        hasKael: Boolean = false,
        factionDiscount: Float = 0f
    ): Int {
        var cost = TechRegistry.baseCost(techId, unlockedTechs)
        if (hasKael) {
            cost = max(1, cost - max(1, (cost * 0.10).toInt()))
        }
        if (factionDiscount > 0f) {
            cost = (cost * (1f - factionDiscount)).toInt()
        }
        return cost
    }
}
