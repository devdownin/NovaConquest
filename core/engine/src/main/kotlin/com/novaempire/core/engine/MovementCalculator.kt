package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.state.GameState

/**
 * Single source of truth for a unit's effective movement points.
 *
 * The value folds in every modifier that affects movement — faction bonus, tech, hero, and
 * the active galactic event (e.g. ION_STORM's -1) — via [BonusRegistry]. The reducer
 * ([handleMoveUnit]) and the UI (reachable-range highlight and drag-path preview) must all
 * call this so that what the player sees highlighted matches what the engine will accept.
 */
object MovementCalculator {

    fun effectiveMovement(state: GameState, unit: GameUnit): Int {
        val player = state.playerStates[unit.faction]
        val moveMod = BonusRegistry.sum(
            BonusType.MOVEMENT_MODIFIER, player, state.activeEvent, state.eventTargetFaction
        )
        return (unit.type.movement + moveMod).coerceAtLeast(1)
    }
}
