package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState
import kotlin.random.Random

object EventSystem {

    fun tick(state: GameState, rng: Random = Random.Default): GameState {
        // A mission's scripted beat comes first and overrides whatever is running. Waiting for a
        // free slot would let a random event swallow the set piece, and it would vanish without a
        // trace that anything was meant to happen.
        scriptedEventDue(state)?.let { scripted ->
            return state.copy(
                activeEvent = scripted.event,
                eventDurationRemaining = scripted.duration,
                eventTargetFaction = if (scripted.event.isTargeted) targetFactionFor(state, scripted.target) else null
            )
        }

        var activeEvent = state.activeEvent
        var duration = state.eventDurationRemaining
        var target = state.eventTargetFaction

        if (activeEvent != GalacticEvent.NONE) {
            // Anomaly Analysis accelerates decay only for the faction the event actually targets —
            // no more leak where any player's tech shortened the shared event for everyone. Global
            // (untargeted) events have no owner to analyse, so they decay at the normal rate.
            val ownerHasAnalysis = target != null &&
                state.playerStates[target]?.techUnlocked?.contains("tech_anomaly_analysis") == true
            duration -= if (ownerHasAnalysis) 2 else 1
            if (duration <= 0) {
                activeEvent = GalacticEvent.NONE
                target = null
            }
        } else if (rng.nextDouble() < 0.20) {
            val events = GalacticEvent.values().filter { it != GalacticEvent.NONE }
            activeEvent = events.random(rng)
            duration = rng.nextInt(2, 5)
            target = if (activeEvent.isTargeted) {
                val candidates = state.playerStates.keys.filter { it != Faction.ANCIENT_NPC }
                if (candidates.isNotEmpty()) candidates.random(rng) else null
            } else null
        }

        return state.copy(activeEvent = activeEvent, eventDurationRemaining = duration, eventTargetFaction = target)
    }

    /**
     * The scripted beat due this turn, if a campaign mission is running and declares one.
     *
     * `tick` is called once per full round, right after the turn counter increments, so an exact
     * match fires exactly once. Two beats on the same turn would be a data mistake; the first wins
     * rather than both being applied and only the last one surviving.
     */
    internal fun scriptedEventDue(state: GameState): com.novaempire.core.domain.models.ScriptedEvent? {
        val missionId = state.campaignState.activeMissionId ?: return null
        val mission = com.novaempire.core.domain.models.CampaignRegistry.MISSIONS.find { it.id == missionId }
            ?: return null
        return mission.scriptedEvents.firstOrNull { it.turn == state.turn && it.event != GalacticEvent.NONE }
    }

    private fun targetFactionFor(
        state: GameState,
        target: com.novaempire.core.domain.models.EventTarget
    ): Faction? {
        val missionId = state.campaignState.activeMissionId ?: return null
        val mission = com.novaempire.core.domain.models.CampaignRegistry.MISSIONS.find { it.id == missionId }
            ?: return null
        return when (target) {
            com.novaempire.core.domain.models.EventTarget.PLAYER -> mission.playerFaction
            com.novaempire.core.domain.models.EventTarget.ENEMY -> mission.enemyFaction
            // A targeted event with no named victim would apply to everyone, which is not what
            // "targeted" means anywhere else in the engine. Fall back to the player.
            com.novaempire.core.domain.models.EventTarget.GLOBAL -> mission.playerFaction
        }
    }
}
