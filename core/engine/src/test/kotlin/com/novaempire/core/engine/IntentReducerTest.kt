package com.novaempire.core.engine

import com.novaempire.core.domain.models.*
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IntentReducerTest {

    private fun engine() = GameEngine(NoOpAI())

    /** Minimal state: DOMINION unit at (0,0,0) with an enemy planet at (1,-1,0). */
    private fun stateWithAdjacentPlanet(planetLevel: Int, planetOwner: Faction?): GameState {
        val unitCoord = HexCoord(0, 0, 0)
        val planetCoord = HexCoord(1, -1, 0)
        return GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 100, capitalCoord = unitCoord)
            ),
            map = GameMap(
                tiles = mapOf(
                    unitCoord to HexTile(unitCoord, TerrainType.EMPTY),
                    planetCoord to HexTile(planetCoord, TerrainType.PLANET, planetLevel, planetOwner)
                )
            ),
            units = mapOf(
                unitCoord to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = unitCoord, currentHp = 25)
            )
        )
    }

    // ── ResearchTech ──────────────────────────────────────────────────────────

    @Test
    fun researchTechQueuesAndDeductsCredits() = runBlocking {
        val e = engine()
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits
        // tech_hull_plating: tier-1 military, no prerequisite, base cost 8
        e.processIntent(GameIntent.ResearchTech("tech_hull_plating"))
        delay(100)
        val player = e.state.value.playerStates[Faction.DOMINION]!!
        // ResearchTech queues the tech into researchInProgress; credits are deducted immediately
        assertEquals("tech_hull_plating", player.researchInProgress?.techId)
        assertEquals(creditsBefore - 8, player.credits)
    }

    @Test
    fun cancelResearchRefundsHalfAndClearsQueue() = runBlocking {
        val e = engine()
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits
        e.processIntent(GameIntent.ResearchTech("tech_hull_plating")) // base cost 8
        delay(100)
        assertEquals(creditsBefore - 8, e.state.value.playerStates[Faction.DOMINION]!!.credits)

        e.processIntent(GameIntent.CancelResearch)
        delay(100)
        val player = e.state.value.playerStates[Faction.DOMINION]!!
        assertNull("Cancelling clears the research queue", player.researchInProgress)
        // Half of 8 refunded → net spend of 4.
        assertEquals(creditsBefore - 4, player.credits)
    }

    @Test
    fun cancelResearchWithoutQueueIsNoOp() = runBlocking {
        val e = engine()
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits
        e.processIntent(GameIntent.CancelResearch)
        delay(100)
        val player = e.state.value.playerStates[Faction.DOMINION]!!
        assertNull(player.researchInProgress)
        assertEquals("No refund when nothing is being researched", creditsBefore, player.credits)
    }

    @Test
    fun researchTechFailsWithoutPrerequisite() = runBlocking {
        val e = engine()
        // tech_plasma_weapons requires tech_hull_plating
        e.processIntent(GameIntent.ResearchTech("tech_plasma_weapons"))
        delay(100)
        assertFalse(e.state.value.playerStates[Faction.DOMINION]!!.techUnlocked.contains("tech_plasma_weapons"))
    }

    // ── BuildUnit ─────────────────────────────────────────────────────────────

    @Test
    fun buildUnitQueuesAndDeductsCredits() = runBlocking {
        val e = engine()
        val before = e.state.value
        val creditsBefore = before.playerStates[Faction.DOMINION]!!.credits
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, null))
        delay(100)
        val after = e.state.value
        // BuildUnit enqueues the order; credits are deducted immediately
        val queue = after.playerStates[Faction.DOMINION]!!.buildQueue
        assertEquals(1, queue.size)
        assertEquals(UnitType.SCOUT, queue.first().unitType)
        assertEquals(creditsBefore - UnitType.SCOUT.cost, after.playerStates[Faction.DOMINION]!!.credits)
    }

    @Test
    fun buildUnitFailsWithInsufficientCredits() = runBlocking {
        val e = engine()
        val broke = e.state.value.let { s ->
            s.copy(playerStates = s.playerStates.toMutableMap().apply {
                this[Faction.DOMINION] = this[Faction.DOMINION]!!.copy(credits = 0)
            })
        }
        e.processIntent(GameIntent.LoadGame(broke))
        delay(50)
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, null))
        delay(100)
        // Rejected: buildQueue must stay empty when credits are insufficient
        assertTrue(e.state.value.playerStates[Faction.DOMINION]!!.buildQueue.isEmpty())
    }

    // ── ChangeRelation ────────────────────────────────────────────────────────

    @Test
    fun changeRelationIsSymmetric() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.ChangeRelation(Faction.TRADERS, DiplomaticRelation.WAR))
        delay(100)
        val state = e.state.value
        assertEquals(DiplomaticRelation.WAR, state.playerStates[Faction.DOMINION]!!.relations[Faction.TRADERS])
        assertEquals(DiplomaticRelation.WAR, state.playerStates[Faction.TRADERS]!!.relations[Faction.DOMINION])
    }

    // ── RecruitHero ───────────────────────────────────────────────────────────

    @Test
    fun recruitHeroDeductsCreditsAndAppearsInRoster() = runBlocking {
        val e = engine()
        // Inject 100 credits (VANCE costs 50)
        val rich = e.state.value.let { s ->
            s.copy(playerStates = s.playerStates.toMutableMap().apply {
                this[Faction.DOMINION] = this[Faction.DOMINION]!!.copy(credits = 100)
            })
        }
        e.processIntent(GameIntent.LoadGame(rich))
        delay(50)
        e.processIntent(GameIntent.RecruitHero(HeroRegistry.VANCE))
        delay(100)
        val player = e.state.value.playerStates[Faction.DOMINION]!!
        assertTrue(player.recruitedHeroes.contains(HeroRegistry.VANCE))
        assertEquals(100 - 50, player.credits)
    }

    @Test
    fun recruitHeroFailsWhenAlreadyRecruited() = runBlocking {
        val e = engine()
        val rich = e.state.value.let { s ->
            s.copy(playerStates = s.playerStates.toMutableMap().apply {
                this[Faction.DOMINION] = this[Faction.DOMINION]!!.copy(credits = 200)
            })
        }
        e.processIntent(GameIntent.LoadGame(rich))
        delay(50)
        e.processIntent(GameIntent.RecruitHero(HeroRegistry.VANCE))
        delay(100)
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits
        e.processIntent(GameIntent.RecruitHero(HeroRegistry.VANCE))
        delay(100)
        // Credits unchanged: second recruit ignored
        assertEquals(creditsBefore, e.state.value.playerStates[Faction.DOMINION]!!.credits)
    }

    @Test
    fun buildUnitRejectedOnAPlanetYouDoNotOwn() = runBlocking {
        // Regression: the reducer checked neither ownership nor that the target was a planet, so an
        // intent carrying any coordinate could queue production on an enemy world — and TurnManager
        // would then spawn the finished ship right there, inside their territory.
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 2, planetOwner = Faction.TRADERS)))
        delay(50)
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits

        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, HexCoord(1, -1, 0)))
        delay(100)

        val player = e.state.value.playerStates[Faction.DOMINION]!!
        assertTrue("no order may be queued on an enemy planet", player.buildQueue.isEmpty())
        assertEquals("credits must not be spent", creditsBefore, player.credits)
    }

    @Test
    fun buildUnitRejectedOnEmptySpace() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 2, planetOwner = Faction.TRADERS)))
        delay(50)
        // (0,0,0) holds the unit and is EMPTY terrain, not a planet.
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, HexCoord(0, 0, 0)))
        delay(100)
        assertTrue(e.state.value.playerStates[Faction.DOMINION]!!.buildQueue.isEmpty())
    }

    @Test
    fun buildUnitAcceptedOnAPlanetYouOwn() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 2, planetOwner = Faction.DOMINION)))
        delay(50)
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, HexCoord(1, -1, 0)))
        delay(100)
        val queue = e.state.value.playerStates[Faction.DOMINION]!!.buildQueue
        assertEquals(1, queue.size)
        assertEquals(UnitType.SCOUT, queue.first().unitType)
    }

    // ── Carrier transport ─────────────────────────────────────────────────────

    /** A CARRIER at (0,0,0) with a damaged FIGHTER escort alongside it at (1,-1,0). */
    private fun carrierWithWoundedEscort(escortHp: Int): GameState {
        val carrierCoord = HexCoord(0, 0, 0)
        val escortCoord = HexCoord(1, -1, 0)
        val deployCoord = HexCoord(0, -1, 1)
        return GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 100)),
            map = GameMap(tiles = listOf(carrierCoord, escortCoord, deployCoord)
                .associateWith { HexTile(it, TerrainType.EMPTY) }),
            units = mapOf(
                carrierCoord to GameUnit(type = UnitType.CARRIER, faction = Faction.DOMINION, position = carrierCoord, currentHp = UnitType.CARRIER.maxHp),
                escortCoord to GameUnit(type = UnitType.FIGHTER, faction = Faction.DOMINION, position = escortCoord, currentHp = escortHp)
            )
        )
    }

    @Test
    fun deployedUnitKeepsTheHpItHadWhenLoaded() = runBlocking {
        // Regression: cargo stored only the unit TYPE, so redeploying rebuilt it at full health —
        // a carrier worked as a free repair bay for nearly-destroyed escorts.
        val e = engine()
        e.processIntent(GameIntent.LoadGame(carrierWithWoundedEscort(escortHp = 3)))
        delay(50)
        val carrierCoord = HexCoord(0, 0, 0)
        e.processIntent(GameIntent.LoadUnit(carrierCoord, HexCoord(1, -1, 0)))
        delay(100)
        assertEquals(listOf(3), e.state.value.units[carrierCoord]!!.cargoHp)

        e.processIntent(GameIntent.DeployUnit(carrierCoord, HexCoord(0, -1, 1), 0))
        delay(100)
        val deployed = e.state.value.units[HexCoord(0, -1, 1)]!!
        assertEquals(UnitType.FIGHTER, deployed.type)
        assertEquals("the escort must come back as damaged as it went in", 3, deployed.currentHp)
        assertTrue("cargo is emptied on deploy", e.state.value.units[carrierCoord]!!.cargo.isEmpty())
        assertTrue(e.state.value.units[carrierCoord]!!.cargoHp.isEmpty())
    }

    // ── Hero abilities ────────────────────────────────────────────────────────

    @Test
    fun nixAbilityRepairsHalfTheHullNotAllOfIt() = runBlocking {
        // H6: Nix already grants a passive +1 HP/turn and, being the mercenary, is hirable by every
        // faction — a free full repair on top made losing a fleet fight nearly costless.
        val e = engine()
        val unitCoord = HexCoord(0, 0, 0)
        val wounded = GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = unitCoord, currentHp = 1)
        e.processIntent(GameIntent.LoadGame(GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(Faction.DOMINION to PlayerState(
                Faction.DOMINION, credits = 10, recruitedHeroes = setOf(HeroRegistry.NIX))),
            map = GameMap(tiles = mapOf(unitCoord to HexTile(unitCoord, TerrainType.EMPTY))),
            units = mapOf(unitCoord to wounded)
        )))
        delay(50)

        e.processIntent(GameIntent.UseHeroAbility(HeroRegistry.NIX))
        delay(100)

        val healed = e.state.value.units[unitCoord]!!
        val expected = 1 + (UnitType.CRUISER.maxHp + 1) / 2
        assertEquals(expected, healed.currentHp)
        assertTrue("the fleet must not be reset to full", healed.currentHp < UnitType.CRUISER.maxHp)
    }

    // ── SiegePlanet ───────────────────────────────────────────────────────────

    @Test
    fun siegePlanetReducesSystemLevel() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 3, planetOwner = Faction.TRADERS)))
        delay(50)
        val planetCoord = HexCoord(1, -1, 0)
        val levelBefore = e.state.value.map.tiles[planetCoord]!!.systemLevel
        e.processIntent(GameIntent.SiegePlanet(HexCoord(0, 0, 0), planetCoord))
        delay(100)
        // CRUISER deals 1 siege damage
        assertEquals(levelBefore - 1, e.state.value.map.tiles[planetCoord]!!.systemLevel)
    }

    // ── CapturePlanet ─────────────────────────────────────────────────────────

    @Test
    fun capturePlanetAtLevel0ChangesOwner() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 0, planetOwner = Faction.TRADERS)))
        delay(50)
        val unitCoord = HexCoord(0, 0, 0)
        val planetCoord = HexCoord(1, -1, 0)
        e.processIntent(GameIntent.CapturePlanet(unitCoord, planetCoord))
        delay(100)
        val tile = e.state.value.map.tiles[planetCoord]!!
        assertEquals(Faction.DOMINION, tile.owner)
        assertEquals(1, tile.systemLevel)
    }

    @Test
    fun capturePlanetFailsWhenLevelAboveZero() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithAdjacentPlanet(planetLevel = 2, planetOwner = Faction.TRADERS)))
        delay(50)
        val planetCoord = HexCoord(1, -1, 0)
        e.processIntent(GameIntent.CapturePlanet(HexCoord(0, 0, 0), planetCoord))
        delay(100)
        // Owner must be unchanged
        assertEquals(Faction.TRADERS, e.state.value.map.tiles[planetCoord]!!.owner)
    }

    // ── Adjacency & friendly-fire guards ───────────────────────────────────────

    /** DOMINION unit at (0,0,0) with an enemy planet placed [distance] hexes along the q-axis. */
    private fun stateWithPlanetAtDistance(distance: Int, planetLevel: Int, planetOwner: Faction?): GameState {
        val unitCoord = HexCoord(0, 0, 0)
        val planetCoord = HexCoord(distance, -distance, 0)
        return GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 100, capitalCoord = unitCoord)),
            map = GameMap(
                tiles = mapOf(
                    unitCoord to HexTile(unitCoord, TerrainType.EMPTY),
                    planetCoord to HexTile(planetCoord, TerrainType.PLANET, planetLevel, planetOwner)
                )
            ),
            units = mapOf(
                unitCoord to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = unitCoord, currentHp = 25)
            )
        )
    }

    @Test
    fun siegeFailsWhenNotAdjacent() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithPlanetAtDistance(distance = 2, planetLevel = 3, planetOwner = Faction.TRADERS)))
        delay(50)
        val planetCoord = HexCoord(2, -2, 0)
        e.processIntent(GameIntent.SiegePlanet(HexCoord(0, 0, 0), planetCoord))
        delay(100)
        // Level unchanged: siege rejected from 2 hexes away
        assertEquals(3, e.state.value.map.tiles[planetCoord]!!.systemLevel)
    }

    @Test
    fun captureFailsWhenNotAdjacent() = runBlocking {
        val e = engine()
        e.processIntent(GameIntent.LoadGame(stateWithPlanetAtDistance(distance = 2, planetLevel = 0, planetOwner = Faction.TRADERS)))
        delay(50)
        val planetCoord = HexCoord(2, -2, 0)
        e.processIntent(GameIntent.CapturePlanet(HexCoord(0, 0, 0), planetCoord))
        delay(100)
        // Owner unchanged: capture rejected from 2 hexes away
        assertEquals(Faction.TRADERS, e.state.value.map.tiles[planetCoord]!!.owner)
    }

    @Test
    fun startCampaignPutsScriptedEnemyAtWar() = runBlocking {
        val e = engine()
        // mission_1: player DOMINION vs enemy XYLAR
        e.processIntent(GameIntent.StartCampaign("mission_1"))
        delay(100)
        val s = e.state.value
        assertEquals("mission_1", s.campaignState.activeMissionId)
        assertEquals(DiplomaticRelation.WAR, s.playerStates[Faction.DOMINION]!!.relations[Faction.XYLAR])
        assertEquals(DiplomaticRelation.WAR, s.playerStates[Faction.XYLAR]!!.relations[Faction.DOMINION])
    }

    @Test
    fun attackFailsOnOwnUnit() = runBlocking {
        val e = engine()
        val a = HexCoord(0, 0, 0)
        val b = HexCoord(1, -1, 0)
        val friendly = GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 100)),
            map = GameMap(tiles = mapOf(a to HexTile(a, TerrainType.EMPTY), b to HexTile(b, TerrainType.EMPTY))),
            units = mapOf(
                a to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = a, currentHp = 25),
                b to GameUnit(type = UnitType.FIGHTER, faction = Faction.DOMINION, position = b, currentHp = UnitType.FIGHTER.maxHp)
            )
        )
        e.processIntent(GameIntent.LoadGame(friendly))
        delay(50)
        e.processIntent(GameIntent.AttackUnit(a, b))
        delay(100)
        // Friendly unit untouched and the attacker's action was not consumed
        assertEquals(UnitType.FIGHTER.maxHp, e.state.value.units[b]?.currentHp)
        assertEquals(false, e.state.value.units[a]?.hasAttacked)
    }
}
