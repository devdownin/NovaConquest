package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.state.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun baseCostNoModifiers() {
        val cost = CostCalculator.techCost("tech_hull_plating", emptySet())
        assertEquals(8, cost)
    }

    @Test
    fun costIncreasesWithUnlockedBranchTechs() {
        val unlocked = setOf("tech_hull_plating")
        val cost = CostCalculator.techCost("tech_plasma_weapons", unlocked)
        assertEquals(8 + 6, cost) // base 8 + 6 * 1 unlocked in branch
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
    fun targetedEventDiscountAppliesOnlyToTargetFaction() {
        // ANCIENT_SIGNAL (-25% tech cost) is a targeted event: only its target faction benefits.
        // The tech-tree UI relies on this — it now passes the event + target into techCost.
        val base = CostCalculator.techCost("tech_hull_plating", emptySet(), PlayerState(Faction.DOMINION))
        val targeted = CostCalculator.techCost(
            "tech_hull_plating", emptySet(), PlayerState(Faction.DOMINION),
            GalacticEvent.ANCIENT_SIGNAL, Faction.DOMINION
        )
        val nonTarget = CostCalculator.techCost(
            "tech_hull_plating", emptySet(), PlayerState(Faction.DOMINION),
            GalacticEvent.ANCIENT_SIGNAL, Faction.TRADERS
        )
        assertTrue("ANCIENT_SIGNAL should discount the targeted faction", targeted < base)
        assertEquals("A non-targeted faction pays full price", base, nonTarget)
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
