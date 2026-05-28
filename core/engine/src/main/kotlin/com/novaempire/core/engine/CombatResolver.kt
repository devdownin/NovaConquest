package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max

object CombatResolver {

    fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): GameState {
        val attacker = state.units[attackerCoord] ?: return state
        val defender = state.units[defenderCoord] ?: return state

        // Check for Hero Bonuses
        val attackerPlayer = state.playerStates[attacker.faction]
        val defenderPlayer = state.playerStates[defender.faction]

        val hasVance = attackerPlayer?.recruitedHeroes?.contains("hero_vance") == true
        val heroBonus = if (hasVance) max(1, (attacker.type.attack * 0.15).toInt()) else 0
        val factionBonus = if (attacker.faction.bonusAttack > 0) max(1, (attacker.type.attack * attacker.faction.bonusAttack).toInt()) else 0
        val totalBonus = heroBonus + factionBonus

        // 1. Attacker deals damage
        val damageToDefender = attacker.type.attack + totalBonus
        val defenderRemainingHp = max(0, defender.currentHp - damageToDefender)

        var newUnits = state.units.toMutableMap()
        var updatedAttacker = attacker.copy(hasAttacked = true, hasMoved = true) // Attacking consumes movement

        if (defenderRemainingHp <= 0) {
            // Defender destroyed, attacker might move into the hex if it's a melee attack?
            // In typical 4X, attacking consumes action but you stay in place unless it's an advance.
            // Let's assume ranged/stay in place for now.
            newUnits.remove(defenderCoord)
            newUnits[attackerCoord] = updatedAttacker
        } else {
            // 2. Defender counter-attacks if still alive
            val damageToAttacker = defender.type.attack
            val attackerRemainingHp = max(0, attacker.currentHp - damageToAttacker)

            if (attackerRemainingHp <= 0) {
                newUnits.remove(attackerCoord)
            } else {
                updatedAttacker = updatedAttacker.copy(currentHp = attackerRemainingHp)
                newUnits[attackerCoord] = updatedAttacker
            }

            // Update defender HP
            val updatedDefender = defender.copy(currentHp = defenderRemainingHp)
            newUnits[defenderCoord] = updatedDefender
        }

        val combatEvent = com.novaempire.core.domain.state.CombatEvent(
            attackerCoord = attackerCoord,
            defenderCoord = defenderCoord,
            targetDestroyed = defenderRemainingHp <= 0
        )

        return state.copy(units = newUnits, lastCombatEvent = combatEvent)
    }
}
