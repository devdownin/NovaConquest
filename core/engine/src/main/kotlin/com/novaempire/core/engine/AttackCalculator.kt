package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import kotlin.math.max

/**
 * Single source of truth for how much damage a unit deals, so the combat resolver and the
 * combat-preview UI agree. Previously the preview re-derived the bonuses by hand (Vance, faction
 * %, plasma flat) and diverged from [CombatResolver] because each source was rounded separately.
 *
 * [effectiveBase] returns the pre-variance damage with attack bonuses and terrain multipliers
 * folded in; the resolver multiplies it by its random variance, the UI brackets it ±20 %.
 * The function is symmetric — a counter-attack is just `effectiveBase(state, defender, attacker)`.
 */
object AttackCalculator {

    fun effectiveBase(state: GameState, fromCoord: HexCoord, toCoord: HexCoord): Float {
        val attacker = state.units[fromCoord] ?: return 0f
        val player = state.playerStates[attacker.faction]
        val attackPct = BonusRegistry.sum(BonusType.ATTACK_PERCENT, player, state.activeEvent)
        val attackFlat = BonusRegistry.sum(BonusType.ATTACK_FLAT, player, state.activeEvent)
        val percentBonus = if (attackPct > 0) max(1, (attacker.type.attack * attackPct / 100.0).toInt()) else 0
        val totalBonus = percentBonus + attackFlat

        val fromTerrain = state.map.tiles[fromCoord]?.terrain
        val toTerrain = state.map.tiles[toCoord]?.terrain
        val attackerMult = if (fromTerrain == TerrainType.BLACK_HOLE) 0.75f else 1.0f
        val defenderMult = if (toTerrain == TerrainType.NEBULA) 0.8f else 1.0f

        return (attacker.type.attack + totalBonus) * attackerMult * defenderMult
    }

    /** Min/max damage after ±20 % variance — for the combat-preview UI. */
    fun damageRange(state: GameState, fromCoord: HexCoord, toCoord: HexCoord): Pair<Int, Int> {
        val base = effectiveBase(state, fromCoord, toCoord)
        return max(1, (base * 0.8f).toInt()) to max(1, (base * 1.2f).toInt())
    }
}
