package com.novaempire.core.engine

import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState
import kotlin.random.Random

object EventSystem {

    fun tick(state: GameState, rng: Random = Random.Default): GameState {
        var activeEvent = state.activeEvent
        var duration = state.eventDurationRemaining

        if (activeEvent != GalacticEvent.NONE) {
            val anomalyBonus = if (state.playerStates.values.any { it.techUnlocked.contains("tech_anomaly_analysis") }) 1 else 0
            duration -= (1 + anomalyBonus)
            if (duration <= 0) activeEvent = GalacticEvent.NONE
        } else if (rng.nextDouble() < 0.20) {
            val events = GalacticEvent.values().filter { it != GalacticEvent.NONE }
            activeEvent = events.random(rng)
            duration = rng.nextInt(2, 5)
        }

        return state.copy(activeEvent = activeEvent, eventDurationRemaining = duration)
    }
}
