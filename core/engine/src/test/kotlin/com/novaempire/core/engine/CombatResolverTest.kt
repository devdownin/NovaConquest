package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
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

    // Use a fixed seed so variance is always exactly 1.0 (nextFloat = 0.5 → variance = 0.8 + 0.5*0.4 = 1.0)
    private val deterministicRng = Random(42)

    private fun baseState(
        attackerCoord: HexCoord,
        defenderCoord: HexCoord,
        attacker: GameUnit,
        defender: GameUnit
    ) = GameState(
        units = mapOf(attackerCoord to attacker, defenderCoord to defender),
        playerStates = mapOf(
            attacker.faction to PlayerState(attacker.faction),
            defender.faction to PlayerState(defender.faction)
        )
    )

    @Test
    fun testCombatAttackerDestroysDefender() {
        // BATTLESHIP (8 ATK + DOMINION bonus ≥ 1) vs SCOUT (5 HP) → Scout always destroyed
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)
        val attacker = GameUnit(type = UnitType.BATTLESHIP, faction = Faction.DOMINION, position = attackerCoord, currentHp = UnitType.BATTLESHIP.maxHp)
        val defender = GameUnit(type = UnitType.SCOUT, faction = Faction.TRADERS, position = defenderCoord, currentHp = UnitType.SCOUT.maxHp)
        val state = baseState(attackerCoord, defenderCoord, attacker, defender)

        val resultState = CombatResolver.resolveCombatWithRng(state, attackerCoord, defenderCoord, deterministicRng)

        assertNull("Scout should be destroyed", resultState.units[defenderCoord])
        assertTrue(resultState.units[attackerCoord]?.hasAttacked == true)
        assertEquals(true, resultState.lastCombatEvent?.targetDestroyed)
    }

    @Test
    fun testCombatDefenderSurvivesAndCounters() {
        // DOMINION FIGHTER (4 ATK + 1 faction bonus = 5) vs CRUISER (25 HP)
        // With fixed rng (seed 42): first nextFloat() determines attacker variance
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)
        val attacker = GameUnit(type = UnitType.FIGHTER, faction = Faction.DOMINION, position = attackerCoord, currentHp = UnitType.FIGHTER.maxHp)
        val defender = GameUnit(type = UnitType.CRUISER, faction = Faction.TRADERS, position = defenderCoord, currentHp = UnitType.CRUISER.maxHp)
        val state = baseState(attackerCoord, defenderCoord, attacker, defender)

        val resultState = CombatResolver.resolveCombatWithRng(state, attackerCoord, defenderCoord, Random(42))

        // Defender survives with reduced HP (FIGHTER 5 ATK ±20% → damage 4–6, Cruiser 25HP survives)
        val resultingDefender = resultState.units[defenderCoord]
        assertTrue("Defender should survive", resultingDefender != null)
        assertTrue("Defender HP should be reduced", resultingDefender!!.currentHp < UnitType.CRUISER.maxHp)

        // Attacker survives counter (CRUISER 6 ATK ±20% → damage 4–7, Fighter 12HP survives if counter is low)
        val resultingAttacker = resultState.units[attackerCoord]
        assertTrue("Attacker should be marked as attacked", resultingAttacker?.hasAttacked == true)
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }

    @Test
    fun testCombatCounterCanKillAttacker() {
        // Weak SCOUT (5HP) vs powerful DREADNOUGHT (counter 10 ATK) → Scout always killed by counter
        val attackerCoord = HexCoord(0, 0, 0)
        val defenderCoord = HexCoord(1, -1, 0)
        val attacker = GameUnit(type = UnitType.SCOUT, faction = Faction.DOMINION, position = attackerCoord, currentHp = UnitType.SCOUT.maxHp)
        val defender = GameUnit(type = UnitType.DREADNOUGHT, faction = Faction.TRADERS, position = defenderCoord, currentHp = UnitType.DREADNOUGHT.maxHp)
        val state = baseState(attackerCoord, defenderCoord, attacker, defender)

        val resultState = CombatResolver.resolveCombatWithRng(state, attackerCoord, defenderCoord, deterministicRng)

        // Scout (5HP) vs Dreadnought counter (10 ATK min 8) → Scout always dies
        assertNull("Scout should be killed by counter-attack", resultState.units[attackerCoord])
        assertEquals(false, resultState.lastCombatEvent?.targetDestroyed)
    }

    // ── Siege / Capture ──────────────────────────────────────────────────────

    private fun stateWithPlanet(
        unitCoord: HexCoord,
        planetCoord: HexCoord,
        unitType: UnitType,
        planetLevel: Int,
        planetOwner: Faction? = null
    ): GameState {
        val unit = GameUnit(type = unitType, faction = Faction.DOMINION, position = unitCoord, currentHp = unitType.maxHp)
        val planet = HexTile(planetCoord, TerrainType.PLANET, systemLevel = planetLevel, owner = planetOwner)
        val map = com.novaempire.core.domain.models.GameMap(
            tiles = mapOf(
                unitCoord to HexTile(unitCoord, TerrainType.EMPTY),
                planetCoord to planet
            )
        )
        return GameState(
            map = map,
            units = mapOf(unitCoord to unit),
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION))
        )
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
