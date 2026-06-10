package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CombatResolverTest {

    private val fixedRng = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextFloat(): Float = 0.5f // Results in 1.0 variance: 0.8 + 0.5 * 0.4 = 1.0
    }

    @Test
    fun testCombatAttackerDestroysDefender() {
        // Attacker: Battleship (10 ATK + 1 DOMINION bonus) vs Defender: Scout (6 HP) -> Scout destroyed
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

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord, fixedRng)

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
        // DOMINION bonusAttack=0.10f → factionBonus = max(1, floor(4*0.10)) = 1 → damage = 5
        // Cruiser survives with 20 HP (25 - 5), counters for 6 damage. Fighter has 12 HP -> 6 HP left.
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

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord, fixedRng)

        // Defender should survive
        val resultingDefender = resultState.units[defenderCoord]
        assertEquals(25 - 5, resultingDefender?.currentHp) // 5 = 4 ATK + 1 DOMINION faction bonus

        // Attacker should survive but take counter damage
        val resultingAttacker = resultState.units[attackerCoord]
        assertEquals(12 - 6, resultingAttacker?.currentHp)
        assertTrue(resultingAttacker?.hasAttacked == true)

        // Check Event
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }

    @Test
    fun testCombatMutualDestructionOrDefenderCountersAndKillsAttacker() {
        // DOMINION bonusAttack=0.10f → factionBonus = max(1, floor(2*0.10)) = 1 → damage = 3
        // Scout attacks for 3 (Cruiser to 22). Cruiser counters for 6 (Scout HP 6 -> 0, dead).
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)

        val attacker = GameUnit(
            type = UnitType.SCOUT,
            faction = Faction.DOMINION,
            position = attackerCoord,
            currentHp = UnitType.SCOUT.maxHp // 6
        )
        val defender = GameUnit(
            type = UnitType.CRUISER,
            faction = Faction.TRADERS,
            position = defenderCoord,
            currentHp = UnitType.CRUISER.maxHp // 25
        )

        val state = GameState(units = mapOf(attackerCoord to attacker, defenderCoord to defender))

        val resultState = CombatResolver.resolveCombat(state, attackerCoord, defenderCoord, fixedRng)

        // Attacker should be dead
        assertNull(resultState.units[attackerCoord])

        // Defender survives
        assertEquals(25 - 3, resultState.units[defenderCoord]?.currentHp) // 3 = 2 ATK + 1 DOMINION faction bonus

        // Check Event
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }

    // ── Siege / Capture ──────────────────────────────────────────────────

    private fun stateWithPlanet(
        unitCoord: HexCoord,
        planetCoord: HexCoord,
        unitType: UnitType,
        planetLevel: Int,
        planetOwner: Faction? = null
    ): GameState {
        val unit = GameUnit(type = unitType, faction = Faction.DOMINION, position = unitCoord, currentHp = unitType.maxHp)
        val planet = HexTile(planetCoord, TerrainType.PLANET, systemLevel = planetLevel, owner = planetOwner)
        val map = com.novaempire.core.domain.models.GameMap(tiles = mapOf(unitCoord to HexTile(unitCoord, TerrainType.EMPTY), planetCoord to planet))
        return GameState(map = map, units = mapOf(unitCoord to unit))
    }

    @Test
    fun siegeReducesPlanetLevelByOne() {
        val state = stateWithPlanet(HexCoord(0,0,0), HexCoord(1,-1,0), UnitType.CRUISER, planetLevel = 3, planetOwner = Faction.TRADERS)
        val result = CombatResolver.siegePlanet(state, HexCoord(0,0,0), HexCoord(1,-1,0))
        assertEquals(2, result.map.tiles[HexCoord(1,-1,0)]?.systemLevel)
        assertEquals(true, result.units[HexCoord(0,0,0)]?.hasAttacked)
    }

    @Test
    fun battleshipSiegeDealsTwoDamage() {
        val state = stateWithPlanet(HexCoord(0,0,0), HexCoord(1,-1,0), UnitType.BATTLESHIP, planetLevel = 3, planetOwner = Faction.TRADERS)
        val result = CombatResolver.siegePlanet(state, HexCoord(0,0,0), HexCoord(1,-1,0))
        assertEquals(1, result.map.tiles[HexCoord(1,-1,0)]?.systemLevel)
    }

    @Test
    fun dreadnoughtSiegeDealsTwoDamage() {
        val state = stateWithPlanet(HexCoord(0,0,0), HexCoord(1,-1,0), UnitType.DREADNOUGHT, planetLevel = 3, planetOwner = Faction.TRADERS)
        val result = CombatResolver.siegePlanet(state, HexCoord(0,0,0), HexCoord(1,-1,0))
        assertEquals(1, result.map.tiles[HexCoord(1,-1,0)]?.systemLevel)
    }

    @Test
    fun siegeDoesNotReduceBelowZero() {
        val state = stateWithPlanet(HexCoord(0,0,0), HexCoord(1,-1,0), UnitType.BATTLESHIP, planetLevel = 1, planetOwner = Faction.TRADERS)
        val result = CombatResolver.siegePlanet(state, HexCoord(0,0,0), HexCoord(1,-1,0))
        assertEquals(0, result.map.tiles[HexCoord(1,-1,0)]?.systemLevel)
    }

    @Test
    fun captureSetsOwnerAndRebuildsAtLevel1() {
        val state = stateWithPlanet(HexCoord(0,0,0), HexCoord(1,-1,0), UnitType.SCOUT, planetLevel = 0)
        val result = CombatResolver.capturePlanet(state, HexCoord(0,0,0), HexCoord(1,-1,0))
        val planet = result.map.tiles[HexCoord(1,-1,0)]!!
        assertEquals(Faction.DOMINION, planet.owner)
        assertEquals(1, planet.systemLevel)
        assertEquals(true, result.units[HexCoord(0,0,0)]?.hasAttacked)
    }
}
