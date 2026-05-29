package com.novaempire.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun baseCostNoModifiers() {
        val cost = CostCalculator.techCost("tech_hull_plating", emptySet())
        assertEquals(4, cost)
    }

    @Test
    fun costIncreasesWithUnlockedBranchTechs() {
        val unlocked = setOf("tech_hull_plating")
        val cost = CostCalculator.techCost("tech_plasma_weapons", unlocked)
        assertEquals(4 + 4, cost) // base 4 + 4 * 1 unlocked in branch
    }

    @Test
    fun kaelApplies10PercentDiscount() {
        val base = CostCalculator.techCost("tech_hull_plating", emptySet(), hasKael = false)
        val withKael = CostCalculator.techCost("tech_hull_plating", emptySet(), hasKael = true)
        assertTrue(withKael < base)
    }

    @Test
    fun factionDiscountApplied() {
        val base = CostCalculator.techCost("tech_hull_plating", emptySet())
        val discounted = CostCalculator.techCost("tech_hull_plating", emptySet(), factionDiscount = 0.5f)
        assertEquals(base / 2, discounted)
    }

    @Test
    fun unknownTechReturns999() {
        assertEquals(999, CostCalculator.techCost("nonexistent_tech", emptySet()))
    }

    @Test
    fun costNeverBelowOne() {
        val result = CostCalculator.techCost("tech_hull_plating", emptySet(), hasKael = true, factionDiscount = 0.99f)
        assertTrue(result >= 1)
    }
}
