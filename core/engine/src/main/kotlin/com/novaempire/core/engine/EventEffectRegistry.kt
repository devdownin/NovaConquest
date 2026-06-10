package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusModifier
import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.GalacticEvent

object EventEffectRegistry {
    private val effects: Map<GalacticEvent, List<BonusModifier>> = mapOf(
        GalacticEvent.ION_STORM     to listOf(BonusModifier(BonusType.MOVEMENT_MODIFIER, -1)),
        GalacticEvent.SOLAR_FLARE   to listOf(BonusModifier(BonusType.VISION_RANGE_MULT_PCT, 50)),
        GalacticEvent.ANCIENT_SIGNAL to listOf(BonusModifier(BonusType.TECH_COST_PERCENT, 25)),
        GalacticEvent.ECONOMIC_BOOM  to listOf(BonusModifier(BonusType.INCOME_FLAT, 3)),
        GalacticEvent.PIRATE_RAID    to listOf(BonusModifier(BonusType.INCOME_FLAT, -5)),
        GalacticEvent.TECH_RUSH      to listOf(BonusModifier(BonusType.RESEARCH_SPEED, 1)),
    )

    fun bonusesFor(event: GalacticEvent): List<BonusModifier> = effects[event] ?: emptyList()
}
