package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.state.PlayerState

object BonusRegistry {

    /**
     * Sum all BonusModifiers of [type] active for [playerState] and [activeEvent].
     * Returns 0 if [playerState] is null (no modifiers without a player context).
     * Event-only bonuses (e.g. VISION_RANGE_MULT_PCT) pass null for playerState.
     */
    fun sum(
        type: BonusType,
        playerState: PlayerState?,
        activeEvent: GalacticEvent = GalacticEvent.NONE
    ): Int {
        val eventBonus = EventEffectRegistry.bonusesFor(activeEvent)
            .filter { it.type == type }.sumOf { it.value }

        if (playerState == null) return eventBonus

        val heroBonus = HeroRegistry.bonusesFor(playerState.recruitedHeroes)
            .filter { it.type == type }.sumOf { it.value }
        val techBonus = TechRegistry.bonusesFor(playerState.techUnlocked)
            .filter { it.type == type }.sumOf { it.value }
        val factionBonus = playerState.faction.bonusModifiers()
            .filter { it.type == type }.sumOf { it.value }

        return heroBonus + techBonus + factionBonus + eventBonus
    }
}
