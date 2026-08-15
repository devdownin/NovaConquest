package com.novaempire.core.engine

import com.novaempire.core.domain.models.CampaignObjectiveType
import com.novaempire.core.domain.models.CampaignRegistry
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GloryRegistry
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.CampaignProgress
import com.novaempire.core.domain.state.CampaignState
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignTest {

    private val gate = HexCoord(-5, 5, 0)

    /**
     * Waits for the end-of-turn cycle to settle on a decided game.
     *
     * A fixed `delay(...)` would be a guess: too short and the test fails on a loaded machine, too
     * long and every run pays for it. Suspending on the condition itself is exact.
     */
    private suspend fun GameEngine.awaitVictory(): GameState =
        withTimeout(5_000) { state.first { it.victoryReason != null } }

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

    @Test
    fun restoringProgressPutsBackTheEarnedRecordOnly() {
        val engine = GameEngine(NoOpAI())
        val running = GameState(campaignState = CampaignState(activeMissionId = "mission_3"))

        val after = engine.reduce(
            running,
            GameIntent.RestoreCampaignProgress(CampaignProgress(setOf("mission_1"), 6))
        ).newState

        assertEquals(setOf("mission_1"), after.campaignState.completedMissions)
        assertEquals(6, after.campaignState.gloryPoints)
        assertEquals("restoring a record must not touch the board", "mission_3", after.campaignState.activeMissionId)
    }

    @Test
    fun loadingASaveKeepsTheDurableRecordOverTheOneInTheFile() {
        // The store is seeded at boot and is the authority: a save written before the last mission
        // was won carries a staler record, and glory it still shows has since been spent.
        val engine = GameEngine(NoOpAI())
        val booted = GameState(campaignState = CampaignState(completedMissions = setOf("mission_1", "mission_2"), gloryPoints = 1))
        val fromFile = GameState(campaignState = CampaignState(activeMissionId = "mission_3", completedMissions = setOf("mission_1"), gloryPoints = 5))

        val after = engine.reduce(booted, GameIntent.LoadGame(fromFile)).newState

        assertEquals("completed missions are unioned", setOf("mission_1", "mission_2"), after.campaignState.completedMissions)
        assertEquals("glory comes from the durable record, spending included", 1, after.campaignState.gloryPoints)
        assertEquals("the mission being played belongs to the file", "mission_3", after.campaignState.activeMissionId)
    }

    // ── Glory: earning ────────────────────────────────────────────────────────

    @Test
    fun completingAMissionAwardsItsGloryOnce() = runBlocking {
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val engine = GameEngine(NoOpAI())

        engine.processIntent(GameIntent.LoadGame(gateState(owner = Faction.DOMINION)))
        engine.processIntent(GameIntent.EndTurn)

        val won = engine.awaitVictory()
        assertTrue("mission_3" in won.campaignState.completedMissions)
        assertEquals(mission.gloryReward, won.campaignState.gloryPoints)
    }

    @Test
    fun replayingACompletedMissionAwardsNoFurtherGlory() = runBlocking {
        // Otherwise the shortest mission is a perk farm: replay, bank, replay.
        val engine = GameEngine(NoOpAI())
        // Boot order the app uses: the durable record is restored before any board is loaded.
        engine.processIntent(GameIntent.RestoreCampaignProgress(CampaignProgress(setOf("mission_3"), 4)))
        engine.processIntent(GameIntent.LoadGame(gateState(owner = Faction.DOMINION)))
        engine.processIntent(GameIntent.EndTurn)

        assertEquals(4, engine.awaitVictory().campaignState.gloryPoints)
    }

    // ── Glory: spending ───────────────────────────────────────────────────────

    @Test
    fun perksAreChargedAndApplied() {
        val engine = GameEngine(NoOpAI())
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val chest = GloryRegistry.find("perk_war_chest")!!
        val hulls = GloryRegistry.find("perk_prototype_hull")!!
        val before = GameState(
            campaignState = CampaignState(gloryPoints = 10),
            playerStates = mapOf(
                mission.playerFaction to PlayerState(mission.playerFaction, credits = 100),
                mission.enemyFaction to PlayerState(mission.enemyFaction, credits = 100)
            )
        )

        val after = engine.reduce(
            before,
            GameIntent.StartCampaign(mission.id, setOf(chest.id, hulls.id))
        ).newState

        val player = after.playerStates[mission.playerFaction]!!
        assertEquals(10 - chest.cost - hulls.cost, after.campaignState.gloryPoints)
        assertEquals(100 + chest.bonusCredits, player.credits)
        assertTrue(hulls.grantsTechId!! in player.techUnlocked)
    }

    @Test
    fun anUnaffordableLoadoutIsRefusedWholesale() {
        // The dangerous failure is a half-applied launch: glory charged, mission started, bonuses
        // missing. Nothing may change unless everything can.
        val engine = GameEngine(NoOpAI())
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val before = GameState(
            campaignState = CampaignState(gloryPoints = 1),
            playerStates = mapOf(
                mission.playerFaction to PlayerState(mission.playerFaction, credits = 100),
                mission.enemyFaction to PlayerState(mission.enemyFaction, credits = 100)
            )
        )

        val result = engine.reduce(before, GameIntent.StartCampaign(mission.id, setOf("perk_war_chest")))

        assertNotNull("an unaffordable loadout must report why", result.error)
        assertEquals("nothing may change", before, result.newState)
    }

    @Test
    fun anUnknownPerkIsRefusedRatherThanIgnored() {
        // Silently dropping it would launch a mission the player believes they paid to equip.
        val engine = GameEngine(NoOpAI())
        val before = GameState(campaignState = CampaignState(gloryPoints = 10))

        val result = engine.reduce(before, GameIntent.StartCampaign("mission_1", setOf("perk_does_not_exist")))

        assertNotNull(result.error)
        assertEquals(before, result.newState)
    }

    @Test
    fun launchingWithoutPerksCostsNothing() {
        val engine = GameEngine(NoOpAI())
        val before = GameState(campaignState = CampaignState(gloryPoints = 3))
        val after = engine.reduce(before, GameIntent.StartCampaign("mission_1")).newState
        assertEquals(3, after.campaignState.gloryPoints)
    }

    @Test
    fun everyPerkGrantsARealTechAndCostsSomething() {
        // A perk pointing at a renamed tech id would be bought, charged, and grant nothing.
        val techIds = com.novaempire.core.domain.models.TechRegistry.ALL_TECHS.map { it.id }.toSet()
        GloryRegistry.ALL_PERKS.forEach { perk ->
            assertTrue("${perk.id} is free", perk.cost > 0)
            assertTrue(
                "${perk.id} grants an effect nobody can feel",
                perk.bonusCredits > 0 || perk.grantsTechId != null
            )
            perk.grantsTechId?.let {
                assertTrue("${perk.id} grants unknown tech \"$it\"", it in techIds)
            }
        }
        val ids = GloryRegistry.ALL_PERKS.map { it.id }
        assertEquals("duplicate perk ids", ids.size, ids.toSet().size)
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
