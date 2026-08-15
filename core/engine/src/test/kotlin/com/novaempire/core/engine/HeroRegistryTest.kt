package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.HeroRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the hero data the (now single-sourced) Hero Academy UI displays: ids are unique, costs
 * are positive, and every hero resolves through [HeroRegistry.getHero]. The academy previously
 * hard-coded a divergent list (wrong costs/factions) — these invariants keep the one source honest.
 */
class HeroRegistryTest {

    @Test
    fun allHeroIdsAreUnique() {
        val ids = HeroRegistry.ALL_HEROES.map { it.id }
        assertEquals("Duplicate hero ids", ids.size, ids.toSet().size)
    }

    @Test
    fun everyHeroHasPositiveCostAndResolves() {
        HeroRegistry.ALL_HEROES.forEach { hero ->
            assertTrue("${hero.id} should cost > 0", hero.cost > 0)
            assertNotNull("${hero.id} must resolve via getHero", HeroRegistry.getHero(hero.id))
            assertNotNull("${hero.id} needs an affinity faction", hero.targetFaction)
        }
    }

    @Test
    fun namedHeroConstantsAllExist() {
        listOf(
            HeroRegistry.VANCE, HeroRegistry.ELARA, HeroRegistry.NIX, HeroRegistry.KAEL,
            HeroRegistry.SARN, HeroRegistry.YSAR, HeroRegistry.VASHK
        ).forEach {
            assertNotNull("Named hero constant $it must be in the registry", HeroRegistry.getHero(it))
        }
    }

    @Test
    fun everyPlayableFactionHasAChampionOfItsOwn() {
        // Affinity is priced, not locked: an off-faction hero costs double. A faction with no hero
        // of its own therefore pays a permanent surcharge for a gap in the content rather than for
        // a choice its player made. NOMADS, KAELEN and XYLAR were in exactly that position.
        val withHeroes = HeroRegistry.ALL_HEROES.map { it.targetFaction }.toSet()
        Faction.values()
            .filter { it != Faction.ANCIENT_NPC }
            .forEach {
                assertTrue("${it.displayName} has no hero of its own affinity", it in withHeroes)
            }
    }

    @Test
    fun everyHeroCarriesAPassiveThatSomeSystemReads() {
        // A hero whose bonus list is empty would charge full price for a description. The bonus
        // types themselves are exercised elsewhere; what matters here is that none is absent.
        HeroRegistry.ALL_HEROES.forEach {
            assertTrue("${it.id} grants no passive at all", it.bonuses.isNotEmpty())
            assertTrue("${it.id} has an empty passive description", it.bonusDescription.isNotBlank())
        }
    }
}
