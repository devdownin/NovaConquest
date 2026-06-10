package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.state.PlayerState
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
        val base = CostCalculator.techCost("tech_hull_plating", emptySet())
        val withKael = CostCalculator.techCost(
            "tech_hull_plating", emptySet(),
            PlayerState(Faction.DOMINION, recruitedHeroes = setOf(HeroRegistry.KAEL))
        )
        assertTrue("Kael should reduce cost", withKael < base)
    }

    @Test
    fun factionDiscountApplied() {
        val base = CostCalculator.techCost("tech_hull_plating", emptySet())
        val discounted = CostCalculator.techCost(
            "tech_hull_plating", emptySet(),
            PlayerState(Faction.SYNTH) // SYNTH has 15% tech discount
        )
        assertTrue("SYNTH faction discount should reduce cost", discounted < base)
    }

    @Test
    fun unknownTechReturns999() {
        assertEquals(999, CostCalculator.techCost("nonexistent_tech", emptySet()))
    }

    @Test
    fun costNeverBelowOne() {
        val result = CostCalculator.techCost(
            "tech_hull_plating", emptySet(),
            PlayerState(Faction.SYNTH, recruitedHeroes = setOf(HeroRegistry.KAEL))
        )
        assertTrue(result >= 1)
    }
}
