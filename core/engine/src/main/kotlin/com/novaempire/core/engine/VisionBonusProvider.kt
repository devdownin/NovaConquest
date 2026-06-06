package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

interface VisionBonusProvider {
    fun rangeBonus(faction: Faction): Int
    fun scoutRangeBonus(faction: Faction): Int
    fun rangeMult(): Float
}

class GameStateVisionBonus(private val state: GameState) : VisionBonusProvider {
    override fun rangeBonus(faction: Faction): Int {
        val playerState = state.playerStates[faction]
        val deepScanners = if (playerState?.techUnlocked?.contains(TechRegistry.DEEP_SCANNERS) == true) 1 else 0
        return deepScanners + faction.bonusVision
    }

    override fun scoutRangeBonus(faction: Faction): Int {
        val playerState = state.playerStates[faction]
        return if (playerState?.techUnlocked?.contains("tech_long_range_sensors") == true) 1 else 0
    }

    override fun rangeMult(): Float =
        if (state.activeEvent == GalacticEvent.SOLAR_FLARE) 0.5f else 1f
}
