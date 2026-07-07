package com.novaempire.core.engine.campaign

import com.novaempire.core.domain.models.CampaignRegistry
import com.novaempire.core.domain.models.CampaignMission
import com.novaempire.core.domain.state.GameState

object CampaignManager {
    // Simple state-less operations to query or initialize campaign

    fun isMissionActive(state: GameState): Boolean {
        return state.campaignState?.activeMissionId != null
    }

    fun getActiveMission(state: GameState): CampaignMission? {
        val missionId = state.campaignState?.activeMissionId ?: return null
        return CampaignRegistry.MISSIONS.find { it.id == missionId }
    }
}
