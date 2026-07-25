package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiplomacyEvaluatorTest {

    private fun state(
        proposerCredits: Int,
        targetCredits: Int,
        targetRelations: Map<Faction, DiplomaticRelation> = emptyMap()
    ) = GameState(
        activeFaction = Faction.DOMINION,
        playerStates = mapOf(
            Faction.DOMINION to PlayerState(Faction.DOMINION, credits = proposerCredits),
            Faction.TRADERS to PlayerState(Faction.TRADERS, credits = targetCredits, relations = targetRelations)
        )
    )

    @Test
    fun warNeedsNoConsent() {
        // You never need permission to attack someone.
        val s = state(proposerCredits = 1, targetCredits = 10_000)
        assertTrue(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.WAR))
    }

    @Test
    fun allianceRefusedWhenTheProposerBringsNothing() {
        // Regression: an alliance used to be imposed on the other side, making the proposer
        // untouchable because the AI only ever engages WAR targets.
        val s = state(proposerCredits = 10, targetCredits = 1000)
        assertFalse(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.ALLIANCE))
    }

    @Test
    fun allianceAcceptedFromAComparablePower() {
        val s = state(proposerCredits = 100, targetCredits = 100)
        assertTrue(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.ALLIANCE))
    }

    @Test
    fun aFactionAtWarElsewhereWelcomesEvenAWeakAlly() {
        val s = state(
            proposerCredits = 10, targetCredits = 1000,
            targetRelations = mapOf(Faction.XYLAR to DiplomaticRelation.WAR)
        )
        assertTrue(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.ALLIANCE))
    }

    @Test
    fun ceasefireRefusedByAFactionThatIsWinning() {
        val s = state(proposerCredits = 10, targetCredits = 1000)
        assertFalse(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.NEUTRAL))
    }

    @Test
    fun ceasefireAcceptedWhenTheFightIsEven() {
        val s = state(proposerCredits = 100, targetCredits = 100)
        assertTrue(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.TRADERS, DiplomaticRelation.NEUTRAL))
    }

    @Test
    fun powerCountsFleetHealthNotJustCredits() {
        val pos = HexCoord(0, 0, 0)
        val withFleet = GameState(
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 50)),
            units = mapOf(pos to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = pos, currentHp = 25))
        )
        assertTrue(
            "a fleet must count towards a faction's weight",
            DiplomacyEvaluator.power(withFleet, Faction.DOMINION) > 50
        )
    }

    @Test
    fun anUnknownFactionCannotAgreeToAnything() {
        // ANCIENT_NPC has no PlayerState — it takes no part in diplomacy.
        val s = state(proposerCredits = 100, targetCredits = 100)
        assertFalse(DiplomacyEvaluator.wouldAccept(s, Faction.DOMINION, Faction.ANCIENT_NPC, DiplomaticRelation.ALLIANCE))
    }
}
