package com.novaempire.core.engine

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
        listOf(HeroRegistry.VANCE, HeroRegistry.ELARA, HeroRegistry.NIX, HeroRegistry.KAEL).forEach {
            assertNotNull("Named hero constant $it must be in the registry", HeroRegistry.getHero(it))
        }
    }
}
