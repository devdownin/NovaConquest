package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EventTargetingTest {

    @Test
    fun targetedEventBonusReachesOnlyTheTargetFaction() {
        val dominion = PlayerState(Faction.DOMINION) // no faction income bonus
        val traders = PlayerState(Faction.TRADERS)   // +5 INCOME_FLAT from faction

        // ECONOMIC_BOOM (+3 INCOME_FLAT) targeted at DOMINION.
        val boomForTarget = BonusRegistry.sum(BonusType.INCOME_FLAT, dominion, GalacticEvent.ECONOMIC_BOOM, Faction.DOMINION)
        val boomForOther = BonusRegistry.sum(BonusType.INCOME_FLAT, traders, GalacticEvent.ECONOMIC_BOOM, Faction.DOMINION)
        val baseline = BonusRegistry.sum(BonusType.INCOME_FLAT, dominion, GalacticEvent.NONE, null)

        assertEquals("target gets the +3 boom", 3, boomForTarget)
        assertEquals("non-target keeps only its own +5, no boom", 5, boomForOther)
        assertEquals(0, baseline)
    }

    @Test
    fun globalEventBonusAppliesToEveryone() {
        val a = PlayerState(Faction.DOMINION)
        val b = PlayerState(Faction.TRADERS)
        // ION_STORM (-1 MOVEMENT_MODIFIER) is global: null target → hits both.
        assertEquals(-1, BonusRegistry.sum(BonusType.MOVEMENT_MODIFIER, a, GalacticEvent.ION_STORM, null))
        assertEquals(-1, BonusRegistry.sum(BonusType.MOVEMENT_MODIFIER, b, GalacticEvent.ION_STORM, null))
    }

    @Test
    fun eventSystemSetsATargetExactlyForTargetedEvents() {
        val base = GameState(
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION),
                Faction.TRADERS to PlayerState(Faction.TRADERS)
            )
        )
        for (seed in 0L..40L) {
            val next = EventSystem.tick(base, Random(seed))
            if (next.activeEvent == GalacticEvent.NONE) continue
            if (next.activeEvent.isTargeted) {
                assertTrue("targeted event must pick a faction", next.eventTargetFaction != null)
                assertTrue("target must be a real active faction", next.eventTargetFaction != Faction.ANCIENT_NPC)
            } else {
                assertTrue("global event must have no target", next.eventTargetFaction == null)
            }
        }
    }
}
