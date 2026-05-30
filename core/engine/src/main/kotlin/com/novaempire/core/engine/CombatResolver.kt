package com.novaempire.core.engine

import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max

object CombatResolver {

    fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): GameState {
        val attacker = state.units[attackerCoord] ?: return state
        val defender = state.units[defenderCoord] ?: return state

        // Check for Hero Bonuses
        val attackerPlayer = state.playerStates[attacker.faction]

        val hasVance = attackerPlayer?.recruitedHeroes?.contains(com.novaempire.core.domain.models.HeroRegistry.VANCE) == true
        val heroBonus = if (hasVance) max(1, (attacker.type.attack * 0.15).toInt()) else 0
        val factionBonus = if (attacker.faction.bonusAttack > 0) max(1, (attacker.type.attack * attacker.faction.bonusAttack).toInt()) else 0
        val totalBonus = heroBonus + factionBonus

        // 1. Attacker deals damage
        val damageToDefender = attacker.type.attack + totalBonus
        val defenderRemainingHp = max(0, defender.currentHp - damageToDefender)

        var newUnits = state.units.toMutableMap()
        var updatedAttacker = attacker.copy(hasAttacked = true, hasMoved = true)

        if (defenderRemainingHp <= 0) {
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

    /** Damage a planet's system level. BATTLESHIP and DREADNOUGHT deal 2; others deal 1. */
    fun siegePlanet(state: GameState, attackerCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[attackerCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val siegeDamage = if (unit.type == UnitType.BATTLESHIP || unit.type == UnitType.DREADNOUGHT) 2 else 1
        val newLevel = max(0, tile.systemLevel - siegeDamage)

        val updatedUnits = state.units.toMutableMap()
        updatedUnits[attackerCoord] = unit.copy(hasAttacked = true)

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(systemLevel = newLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }

    /** Claim a planet at systemLevel 0 for the attacker's faction. */
    fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[unitCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val updatedUnits = state.units.toMutableMap()
        updatedUnits[unitCoord] = unit.copy(hasAttacked = true)

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(owner = unit.faction, systemLevel = 1)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }
}
