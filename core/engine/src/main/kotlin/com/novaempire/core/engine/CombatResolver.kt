package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.CombatEvent
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max
import kotlin.random.Random

object CombatResolver : CombatSystem {

    override fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): GameState =
        resolveCombatWithRng(state, attackerCoord, defenderCoord, Random.Default)

    fun resolveCombatWithRng(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord, rng: Random): GameState {
        val attacker = state.units[attackerCoord] ?: return state
        val defender = state.units[defenderCoord] ?: return state

        val attackerPlayer = state.playerStates[attacker.faction]
        val attackPct = BonusRegistry.sum(BonusType.ATTACK_PERCENT, attackerPlayer, state.activeEvent)
        val attackFlat = BonusRegistry.sum(BonusType.ATTACK_FLAT, attackerPlayer, state.activeEvent)
        val percentBonus = if (attackPct > 0) max(1, (attacker.type.attack * attackPct / 100.0).toInt()) else 0
        val totalBonus = percentBonus + attackFlat

        val attackerTerrain = state.map.tiles[attackerCoord]?.terrain
        val defenderTerrain = state.map.tiles[defenderCoord]?.terrain
        val terrainAttackMult = if (attackerTerrain == TerrainType.BLACK_HOLE) 0.75f else 1.0f
        val terrainDefenseMult = if (defenderTerrain == TerrainType.NEBULA) 0.8f else 1.0f

        val attackVariance = 0.8f + rng.nextFloat() * 0.4f
        val damageToDefender = max(1, ((attacker.type.attack + totalBonus) * terrainAttackMult * attackVariance * terrainDefenseMult).toInt())
        val defenderRemainingHp = max(0, defender.currentHp - damageToDefender)

        var newUnits = state.units.toMutableMap()
        var updatedAttacker = attacker.copy(hasAttacked = true, hasMoved = true)

        if (defenderRemainingHp <= 0) {
            newUnits.remove(defenderCoord)
            newUnits[attackerCoord] = updatedAttacker
        } else {
            val counterVariance = 0.8f + rng.nextFloat() * 0.4f
            val damageToAttacker = max(1, (defender.type.attack * counterVariance).toInt())
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

        val combatEvent = CombatEvent(
            attackerCoord = attackerCoord,
            defenderCoord = defenderCoord,
            targetDestroyed = defenderRemainingHp <= 0
        )

        return state.copy(units = newUnits, lastCombatEvent = combatEvent)
    }

    override fun siegePlanet(state: GameState, attackerCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[attackerCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val attackerPlayer = state.playerStates[unit.faction]
        val siegeBonus = BonusRegistry.sum(BonusType.SIEGE_DAMAGE, attackerPlayer, state.activeEvent)
        val siegeDamage = (if (unit.type == UnitType.BATTLESHIP || unit.type == UnitType.DREADNOUGHT) 2 else 1) + siegeBonus
        val newLevel = max(0, tile.systemLevel - siegeDamage)

        val updatedUnits = state.units.toMutableMap()

        val defenseRetaliation = tile.systemLevel * 2
        val attackerHpAfterSiege = max(0, unit.currentHp - defenseRetaliation)
        if (attackerHpAfterSiege <= 0) {
            updatedUnits.remove(attackerCoord)
        } else {
            updatedUnits[attackerCoord] = unit.copy(hasAttacked = true, currentHp = attackerHpAfterSiege)
        }

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(systemLevel = newLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }

    override fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[unitCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val updatedUnits = state.units.toMutableMap()
        updatedUnits[unitCoord] = unit.copy(hasAttacked = true)

        val capturingPlayer = state.playerStates[unit.faction]
        val startLevel = 1 + BonusRegistry.sum(BonusType.CAPTURE_START_LEVEL, capturingPlayer, state.activeEvent)

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(owner = unit.faction, systemLevel = startLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }
}
