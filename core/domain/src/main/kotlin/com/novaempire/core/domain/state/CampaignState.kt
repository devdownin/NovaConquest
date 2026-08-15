package com.novaempire.core.domain.state

import kotlinx.serialization.Serializable

@Serializable
data class CampaignState(
    val activeMissionId: String? = null,
    val completedMissions: Set<String> = emptySet(),
    val gloryPoints: Int = 0
) {
    /** The part of this state that outlives a single game — see [CampaignProgress]. */
    fun toProgress(): CampaignProgress = CampaignProgress(completedMissions, gloryPoints)
}

/**
 * Campaign progress that must survive the game it was earned in.
 *
 * Deliberately **not** stored in the turn autosave. The autosave skips terminal states on purpose
 * (resuming a finished game drops the player straight onto the victory screen), which meant the
 * one moment progress is created — winning a mission — was the one moment nothing was written.
 * This shape is persisted separately, on its own file, whenever it changes.
 *
 * It is [CampaignState] minus `activeMissionId`: which mission is currently running belongs to a
 * game, not to a player's record, and restoring it at boot would resurrect a mission with no board.
 */
@Serializable
data class CampaignProgress(
    val completedMissions: Set<String> = emptySet(),
    val gloryPoints: Int = 0
)
