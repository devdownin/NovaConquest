package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementCalculatorTest {

    private fun stateWith(faction: Faction, event: GalacticEvent = GalacticEvent.NONE) = GameState(
        activeFaction = faction,
        activeEvent = event,
        playerStates = mapOf(faction to PlayerState(faction, credits = 10))
    )

    private fun unit(faction: Faction, type: UnitType = UnitType.CRUISER) =
        GameUnit(type = type, faction = faction, position = HexCoord(0, 0, 0), currentHp = type.maxHp)

    @Test
    fun baseMovementIsUnitMovement() {
        // DOMINION has no movement bonus.
        val state = stateWith(Faction.DOMINION)
        assertEquals(UnitType.CRUISER.movement, MovementCalculator.effectiveMovement(state, unit(Faction.DOMINION)))
    }

    @Test
    fun factionMovementBonusApplies() {
        // NOMADS get +1 movement.
        val state = stateWith(Faction.NOMADS)
        assertEquals(UnitType.CRUISER.movement + 1, MovementCalculator.effectiveMovement(state, unit(Faction.NOMADS)))
    }

    @Test
    fun ionStormReducesMovement() {
        val calm = stateWith(Faction.DOMINION)
        val storm = stateWith(Faction.DOMINION, GalacticEvent.ION_STORM)
        val u = unit(Faction.DOMINION)
        assertEquals(
            MovementCalculator.effectiveMovement(calm, u) - 1,
            MovementCalculator.effectiveMovement(storm, u)
        )
    }

    @Test
    fun mobileUnitNeverDropsBelowOne() {
        // The floor protects units that can normally move: a DREADNOUGHT (movement 1) caught in an
        // ION_STORM (-1) must still be able to advance one hex rather than being stranded.
        val storm = stateWith(Faction.DOMINION, GalacticEvent.ION_STORM)
        assertEquals(1, MovementCalculator.effectiveMovement(storm, unit(Faction.DOMINION, UnitType.DREADNOUGHT)))
    }

    @Test
    fun immobileStructureStaysImmobile() {
        // Regression: DEFENSE_PLATFORM has movement 0, but the "never below 1" floor used to grant
        // it a free hex every turn — a static turret could walk across the map.
        assertEquals(0, MovementCalculator.effectiveMovement(
            stateWith(Faction.DOMINION), unit(Faction.DOMINION, UnitType.DEFENSE_PLATFORM)))
        // Even with a faction movement bonus (NOMADS +1) it must not become mobile.
        assertEquals(0, MovementCalculator.effectiveMovement(
            stateWith(Faction.NOMADS), unit(Faction.NOMADS, UnitType.DEFENSE_PLATFORM)))
    }
}
