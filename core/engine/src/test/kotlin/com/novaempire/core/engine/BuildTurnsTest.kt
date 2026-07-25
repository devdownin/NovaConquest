package com.novaempire.core.engine

import com.novaempire.core.domain.models.UnitType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build time must track hull class (P4). Six of the seven types used to take a flat 2 turns, so a
 * 3-credit Scout appeared as fast as a 25-credit Carrier and only credits gated a fleet.
 */
class BuildTurnsTest {

    @Test
    fun heavierHullsTakeLonger() {
        assertTrue(buildTurns(UnitType.SCOUT) < buildTurns(UnitType.CRUISER))
        assertTrue(buildTurns(UnitType.CRUISER) < buildTurns(UnitType.BATTLESHIP))
        assertTrue(buildTurns(UnitType.BATTLESHIP) < buildTurns(UnitType.CARRIER))
        assertTrue(buildTurns(UnitType.CARRIER) < buildTurns(UnitType.DREADNOUGHT))
    }

    @Test
    fun buildTimeIsNeverInstantOrNegative() {
        UnitType.values().forEach {
            assertTrue("$it must take at least one turn", buildTurns(it) >= 1)
        }
    }

    @Test
    fun costlierUnitsAreNeverFasterThanCheaperOnes() {
        // A weak monotonic relationship with cost: no hull should be both pricier and quicker.
        val byCost = UnitType.values().sortedBy { it.cost }
        byCost.zipWithNext().forEach { (cheaper, pricier) ->
            assertTrue(
                "${pricier.name} (${pricier.cost} C) builds faster than ${cheaper.name} (${cheaper.cost} C)",
                buildTurns(pricier) >= buildTurns(cheaper)
            )
        }
    }
}
