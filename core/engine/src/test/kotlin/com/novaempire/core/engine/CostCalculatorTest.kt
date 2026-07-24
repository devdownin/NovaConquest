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
        // Plasma is a tier-2 tech (base 14) + 6 * 1 unlocked in branch.
        assertEquals(14 + 6, cost)
    }

    @Test
    fun higherTierTechsCostMore() {
        // With no branch techs unlocked, cost is the tier base alone — later tiers cost more.
        val t1 = CostCalculator.techCost("tech_hull_plating", emptySet())   // tier 1
        val t2 = CostCalculator.techCost("tech_plasma_weapons", emptySet()) // tier 2
        val t4 = CostCalculator.techCost("tech_nano_armor", emptySet())     // tier 4
        assertTrue("Tech cost should rise with tier", t1 < t2 && t2 < t4)
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
