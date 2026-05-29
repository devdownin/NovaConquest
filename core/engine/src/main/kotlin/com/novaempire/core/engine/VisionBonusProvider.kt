package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState

interface VisionBonusProvider {
    fun rangeBonus(faction: Faction): Int
    fun rangeMult(): Float
}

class GameStateVisionBonus(private val state: GameState) : VisionBonusProvider {
    override fun rangeBonus(faction: Faction): Int {
        val playerState = state.playerStates[faction]
        val deepScanners = if (playerState?.techUnlocked?.contains("tech_deep_scanners") == true) 1 else 0
        return deepScanners + faction.bonusVision
    }

    override fun rangeMult(): Float =
        if (state.activeEvent == GalacticEvent.SOLAR_FLARE) 0.5f else 1f
}
