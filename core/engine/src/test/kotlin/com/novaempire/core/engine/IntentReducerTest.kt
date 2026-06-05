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
    fun researchTechUnlocksAndDeductsCredits() = runBlocking {
        val e = engine()
        val creditsBefore = e.state.value.playerStates[Faction.DOMINION]!!.credits
        // tech_hull_plating: tier-1 military, no prerequisite, base cost 4
        e.processIntent(GameIntent.ResearchTech("tech_hull_plating"))
        delay(100)
        val player = e.state.value.playerStates[Faction.DOMINION]!!
        assertTrue(player.techUnlocked.contains("tech_hull_plating"))
        assertEquals(creditsBefore - 4, player.credits)
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
    fun buildUnitSpawnsAndDeductsCredits() = runBlocking {
        val e = engine()
        val before = e.state.value
        val creditsBefore = before.playerStates[Faction.DOMINION]!!.credits
        val unitsBefore = before.units.size
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, null))
        delay(100)
        val after = e.state.value
        assertEquals(unitsBefore + 1, after.units.size)
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
        val unitsBefore = e.state.value.units.size
        e.processIntent(GameIntent.BuildUnit(UnitType.SCOUT, null))
        delay(100)
        assertEquals(unitsBefore, e.state.value.units.size)
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
}
