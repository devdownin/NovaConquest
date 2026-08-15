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
        // Structures with no base movement (DEFENSE_PLATFORM) are immobile by design — the floor
        // below must never hand them a free hex. It exists only so a debuff (ION_STORM's -1)
        // cannot strand a unit that *can* normally move, such as the movement-1 DREADNOUGHT.
        if (unit.type.movement <= 0) return 0

        val player = state.playerStates[unit.faction]
        val moveMod = BonusRegistry.sum(
            BonusType.MOVEMENT_MODIFIER, player, state.activeEvent, state.eventTargetFaction
        )
        return (unit.type.movement + moveMod).coerceAtLeast(1)
    }

    /**
     * Movement points [unit] still has this turn — its budget less what it has already spent.
     *
     * This, not [effectiveMovement], is what the reachable-range highlight, the drag preview and
     * the reducer must all use, so that a fleet halfway through its allowance is offered exactly
     * the hexes it can still get to.
     */
    fun remainingMovement(state: GameState, unit: GameUnit): Int =
        (effectiveMovement(state, unit) - unit.movementUsed).coerceAtLeast(0)
}
