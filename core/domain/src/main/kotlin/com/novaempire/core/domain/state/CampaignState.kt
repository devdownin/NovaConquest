package com.novaempire.core.domain.state

import kotlinx.serialization.Serializable

@Serializable
data class CampaignState(
    val activeMissionId: String? = null,
    val completedMissions: Set<String> = emptySet(),
    val gloryPoints: Int = 0
)
