package com.novaempire.core.engine

import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max
import kotlin.random.Random

object CombatResolver {

    fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord, rng: Random = Random.Default): GameState {
        val attacker = state.units[attackerCoord] ?: return state
        val defender = state.units[defenderCoord] ?: return state

        // Check for Hero Bonuses
        val attackerPlayer = state.playerStates[attacker.faction]

        val hasVance = attackerPlayer?.recruitedHeroes?.contains(com.novaempire.core.domain.models.HeroRegistry.VANCE) == true
        val heroBonus = if (hasVance) max(1, (attacker.type.attack * 0.15).toInt()) else 0
        val factionBonus = if (attacker.faction.bonusAttack > 0) max(1, (attacker.type.attack * attacker.faction.bonusAttack).toInt()) else 0
        val plasmaBonus = if (attackerPlayer?.techUnlocked?.contains("tech_plasma_weapons") == true) 2 else 0
        val totalBonus = heroBonus + factionBonus + plasmaBonus

        // Terrain modifiers: attacker on BLACK_HOLE → -25% attack; defender in NEBULA → -20% damage taken
        val attackerTerrain = state.map.tiles[attackerCoord]?.terrain
        val defenderTerrain = state.map.tiles[defenderCoord]?.terrain
        val terrainAttackMult = if (attackerTerrain == TerrainType.BLACK_HOLE) 0.75f else 1.0f
        val terrainDefenseMult = if (defenderTerrain == TerrainType.NEBULA) 0.8f else 1.0f

        // 1. Attacker deals damage (±20% variance, terrain modifiers)
        val attackVariance = 0.8f + rng.nextFloat() * 0.4f
        val damageToDefender = max(1, ((attacker.type.attack + totalBonus) * terrainAttackMult * attackVariance * terrainDefenseMult).toInt())
        val defenderRemainingHp = max(0, defender.currentHp - damageToDefender)

        var newUnits = state.units.toMutableMap()
        var updatedAttacker = attacker.copy(hasAttacked = true, hasMoved = true)

        if (defenderRemainingHp <= 0) {
            newUnits.remove(defenderCoord)
            newUnits[attackerCoord] = updatedAttacker
        } else {
            // 2. Defender counter-attacks if still alive (±20% variance)
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

        val combatEvent = com.novaempire.core.domain.state.CombatEvent(
            attackerCoord = attackerCoord,
            defenderCoord = defenderCoord,
            targetDestroyed = defenderRemainingHp <= 0
        )

        return state.copy(units = newUnits, lastCombatEvent = combatEvent)
    }

    /** Damage a planet's system level. BATTLESHIP and DREADNOUGHT deal 2; others deal 1.
     *  Orbital defenses retaliate: level * 2 damage to the attacker. */
    fun siegePlanet(state: GameState, attackerCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[attackerCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val hasSiegeProtocols = state.playerStates[unit.faction]?.techUnlocked?.contains("tech_siege_protocols") == true
        val siegeDamage = (if (unit.type == UnitType.BATTLESHIP || unit.type == UnitType.DREADNOUGHT) 2 else 1) + (if (hasSiegeProtocols) 1 else 0)
        val newLevel = max(0, tile.systemLevel - siegeDamage)

        val updatedUnits = state.units.toMutableMap()

        // Orbital defense retaliation: level * 2 damage back to attacker
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

    /** Claim a planet at systemLevel 0 for the attacker's faction. */
    fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[unitCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val updatedUnits = state.units.toMutableMap()
        updatedUnits[unitCoord] = unit.copy(hasAttacked = true)

        val hasTerraforming = state.playerStates[unit.faction]?.techUnlocked?.contains("tech_terraforming") == true
        val startLevel = if (hasTerraforming) 2 else 1

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(owner = unit.faction, systemLevel = startLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }
}
