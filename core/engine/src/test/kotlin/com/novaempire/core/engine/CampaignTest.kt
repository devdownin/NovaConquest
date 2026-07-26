package com.novaempire.core.engine

import com.novaempire.core.domain.models.CampaignObjectiveType
import com.novaempire.core.domain.models.CampaignRegistry
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.CampaignState
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignTest {

    private val gate = HexCoord(-5, 5, 0)

    /** A mission_3 state: the gate world exists and is held by [owner]. */
    private fun gateState(owner: Faction, turn: Int = 3) = GameState(
        turn = turn,
        activeFaction = Faction.DOMINION,
        humanFaction = Faction.DOMINION,
        campaignState = CampaignState(activeMissionId = "mission_3"),
        playerStates = mapOf(
            Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 50),
            Faction.KAELEN to PlayerState(Faction.KAELEN, credits = 50)
        ),
        map = GameMap(tiles = mapOf(gate to HexTile(gate, TerrainType.PLANET, systemLevel = 2, owner = owner))),
        // A surviving unit, otherwise the annihilation check fires first.
        units = mapOf(
            HexCoord(0, 0, 0) to com.novaempire.core.domain.models.GameUnit(
                type = com.novaempire.core.domain.models.UnitType.CRUISER,
                faction = Faction.DOMINION, position = HexCoord(0, 0, 0), currentHp = 25
            )
        )
    )

    // ── CAPTURE_SPECIFIC_PLANET (P0.1) ────────────────────────────────────────

    @Test
    fun captureObjectiveIsWonWhenTheTargetChangesHands() {
        // Regression: this objective type fell into `else -> false`, so the mission was unwinnable.
        val result = VictoryChecker.check(gateState(owner = Faction.DOMINION))
        assertNotNull("capturing the target world must complete the mission", result)
        assertEquals(Faction.DOMINION, result!!.winner)
        assertTrue(result.reason.contains("Campaign Mission Complete"))
    }

    @Test
    fun captureObjectiveIsNotWonWhileTheEnemyHoldsTheTarget() {
        assertNull(VictoryChecker.check(gateState(owner = Faction.KAELEN)))
    }

    @Test
    fun malformedTargetCoordinateDoesNotCrashTheCheck() {
        // A typo in mission data must leave the objective unmet, never throw mid-game.
        listOf("", "abc", "5", "5,", "1,2,3", "x,y").forEach {
            assertNull("\"$it\" should not parse", VictoryChecker.parseTargetCoord(it))
        }
        assertEquals(HexCoord(-5, 5, 0), VictoryChecker.parseTargetCoord("-5,5"))
        assertEquals(HexCoord(2, -1, -1), VictoryChecker.parseTargetCoord(" 2 , -1 "))
    }

    // ── Deadline (P0.4) ───────────────────────────────────────────────────────

    @Test
    fun missionIsFailedPastItsDeadline() {
        // Without a deadline the campaign branch never times out: it returns before the standard
        // 100-turn rule, so an unreachable objective would run forever.
        val limit = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }.turnLimit
        val result = VictoryChecker.check(gateState(owner = Faction.KAELEN, turn = limit + 1))
        assertNotNull(result)
        assertEquals("the scripted enemy wins by default", Faction.KAELEN, result!!.winner)
        assertTrue(result.reason.contains("deadline"))
    }

    @Test
    fun objectiveMetOnTheDeadlineTurnStillWins() {
        val limit = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }.turnLimit
        val result = VictoryChecker.check(gateState(owner = Faction.DOMINION, turn = limit))
        assertEquals(Faction.DOMINION, result?.winner)
    }

    // ── Enemy head start (P0.3) ───────────────────────────────────────────────

    @Test
    fun startingACampaignGrantsTheScriptedEnemyItsBonusCredits() {
        // enemyBonusCredits was declared on every mission but never applied, so scripted opponents
        // all began on equal footing with the player — the difficulty dial did nothing.
        val engine = GameEngine(NoOpAI())
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val before = GameState(
            playerStates = mapOf(
                mission.playerFaction to PlayerState(mission.playerFaction, credits = 100),
                mission.enemyFaction to PlayerState(mission.enemyFaction, credits = 100)
            )
        )
        val after = engine.reduce(before, GameIntent.StartCampaign(mission.id)).newState
        assertEquals(
            100 + mission.enemyBonusCredits,
            after.playerStates[mission.enemyFaction]!!.credits
        )
        assertEquals("the player's treasury is untouched", 100, after.playerStates[mission.playerFaction]!!.credits)
    }

    // ── Progression (P0.2) ────────────────────────────────────────────────────

    @Test
    fun campaignProgressSurvivesStartingTheNextMission() {
        // createInitialState builds a brand-new GameState whose campaignState is empty, so launching
        // the next mission used to wipe the record of the previous one.
        val engine = GameEngine(NoOpAI())
        val done = GameState(campaignState = CampaignState(completedMissions = setOf("mission_1"), gloryPoints = 7))

        val next = engine.reduce(done, GameIntent.StartNewGameWithSize()).newState

        assertEquals(setOf("mission_1"), next.campaignState.completedMissions)
        assertEquals(7, next.campaignState.gloryPoints)
        assertNull("the new board has no mission running yet", next.campaignState.activeMissionId)
    }

    // ── Mission data integrity ────────────────────────────────────────────────

    @Test
    fun everyCaptureObjectiveCarriesAParsableTarget() {
        // Guards the trap that made this whole area fragile: a capture mission whose target cannot
        // be parsed is silently unwinnable.
        CampaignRegistry.MISSIONS
            .filter { it.objective.type == CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET }
            .forEach {
                assertNotNull(
                    "${it.id} has an unparsable capture target: \"${it.objective.targetString}\"",
                    VictoryChecker.parseTargetCoord(it.objective.targetString)
                )
            }
    }

    @Test
    fun missionIdsAreUniqueAndDeadlinesNonNegative() {
        val ids = CampaignRegistry.MISSIONS.map { it.id }
        assertEquals("duplicate mission ids", ids.size, ids.toSet().size)
        CampaignRegistry.MISSIONS.forEach {
            assertTrue("${it.id} has a negative deadline", it.turnLimit >= 0)
            assertTrue("${it.id} grants negative enemy credits", it.enemyBonusCredits >= 0)
        }
    }

    @Test
    fun surviveMissionsDeadlineDoesNotPreemptTheirObjective() {
        // A SURVIVE_TURNS mission must be winnable: its deadline, if any, has to leave room for the
        // survival target to be reached.
        CampaignRegistry.MISSIONS
            .filter { it.objective.type == CampaignObjectiveType.SURVIVE_TURNS && it.turnLimit > 0 }
            .forEach {
                assertTrue(
                    "${it.id}: deadline ${it.turnLimit} is before its survival target ${it.objective.targetValue}",
                    it.turnLimit >= it.objective.targetValue
                )
            }
    }
}
