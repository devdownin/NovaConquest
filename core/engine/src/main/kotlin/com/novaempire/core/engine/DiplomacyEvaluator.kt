package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.state.GameState

/**
 * Decides whether a faction accepts a diplomatic proposal (D1).
 *
 * `handleChangeRelation` used to write the new relation onto **both** sides unconditionally, so any
 * player could declare themselves allied with everyone. Since the AI only ever engages factions it
 * is at `WAR` with, that made the proposer permanently untouchable — for free, in one click.
 *
 * The rule is now asymmetric, which matches how diplomacy actually works:
 *  - **war is unilateral** — you never need permission to attack someone;
 *  - **peace and alliance are proposals** the other side weighs up and may refuse.
 */
object DiplomacyEvaluator {

    /** Rough strength of a faction: liquid credits plus the health of its fleet. */
    fun power(state: GameState, faction: Faction): Int {
        val credits = state.playerStates[faction]?.credits ?: 0
        val fleet = state.units.values.filter { it.faction == faction }.sumOf { it.currentHp }
        return credits + fleet
    }

    /**
     * Would [target] agree to move to [proposed] with [proposer]?
     *
     * `WAR` is never subject to consent. An **alliance** is accepted when the proposer brings real
     * weight, or when the target already has a war on its hands and could use a friend. A
     * **ceasefire** is accepted unless the target is winning decisively and would rather press on.
     */
    fun wouldAccept(
        state: GameState,
        proposer: Faction,
        target: Faction,
        proposed: DiplomaticRelation
    ): Boolean {
        if (proposed == DiplomaticRelation.WAR) return true
        val targetState = state.playerStates[target] ?: return false

        val proposerPower = power(state, proposer)
        val targetPower = power(state, target)

        return when (proposed) {
            DiplomaticRelation.ALLIANCE -> {
                val targetFightsElsewhere = targetState.relations
                    .any { (faction, relation) -> faction != proposer && relation == DiplomaticRelation.WAR }
                targetFightsElsewhere || proposerPower >= (targetPower * 0.75).toInt()
            }
            DiplomaticRelation.NEUTRAL -> targetPower <= (proposerPower * 1.5).toInt()
            DiplomaticRelation.WAR -> true
        }
    }
}
