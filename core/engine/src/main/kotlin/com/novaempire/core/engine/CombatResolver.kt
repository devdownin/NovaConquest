package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.CombatEvent
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max
import kotlin.random.Random

object CombatResolver : CombatSystem {

    override fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): CombatOutcome =
        resolveCombatWithRng(state, attackerCoord, defenderCoord, Random.Default)

    fun resolveCombatWithRng(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord, rng: Random): CombatOutcome {
        val attacker = state.units[attackerCoord] ?: return CombatOutcome(state, null)
        val defender = state.units[defenderCoord] ?: return CombatOutcome(state, null)

        val attackVariance = 0.8f + rng.nextFloat() * 0.4f
        val damageToDefender = max(1, (AttackCalculator.effectiveBase(state, attackerCoord, defenderCoord) * attackVariance).toInt())
        val defenderRemainingHp = max(0, defender.currentHp - damageToDefender)

        var newUnits = state.units.toMutableMap()
        var updatedAttacker = attacker.copy(hasAttacked = true, hasMoved = true)

        if (defenderRemainingHp <= 0) {
            newUnits.remove(defenderCoord)
            newUnits[attackerCoord] = updatedAttacker
        } else {
            // The defender retaliates only if the attacker is within ITS weapon range —
            // striking from beyond the enemy's reach (a longer-ranged ship) is safe.
            val defenderCanRetaliate = attackerCoord.distanceTo(defenderCoord) <= defender.type.range
            if (defenderCanRetaliate) {
                // The counter is symmetric: the defender now fires from its tile at the attacker's,
                // so the same shared calculator (bonuses + mirrored terrain) applies.
                val counterVariance = 0.8f + rng.nextFloat() * 0.4f
                val damageToAttacker = max(1, (AttackCalculator.effectiveBase(state, defenderCoord, attackerCoord) * counterVariance).toInt())
                val attackerRemainingHp = max(0, attacker.currentHp - damageToAttacker)

                if (attackerRemainingHp <= 0) {
                    newUnits.remove(attackerCoord)
                } else {
                    updatedAttacker = updatedAttacker.copy(currentHp = attackerRemainingHp)
                    newUnits[attackerCoord] = updatedAttacker
                }
            } else {
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

        return CombatOutcome(state.copy(units = newUnits), combatEvent)
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
            // hasMoved too: firing on a planet ends the ship's turn exactly like firing on a unit
            // (resolveCombat sets both). Without it, a fleet could bombard and then withdraw.
            updatedUnits[attackerCoord] = unit.copy(hasAttacked = true, hasMoved = true, currentHp = attackerHpAfterSiege)
        }

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(systemLevel = newLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }

    override fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState {
        val unit = state.units[unitCoord] ?: return state
        val tile = state.map.tiles[planetCoord] ?: return state

        val updatedUnits = state.units.toMutableMap()
        // Same rule as siege/attack: taking a world consumes the ship's whole turn.
        updatedUnits[unitCoord] = unit.copy(hasAttacked = true, hasMoved = true)

        val capturingPlayer = state.playerStates[unit.faction]
        val startLevel = 1 + BonusRegistry.sum(BonusType.CAPTURE_START_LEVEL, capturingPlayer, state.activeEvent)

        val newTiles = state.map.tiles.toMutableMap()
        newTiles[planetCoord] = tile.copy(owner = unit.faction, systemLevel = startLevel)

        return state.copy(units = updatedUnits, map = state.map.copy(tiles = newTiles))
    }
}
