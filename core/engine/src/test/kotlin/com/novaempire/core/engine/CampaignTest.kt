package com.novaempire.core.engine

import com.novaempire.core.domain.models.CampaignMission
import com.novaempire.core.domain.models.CampaignObjective
import com.novaempire.core.domain.models.CampaignObjectiveType
import com.novaempire.core.domain.models.CampaignRegistry
import com.novaempire.core.domain.models.EventTarget
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GloryRegistry
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.ObjectiveMode
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.CampaignProgress
import com.novaempire.core.domain.state.CampaignState
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            campaignState = CampaignState(completedMissions = prerequisitesOf(mission.id)),
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
        // The player's purse comes from the mission's scripted opening, never from the enemy's dial.
        assertEquals(mission.setup.startingCredits, after.playerStates[mission.playerFaction]!!.credits)
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
        // mission_1 on purpose: it has no scripted opening, so this isolates what the perks do.
        // Stacking on a mission's scripted treasury is covered separately.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_1" }
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
            // Prérequis satisfaits : sans cela le lancement échouerait sur le verrou et ce test
            // passerait sans jamais éprouver le refus pour gloire insuffisante.
            campaignState = CampaignState(completedMissions = prerequisitesOf(mission.id), gloryPoints = 1),
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
    fun everyPerkCostsSomethingAndGrantsSomethingReal() {
        // A perk pointing at a renamed tech or hero id would be bought, charged, and grant nothing.
        val techIds = com.novaempire.core.domain.models.TechRegistry.ALL_TECHS.map { it.id }.toSet()
        GloryRegistry.ALL_PERKS.forEach { perk ->
            assertTrue("${perk.id} is free", perk.cost > 0)
            assertTrue(
                "${perk.id} grants an effect nobody can feel",
                perk.bonusCredits > 0 || perk.grantsTechId != null || perk.grantsUnitType != null ||
                    perk.grantsHeroId != null || perk.revealsMap
            )
            perk.grantsTechId?.let {
                assertTrue("${perk.id} grants unknown tech \"$it\"", it in techIds)
            }
            perk.grantsHeroId?.let { id ->
                val hero = com.novaempire.core.domain.models.HeroRegistry.getHero(id)
                assertNotNull("${perk.id} grants unknown hero \"$id\"", hero)
                // A faction's own champion serving a rival's mission would contradict the affinity
                // pricing the hero system is built on; only mercenaries read the same for everyone.
                assertTrue(
                    "${perk.id} grants ${hero!!.name}, who is loyal to ${hero.targetFaction} rather than for hire",
                    HeroCostCalculator.isMercenary(hero)
                )
            }
        }
        val ids = GloryRegistry.ALL_PERKS.map { it.id }
        assertEquals("duplicate perk ids", ids.size, ids.toSet().size)
    }

    // ── Glory: perks that change how a mission is played ──────────────────────

    private val capital = HexCoord(0, 0, 0)

    /**
     * Every mission that must be finished before [missionId] can start.
     *
     * Tests about perks and setups are not tests about unlocking: they need a save where the chain
     * is already satisfied, or they would all fail on the lock and stop covering what they name.
     */
    private fun prerequisitesOf(missionId: String): Set<String> {
        val done = mutableSetOf<String>()
        var current = CampaignRegistry.MISSIONS.find { it.id == missionId }?.requiresMissionId
        while (current != null && done.add(current)) {
            current = CampaignRegistry.MISSIONS.find { it.id == current }?.requiresMissionId
        }
        return done
    }

    /** A launch-ready board for [missionId]: a small blob of hexes with the player's capital at 0,0. */
    private fun launchState(missionId: String, glory: Int): GameState {
        val mission = CampaignRegistry.MISSIONS.first { it.id == missionId }
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -2..2) for (r in -2..2) {
            val s = -q - r
            if (kotlin.math.abs(s) <= 2) {
                val c = HexCoord(q, r, s)
                tiles[c] = HexTile(c, TerrainType.EMPTY)
            }
        }
        tiles[capital] = HexTile(capital, TerrainType.PLANET, systemLevel = 1, owner = mission.playerFaction)
        return GameState(
            activeFaction = mission.playerFaction,
            humanFaction = mission.playerFaction,
            campaignState = CampaignState(completedMissions = prerequisitesOf(missionId), gloryPoints = glory),
            playerStates = mapOf(
                mission.playerFaction to PlayerState(mission.playerFaction, credits = 100, capitalCoord = capital),
                mission.enemyFaction to PlayerState(mission.enemyFaction, credits = 100)
            ),
            map = GameMap(tiles = tiles, radius = 2)
        )
    }

    private fun launch(missionId: String, perkIds: Set<String>, state: GameState = launchState(missionId, 10)) =
        GameEngine(NoOpAI()).reduce(state, GameIntent.StartCampaign(missionId, perkIds))

    @Test
    fun theVanguardPerkPutsAShipOnTheBoardOnTurnOne() {
        // The point of this perk is what credits cannot buy: production takes turns, this does not.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_1" }
        val after = launch("mission_1", setOf("perk_vanguard")).newState

        val granted = after.units.values.filter { it.faction == mission.playerFaction }
        assertEquals(1, granted.size)
        val ship = granted.single()
        assertEquals(UnitType.CRUISER, ship.type)
        assertEquals("a granted ship arrives intact", UnitType.CRUISER.maxHp, ship.currentHp)
        assertTrue(
            "the ship must stand at the capital or beside it, not anywhere on the map",
            ship.position.distanceTo(capital) <= 1
        )
    }

    @Test
    fun aGrantedShipWithNowhereToStandDoesNotCrashTheLaunch() {
        // Documented edge: the glory is still spent. Better a missing ship than a refused launch
        // after the board has already been built — and `UnitPlacement` is shared with the shipyard
        // precisely so the two never disagree about what "nowhere" means.
        val crowded = launchState("mission_1", 10).let { s ->
            val blockers = (listOf(capital) + GameGridMap(s).getNeighbors(capital)).associateWith { hex ->
                com.novaempire.core.domain.models.GameUnit(
                    type = UnitType.FIGHTER, faction = Faction.XYLAR, position = hex, currentHp = 12
                )
            }
            s.copy(units = blockers)
        }

        val result = launch("mission_1", setOf("perk_vanguard"), crowded)

        assertNull("the launch itself must succeed", result.error)
        assertTrue(
            "no room means no ship",
            result.newState.units.values.none { it.faction == Faction.DOMINION }
        )
        assertEquals("the perk is still charged", 10 - 3, result.newState.campaignState.gloryPoints)
    }

    @Test
    fun theSeerContractPutsNixUnderContractFromTurnOne() {
        val after = launch("mission_1", setOf("perk_seer_contract")).newState
        assertTrue("hero_nix" in after.playerStates[Faction.DOMINION]!!.recruitedHeroes)
    }

    @Test
    fun starChartsRevealTheTerrainButNotTheEnemy() {
        // The whole distinction this perk rests on: explored ≠ visible. Vision comes from units
        // alone, so a board with no fleet has nothing visible however much of it is mapped.
        val state = launchState("mission_1", 10)
        val after = launch("mission_1", setOf("perk_star_charts"), state).newState
        val player = after.playerStates[Faction.DOMINION]!!

        assertEquals("every hex is on the charts", state.map.tiles.keys, player.exploredHexes)
        assertTrue("fog still hides fleets", player.visibleHexes.isEmpty())
    }

    @Test
    fun withoutStarChartsTheMapStaysUnknown() {
        val after = launch("mission_1", emptySet()).newState
        assertTrue(after.playerStates[Faction.DOMINION]!!.exploredHexes.isEmpty())
    }

    @Test
    fun perksOfDifferentKindsCombineInOneLaunch() {
        // Each effect is read off its own field, so a mixed basket must apply every one of them.
        val after = launch("mission_1", setOf("perk_war_chest", "perk_vanguard", "perk_star_charts")).newState
        val player = after.playerStates[Faction.DOMINION]!!

        assertEquals(100 + 150, player.credits)
        assertTrue(after.units.values.any { it.faction == Faction.DOMINION && it.type == UnitType.CRUISER })
        assertEquals(after.map.tiles.keys, player.exploredHexes)
        // Spelled out rather than summed from the registry: a test that recomputes the cost the
        // same way the code does would agree with any mistake both of them made.
        assertEquals("war chest 2 + vanguard 3 + charts 2", 10 - 7, after.campaignState.gloryPoints)
    }

    // ── Scripted starting conditions ──────────────────────────────────────────

    @Test
    fun aScriptedMissionOpensWithItsOwnFleetTechAndTreasury() {
        // Mission 3 is a raid: hulls instead of an economy. Before this, every mission opened the
        // same way and only the map and the enemy purse could differ.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val after = launch("mission_3", emptySet(), launchState("mission_3", 0)).newState
        val player = after.playerStates[mission.playerFaction]!!

        assertEquals(mission.setup.startingCredits, player.credits)
        assertTrue("tech_hull_plating" in player.techUnlocked)
        assertEquals(
            "every scripted hull reaches the board",
            mission.setup.startingFleet.size,
            after.units.values.count { it.faction == mission.playerFaction }
        )
        assertTrue(
            "the scripted fleet stands at the capital",
            after.units.values.filter { it.faction == mission.playerFaction }
                .all { it.position.distanceTo(capital) <= 1 }
        )
    }

    @Test
    fun aMissionWithoutASetupOpensTheStandardWay() {
        val before = launchState("mission_1", 0)
        val after = launch("mission_1", emptySet(), before).newState
        assertEquals(100, after.playerStates[Faction.DOMINION]!!.credits)
        assertTrue(after.units.isEmpty())
    }

    @Test
    fun perkCreditsStackOnTopOfTheMissionsBaseTreasury() {
        // The order is the whole point of routing both through one path: the mission sets the
        // treasury, the perk adds to it. Either winning outright would be a silent balance bug.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val after = launch("mission_3", setOf("perk_war_chest"), launchState("mission_3", 10)).newState

        assertEquals(mission.setup.startingCredits!! + 150, after.playerStates[mission.playerFaction]!!.credits)
    }

    @Test
    fun aScriptedFleetAndABoughtShipBothReachTheBoard() {
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val after = launch("mission_3", setOf("perk_vanguard"), launchState("mission_3", 10)).newState

        assertEquals(
            mission.setup.startingFleet.size + 1,
            after.units.values.count { it.faction == mission.playerFaction }
        )
    }

    @Test
    fun aScriptedStartLiftsTheFogAroundTheNewFleet() {
        // Without the vision recompute the starting squadron would sit in fog until something else
        // happened to trigger one.
        val after = launch("mission_3", emptySet(), launchState("mission_3", 0)).newState
        assertTrue(after.playerStates[Faction.DOMINION]!!.visibleHexes.isNotEmpty())
    }

    // `startingPlanets` is the one lever no shipped mission uses yet — see AUDIT_CAMPAGNES.md §7 —
    // so it is exercised here directly rather than through the registry.

    @Test
    fun aScriptedWorldChangesHandsBeforeTheFirstTurn() {
        val outpost = HexCoord(2, -2, 0)
        val board = launchState("mission_1", 0).let { s ->
            s.copy(map = s.map.copy(tiles = s.map.tiles + (outpost to HexTile(outpost, TerrainType.PLANET, systemLevel = 2))))
        }

        val after = applyLoadout(board, Faction.DOMINION, Loadout(planets = listOf(outpost)))

        assertEquals(Faction.DOMINION, after.map.tiles[outpost]!!.owner)
    }

    @Test
    fun aScriptedWorldOnEmptySpaceIsLeftAloneRatherThanConjured() {
        // Turning arbitrary terrain into a world would let a mission drop one inside an asteroid
        // field, which MapFactory's connectivity pass never promised to keep reachable.
        val empty = HexCoord(1, -1, 0)
        val board = launchState("mission_1", 0)
        assertEquals(TerrainType.EMPTY, board.map.tiles[empty]!!.terrain)

        val after = applyLoadout(board, Faction.DOMINION, Loadout(planets = listOf(empty)))

        assertNull(after.map.tiles[empty]!!.owner)
        assertEquals(TerrainType.EMPTY, after.map.tiles[empty]!!.terrain)
    }

    @Test
    fun everyScriptedSetupReferencesThingsThatExist() {
        // The trap this whole area keeps producing: data pointing at a renamed id, bought and
        // charged, granting nothing.
        val techIds = com.novaempire.core.domain.models.TechRegistry.ALL_TECHS.map { it.id }.toSet()
        CampaignRegistry.MISSIONS.forEach { mission ->
            mission.setup.startingTechs.forEach {
                assertTrue("${mission.id} starts with unknown tech \"$it\"", it in techIds)
            }
            mission.setup.startingPlanets.forEach {
                assertNotNull(
                    "${mission.id} has an unparsable starting planet \"$it\"",
                    VictoryChecker.parseTargetCoord(it)
                )
            }
            mission.setup.startingCredits?.let {
                assertTrue("${mission.id} starts with negative credits", it >= 0)
            }
        }
    }

    // ── Scripted events ───────────────────────────────────────────────────────

    private fun runningMission(missionId: String, turn: Int) = GameState(
        turn = turn,
        campaignState = CampaignState(activeMissionId = missionId),
        playerStates = CampaignRegistry.MISSIONS.first { it.id == missionId }.let {
            mapOf(
                it.playerFaction to PlayerState(it.playerFaction),
                it.enemyFaction to PlayerState(it.enemyFaction)
            )
        }
    )

    @Test
    fun aScriptedBeatFiresOnItsTurn() {
        val beat = CampaignRegistry.MISSIONS.first { it.id == "mission_1" }.scriptedEvents.first()
        val after = EventSystem.tick(runningMission("mission_1", beat.turn))

        assertEquals(beat.event, after.activeEvent)
        assertEquals(beat.duration, after.eventDurationRemaining)
    }

    @Test
    fun aScriptedBeatOverridesAnEventAlreadyRunning() {
        // Waiting for a free slot would let a random event swallow the mission's set piece, and it
        // would vanish with no sign that anything was meant to happen.
        val beat = CampaignRegistry.MISSIONS.first { it.id == "mission_1" }.scriptedEvents.first()
        val busy = runningMission("mission_1", beat.turn).copy(
            activeEvent = com.novaempire.core.domain.models.GalacticEvent.TECH_RUSH,
            eventDurationRemaining = 4
        )

        assertEquals(beat.event, EventSystem.tick(busy).activeEvent)
    }

    @Test
    fun noBeatIsDueOnAQuietTurn() {
        val beat = CampaignRegistry.MISSIONS.first { it.id == "mission_1" }.scriptedEvents.first()
        assertNull(EventSystem.scriptedEventDue(runningMission("mission_1", beat.turn + 1)))
    }

    @Test
    fun aBeatAimedAtThePlayerLandsOnThePlayer() {
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_2" }
        val beat = mission.scriptedEvents.first { it.target == EventTarget.PLAYER && it.event.isTargeted }

        val after = EventSystem.tick(runningMission(mission.id, beat.turn))

        assertEquals(mission.playerFaction, after.eventTargetFaction)
    }

    @Test
    fun aBeatAimedAtTheEnemyLandsOnTheEnemy() {
        // mission_3 hands the Hegemony a windfall mid-siege: the mission has a shape, not a slope.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val beat = mission.scriptedEvents.first { it.target == EventTarget.ENEMY }

        val after = EventSystem.tick(runningMission(mission.id, beat.turn))

        assertEquals(mission.enemyFaction, after.eventTargetFaction)
    }

    @Test
    fun outsideACampaignNothingIsScripted() {
        assertNull(EventSystem.scriptedEventDue(GameState(turn = 8)))
    }

    @Test
    fun everyScriptedBeatCanActuallyFire() {
        // A beat scheduled after the mission's own deadline is dead data: the mission ends first and
        // the set piece never happens, silently. Turn 0 is equally dead — the counter starts at 1.
        CampaignRegistry.MISSIONS.forEach { mission ->
            mission.scriptedEvents.forEach { beat ->
                assertTrue("${mission.id}: a beat on turn ${beat.turn} can never fire", beat.turn >= 1)
                if (mission.turnLimit > 0) {
                    assertTrue(
                        "${mission.id}: beat on turn ${beat.turn} is past the deadline ${mission.turnLimit}",
                        beat.turn <= mission.turnLimit
                    )
                }
                assertTrue("${mission.id}: a beat with no duration does nothing", beat.duration > 0)
            }
            val turns = mission.scriptedEvents.map { it.turn }
            assertEquals("${mission.id}: two beats share a turn, only one can fire", turns.size, turns.toSet().size)
        }
    }

    // ── Sequential unlocking ──────────────────────────────────────────────────

    @Test
    fun aLockedMissionIsRefusedByTheEngineNotJustHiddenByTheScreen() {
        val engine = GameEngine(NoOpAI())
        val locked = CampaignRegistry.MISSIONS.first { it.requiresMissionId != null }
        val before = GameState(campaignState = CampaignState(gloryPoints = 10))

        val result = engine.reduce(before, GameIntent.StartCampaign(locked.id))

        assertNotNull("a locked mission must be refused", result.error)
        assertTrue(result.error!!.contains("Locked"))
        assertEquals("nothing may change", before, result.newState)
    }

    @Test
    fun completingThePrerequisiteUnlocksTheNextMission() {
        val engine = GameEngine(NoOpAI())
        val locked = CampaignRegistry.MISSIONS.first { it.requiresMissionId != null }
        val unlocked = GameState(
            campaignState = CampaignState(completedMissions = setOf(locked.requiresMissionId!!))
        )

        val result = engine.reduce(unlocked, GameIntent.StartCampaign(locked.id))

        assertNull(result.error)
        assertEquals(locked.id, result.newState.campaignState.activeMissionId)
    }

    @Test
    fun thePrerequisiteChainIsSaneAndReachable() {
        val ids = CampaignRegistry.MISSIONS.map { it.id }.toSet()
        CampaignRegistry.MISSIONS.forEach { mission ->
            val required = mission.requiresMissionId ?: return@forEach
            assertTrue("${mission.id} requires unknown mission \"$required\"", required in ids)
            assertTrue("${mission.id} requires itself", required != mission.id)
        }
        // At least one mission must be playable from a clean save, or the campaign cannot start.
        assertTrue(
            "every mission is locked — the campaign has no entry point",
            CampaignRegistry.MISSIONS.any { it.requiresMissionId == null }
        )
        // No cycles: walking the chain from any mission must terminate.
        CampaignRegistry.MISSIONS.forEach { start ->
            val seen = mutableSetOf(start.id)
            var current = start.requiresMissionId
            while (current != null) {
                assertTrue("prerequisite cycle involving ${start.id}", seen.add(current))
                current = CampaignRegistry.MISSIONS.find { it.id == current }?.requiresMissionId
            }
        }
    }

    // ── Narration ─────────────────────────────────────────────────────────────

    @Test
    fun everyMissionThatSpeaksSpeaksOnBothOutcomes() {
        // A mission with a victory text but no defeat text ends in silence exactly when the player
        // most wants to be told what happened.
        CampaignRegistry.MISSIONS.forEach { mission ->
            if (mission.victoryText.isNotBlank() || mission.defeatText.isNotBlank()) {
                assertTrue("${mission.id} wins in silence", mission.victoryText.isNotBlank())
                assertTrue("${mission.id} loses in silence", mission.defeatText.isNotBlank())
            }
            assertTrue("${mission.id} has no description at all", mission.description.isNotBlank())
        }
    }

    // ── Composite objectives ──────────────────────────────────────────────────

    /** A state for [missionId] with [credits] in the player's treasury, on turn [turn]. */
    private fun missionState(missionId: String, credits: Int, turn: Int): GameState {
        val mission = CampaignRegistry.MISSIONS.first { it.id == missionId }
        val home = HexCoord(0, 0, 0)
        return GameState(
            turn = turn,
            activeFaction = mission.playerFaction,
            humanFaction = mission.playerFaction,
            campaignState = CampaignState(activeMissionId = missionId),
            playerStates = mapOf(
                mission.playerFaction to PlayerState(mission.playerFaction, credits = credits),
                mission.enemyFaction to PlayerState(mission.enemyFaction, credits = 50)
            ),
            // An enemy planet, so DEFEAT_FACTION is not accidentally satisfied by an empty board.
            map = GameMap(tiles = mapOf(
                home to HexTile(home, TerrainType.PLANET, systemLevel = 1, owner = mission.playerFaction),
                HexCoord(2, -2, 0) to HexTile(HexCoord(2, -2, 0), TerrainType.PLANET, owner = mission.enemyFaction)
            )),
            units = mapOf(
                home to com.novaempire.core.domain.models.GameUnit(
                    type = com.novaempire.core.domain.models.UnitType.CRUISER,
                    faction = mission.playerFaction, position = home, currentHp = 25
                )
            )
        )
    }

    @Test
    fun allModeNeedsEveryObjective() {
        // mission_4: survive 20 turns AND hold 300 credits.
        assertNull("turns reached but treasury short", VictoryChecker.check(missionState("mission_4", credits = 100, turn = 25)))
        assertNull("treasury full but too early", VictoryChecker.check(missionState("mission_4", credits = 400, turn = 5)))
        val both = VictoryChecker.check(missionState("mission_4", credits = 400, turn = 25))
        assertNotNull("both met must win", both)
        assertEquals(Faction.DOMINION, both!!.winner)
    }

    @Test
    fun anyModeNeedsOnlyOneObjective() {
        // mission_2: bank 500 credits OR eliminate the rival. Neither met → no victory.
        assertNull(VictoryChecker.check(missionState("mission_2", credits = 100, turn = 5)))
        assertNotNull(
            "the credits route alone must win",
            VictoryChecker.check(missionState("mission_2", credits = 500, turn = 5))
        )
        // The other route: the enemy holds nothing and has no units left.
        val poorButVictorious = missionState("mission_2", credits = 10, turn = 5).let { s ->
            s.copy(map = s.map.copy(tiles = s.map.tiles.filterValues { it.owner != Faction.KAELEN }))
        }
        assertNotNull("the elimination route alone must win", VictoryChecker.check(poorButVictorious))
    }

    @Test
    fun eachObjectiveTypeIsJudgedByTheSameRule() {
        // isObjectiveMet is the single place a type becomes a rule. If required and optional
        // objectives ever read it differently, a mission would be winnable but its side goal
        // unpayable, or the reverse.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_4" }
        val state = missionState("mission_4", credits = 400, turn = 25)

        assertTrue(VictoryChecker.isObjectiveMet(state, mission, CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, 20)))
        assertFalse(VictoryChecker.isObjectiveMet(state, mission, CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, 99)))
        assertTrue(VictoryChecker.isObjectiveMet(state, mission, CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, 300)))
        assertFalse(VictoryChecker.isObjectiveMet(state, mission, CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, 900)))
        assertFalse(
            "the enemy still holds a world",
            VictoryChecker.isObjectiveMet(state, mission, CampaignObjective(CampaignObjectiveType.DEFEAT_FACTION))
        )
        assertFalse(
            "an unreadable target stays unmet rather than throwing",
            VictoryChecker.isObjectiveMet(
                state, mission,
                CampaignObjective(CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET, targetString = "nowhere")
            )
        )
    }

    // ── Glory: optional objectives ────────────────────────────────────────────

    @Test
    fun bonusGloryIsPaidOnlyForObjectivesActuallyMet() {
        // mission_3 pays 2 extra for holding 250 credits at the moment of capture.
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val bonus = mission.bonusObjectives.single()

        val rich = gateState(owner = Faction.DOMINION).let { s ->
            s.copy(playerStates = s.playerStates + (Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 300)))
        }
        val broke = gateState(owner = Faction.DOMINION).let { s ->
            s.copy(playerStates = s.playerStates + (Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 10)))
        }

        assertEquals(bonus.gloryReward, VictoryChecker.bonusGloryEarned(rich, mission))
        assertEquals(0, VictoryChecker.bonusGloryEarned(broke, mission))
    }

    @Test
    fun winningWithABonusObjectiveMetPaysBothRewards() = runBlocking {
        val mission = CampaignRegistry.MISSIONS.first { it.id == "mission_3" }
        val bonus = mission.bonusObjectives.single()
        val engine = GameEngine(NoOpAI())
        val rich = gateState(owner = Faction.DOMINION).let { s ->
            s.copy(playerStates = s.playerStates + (Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 300)))
        }

        engine.processIntent(GameIntent.LoadGame(rich))
        engine.processIntent(GameIntent.EndTurn)

        assertEquals(
            mission.gloryReward + bonus.gloryReward,
            engine.awaitVictory().campaignState.gloryPoints
        )
    }

    @Test
    fun replayingAMissionPaysNoBonusGloryEither() = runBlocking {
        // The base reward is first-completion only; a side objective that kept paying would just
        // move the farm one level down. Replayed here *with the bonus objective met*, so a
        // regression would show up as glory that grew.
        val engine = GameEngine(NoOpAI())
        val rich = gateState(owner = Faction.DOMINION).let { s ->
            s.copy(playerStates = s.playerStates + (Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 300)))
        }

        engine.processIntent(GameIntent.RestoreCampaignProgress(CampaignProgress(setOf("mission_3"), 4)))
        engine.processIntent(GameIntent.LoadGame(rich))
        engine.processIntent(GameIntent.EndTurn)

        assertEquals(4, engine.awaitVictory().campaignState.gloryPoints)
    }

    // ── Mission data integrity ────────────────────────────────────────────────

    /** Every objective a mission declares, required or optional. */
    private val CampaignMission.allObjectives: List<CampaignObjective>
        get() = objectives + bonusObjectives.map { it.objective }

    @Test
    fun everyCaptureObjectiveCarriesAParsableTarget() {
        // Guards the trap that made this whole area fragile: a capture mission whose target cannot
        // be parsed is silently unwinnable. Optional objectives are checked too — an unreadable
        // target there is a reward that can never be paid.
        CampaignRegistry.MISSIONS.forEach { mission ->
            mission.allObjectives
                .filter { it.type == CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET }
                .forEach {
                    assertNotNull(
                        "${mission.id} has an unparsable capture target: \"${it.targetString}\"",
                        VictoryChecker.parseTargetCoord(it.targetString)
                    )
                }
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
    fun everyMissionDeclaresAtLeastOneObjective() {
        // An empty list under ALL reads as "everything is done": the mission would be won on turn
        // one, in silence. VictoryChecker guards it too — this catches the data, that catches the
        // rule.
        CampaignRegistry.MISSIONS.forEach {
            assertTrue("${it.id} declares no objective", it.objectives.isNotEmpty())
        }
    }

    @Test
    fun bonusObjectivesPayASensibleAmount() {
        CampaignRegistry.MISSIONS.forEach { mission ->
            mission.bonusObjectives.forEach {
                assertTrue("${mission.id} has a bonus objective worth nothing", it.gloryReward > 0)
            }
        }
    }

    @Test
    fun surviveMissionsDeadlineDoesNotPreemptTheirObjective() {
        // A survival objective must be reachable within the mission's own deadline. Under ALL every
        // objective has to fit; under ANY it is enough that one route does, so a long survival goal
        // beside a short alternative is legitimate.
        CampaignRegistry.MISSIONS
            .filter { it.turnLimit > 0 }
            .forEach { mission ->
                val survivals = mission.objectives.filter { it.type == CampaignObjectiveType.SURVIVE_TURNS }
                if (survivals.isEmpty()) return@forEach
                val reachable = when (mission.objectiveMode) {
                    ObjectiveMode.ALL -> survivals.all { mission.turnLimit >= it.targetValue }
                    ObjectiveMode.ANY -> mission.objectives.any {
                        it.type != CampaignObjectiveType.SURVIVE_TURNS || mission.turnLimit >= it.targetValue
                    }
                }
                assertTrue(
                    "${mission.id}: deadline ${mission.turnLimit} leaves no reachable route",
                    reachable
                )
            }
    }
}
