package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatResolverTest {

    @Test
    fun testCombatAttackerDestroysDefender() {
        // Attacker: Battleship (8 ATK) vs Defender: Scout (5 HP) -> Scout destroyed
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)

        val attacker = GameUnit(
            type = UnitType.BATTLESHIP,
            faction = Faction.DOMINION,
            position = attackerCoord,
            currentHp = UnitType.BATTLESHIP.maxHp
        )
        val defender = GameUnit(
            type = UnitType.SCOUT,
            faction = Faction.TRADERS,
            position = defenderCoord,
            currentHp = UnitType.SCOUT.maxHp
        )

        val state = GameState(units = mapOf(attackerCoord to attacker, defenderCoord to defender))

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord)

        // Defender should be removed
        assertNull(resultState.units[defenderCoord])

        // Attacker should still be alive and at max HP (target destroyed before counter)
        assertEquals(UnitType.BATTLESHIP.maxHp, resultState.units[attackerCoord]?.currentHp)
        assertTrue(resultState.units[attackerCoord]?.hasAttacked == true)

        // Check Event
        assertEquals(true, resultState.lastCombatEvent?.targetDestroyed)
    }

    @Test
    fun testCombatDefenderSurvivesAndCounters() {
        // Attacker: Fighter (4 ATK) vs Defender: Cruiser (25 HP, 6 ATK)
        // Cruiser survives with 21 HP, counters for 6 damage. Fighter has 12 HP -> 6 HP left.
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)

        val attacker = GameUnit(
            type = UnitType.FIGHTER,
            faction = Faction.DOMINION,
            position = attackerCoord,
            currentHp = UnitType.FIGHTER.maxHp // 12
        )
        val defender = GameUnit(
            type = UnitType.CRUISER,
            faction = Faction.TRADERS,
            position = defenderCoord,
            currentHp = UnitType.CRUISER.maxHp // 25
        )

        val state = GameState(units = mapOf(attackerCoord to attacker, defenderCoord to defender))

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord)

        // Defender should survive
        val resultingDefender = resultState.units[defenderCoord]
        assertEquals(25 - 4, resultingDefender?.currentHp)

        // Attacker should survive but take counter damage
        val resultingAttacker = resultState.units[attackerCoord]
        assertEquals(12 - 6, resultingAttacker?.currentHp)
        assertTrue(resultingAttacker?.hasAttacked == true)

        // Check Event
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }

    @Test
    fun testCombatMutualDestructionOrDefenderCountersAndKillsAttacker() {
        // Attacker: Scout (5 HP, 2 ATK) vs Defender: Cruiser (25 HP, 6 ATK)
        // Scout attacks for 2 (Cruiser to 23). Cruiser counters for 6 (Scout HP 5 -> -1, dead).
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)

        val attacker = GameUnit(
            type = UnitType.SCOUT,
            faction = Faction.DOMINION,
            position = attackerCoord,
            currentHp = UnitType.SCOUT.maxHp // 5
        )
        val defender = GameUnit(
            type = UnitType.CRUISER,
            faction = Faction.TRADERS,
            position = defenderCoord,
            currentHp = UnitType.CRUISER.maxHp // 25
        )

        val state = GameState(units = mapOf(attackerCoord to attacker, defenderCoord to defender))

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord)

        // Attacker should be dead
        assertNull(resultState.units[attackerCoord])

        // Defender survives
        assertEquals(25 - 2, resultState.units[defenderCoord]?.currentHp)

        // Check Event
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }
}
