package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState
import kotlin.random.Random

object EventSystem {

    fun tick(state: GameState, rng: Random = Random.Default): GameState {
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
}
