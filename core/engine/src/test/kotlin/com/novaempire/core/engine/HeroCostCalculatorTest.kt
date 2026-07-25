package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HeroRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroCostCalculatorTest {

    private val vance = HeroRegistry.getHero(HeroRegistry.VANCE)!!   // DOMINION
    private val nix = HeroRegistry.getHero(HeroRegistry.NIX)!!       // ANCIENT_NPC → mercenary

    @Test
    fun ownFactionHeroCostsBasePrice() {
        assertEquals(vance.cost, HeroCostCalculator.costFor(vance, Faction.DOMINION))
    }

    @Test
    fun offFactionHeroCostsAPremium() {
        assertEquals(
            vance.cost * HeroCostCalculator.OFF_FACTION_MULTIPLIER,
            HeroCostCalculator.costFor(vance, Faction.TRADERS)
        )
    }

    @Test
    fun mercenarySellsToEveryoneAtBasePrice() {
        // Nix swears to ANCIENT_NPC — no living empire — so nobody pays the loyalty premium.
        assertTrue(HeroCostCalculator.isMercenary(nix))
        Faction.values().forEach { faction ->
            assertEquals("$faction should pay base price for the mercenary",
                nix.cost, HeroCostCalculator.costFor(nix, faction))
        }
    }

    @Test
    fun everyFactionCanStillReachEveryHero() {
        // A hard affinity lock would have left NOMADS, KAELEN and XYLAR with no hero at all —
        // pricing keeps the whole roster reachable for every faction.
        val playable = Faction.values().filter { it != Faction.ANCIENT_NPC }
        playable.forEach { faction ->
            HeroRegistry.ALL_HEROES.forEach { hero ->
                assertTrue(
                    "${hero.id} must remain purchasable by $faction",
                    HeroCostCalculator.costFor(hero, faction) > 0
                )
            }
        }
    }
}
