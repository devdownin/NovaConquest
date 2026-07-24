package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Test

class AttackCalculatorTest {

    private val from = HexCoord(0, 0, 0)
    private val to = HexCoord(1, -1, 0)

    private fun state(attackerFaction: Faction, fromTerrain: TerrainType = TerrainType.EMPTY, toTerrain: TerrainType = TerrainType.EMPTY) =
        GameState(
            playerStates = mapOf(attackerFaction to PlayerState(attackerFaction)),
            units = mapOf(from to GameUnit(type = UnitType.CRUISER, faction = attackerFaction, position = from, currentHp = 25)),
            map = GameMap(tiles = mapOf(from to HexTile(from, fromTerrain), to to HexTile(to, toTerrain)))
        )

    @Test
    fun foldsFactionAttackPercent() {
        // DOMINION +10% on a 6-atk Cruiser → percent bonus max(1, floor(0.6)) = 1 → base 7.
        assertEquals(7f, AttackCalculator.effectiveBase(state(Faction.DOMINION), from, to), 0.001f)
    }

    @Test
    fun noBonusFactionUsesRawAttack() {
        // TRADERS have no attack bonus → base equals the raw Cruiser attack (6).
        assertEquals(6f, AttackCalculator.effectiveBase(state(Faction.TRADERS), from, to), 0.001f)
    }

    @Test
    fun blackHoleAttackerAndNebulaDefenderReduceBase() {
        val s = state(Faction.TRADERS, fromTerrain = TerrainType.BLACK_HOLE, toTerrain = TerrainType.NEBULA)
        // 6 * 0.75 (black hole) * 0.8 (nebula) = 3.6
        assertEquals(3.6f, AttackCalculator.effectiveBase(s, from, to), 0.001f)
    }

    @Test
    fun damageRangeBracketsBaseByTwentyPercent() {
        // TRADERS Cruiser base 6 → min floor(4.8)=4, max floor(7.2)=7.
        val (min, max) = AttackCalculator.damageRange(state(Faction.TRADERS), from, to)
        assertEquals(4, min)
        assertEquals(7, max)
    }
}
