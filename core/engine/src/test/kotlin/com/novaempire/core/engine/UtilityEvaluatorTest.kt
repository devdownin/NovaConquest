package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.TechBranch
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityEvaluatorTest {

    private val engine = GameEngine()
    private val reduce: (GameState, GameIntent) -> GameState = { s, i -> engine.reduce(s, i).newState }

    /** XYLAR (ai) at war with TRADERS. Helper to wire mutual WAR relations. */
    private fun warState(
        activeFaction: Faction,
        aiCredits: Int,
        tiles: Map<HexCoord, HexTile>,
        units: Map<HexCoord, GameUnit>,
    ): GameState = GameState(
        activeFaction = activeFaction,
        humanFaction = Faction.DOMINION,
        playerStates = mapOf(
            activeFaction to PlayerState(activeFaction, credits = aiCredits, relations = mapOf(Faction.TRADERS to DiplomaticRelation.WAR)),
            Faction.TRADERS to PlayerState(Faction.TRADERS, credits = 5, relations = mapOf(activeFaction to DiplomaticRelation.WAR))
        ),
        map = GameMap(tiles = tiles),
        units = units
    )

    private fun line(vararg coords: HexCoord): Map<HexCoord, HexTile> =
        coords.associateWith { HexTile(it, TerrainType.EMPTY) }

    @Test
    fun capturesAdjacentLevelZeroPlanet() = runBlocking {
        val u = HexCoord(0, 0, 0)
        val p = HexCoord(1, -1, 0)
        val tiles = mapOf(
            u to HexTile(u, TerrainType.EMPTY),
            p to HexTile(p, TerrainType.PLANET, systemLevel = 0, owner = Faction.TRADERS)
        )
        val state = warState(
            Faction.XYLAR, aiCredits = 50, tiles = tiles,
            units = mapOf(u to GameUnit(type = UnitType.CRUISER, faction = Faction.XYLAR, position = u, currentHp = 25))
        )
        val result = UtilityEvaluator.executeAITurn(state, Faction.XYLAR, reduce)
        assertEquals("AI should capture the adjacent level-0 planet", Faction.XYLAR, result.map.tiles[p]?.owner)
    }

    @Test
    fun attacksEnemyUnitInRange() = runBlocking {
        val u = HexCoord(0, 0, 0)
        val e = HexCoord(1, -1, 0)
        val tiles = mapOf(u to HexTile(u, TerrainType.EMPTY), e to HexTile(e, TerrainType.EMPTY))
        val state = warState(
            Faction.XYLAR, aiCredits = 50, tiles = tiles,
            units = mapOf(
                u to GameUnit(type = UnitType.CRUISER, faction = Faction.XYLAR, position = u, currentHp = 25),
                e to GameUnit(type = UnitType.SCOUT, faction = Faction.TRADERS, position = e, currentHp = UnitType.SCOUT.maxHp)
            )
        )
        val result = UtilityEvaluator.executeAITurn(state, Faction.XYLAR, reduce)
        // The AI must engage the in-range enemy. Combat damage is randomised (variance 0.8–1.2),
        // so a ~7-dmg Cruiser doesn't *always* one-shot a 6-HP Scout — assert the scout was
        // attacked (destroyed or at least damaged) rather than relying on the roll.
        val scout = result.units.values.firstOrNull { it.faction == Faction.TRADERS }
        assertTrue(
            "AI should have attacked the in-range enemy scout (destroyed or damaged)",
            scout == null || scout.currentHp < UnitType.SCOUT.maxHp
        )
    }

    @Test
    fun aiAtWarPrefersMilitaryResearch() {
        val ps = PlayerState(Faction.XYLAR, credits = 100,
            relations = mapOf(Faction.TRADERS to DiplomaticRelation.WAR))
        val state = GameState(activeFaction = Faction.XYLAR, playerStates = mapOf(Faction.XYLAR to ps))
        assertEquals(TechBranch.MILITARY, UtilityEvaluator.chooseResearchTech(state, ps)?.branch)
    }

    @Test
    fun aiAtPeacePrefersExpansionResearch() {
        val ps = PlayerState(Faction.XYLAR, credits = 100) // no WAR relations
        val state = GameState(activeFaction = Faction.XYLAR, playerStates = mapOf(Faction.XYLAR to ps))
        assertEquals(TechBranch.EXPANSION, UtilityEvaluator.chooseResearchTech(state, ps)?.branch)
    }

    // ── Hero recruitment (H5) ─────────────────────────────────────────────────

    private fun heroState(faction: Faction, atWar: Boolean, units: Map<HexCoord, GameUnit> = emptyMap()): Pair<GameState, PlayerState> {
        val ps = PlayerState(
            faction, credits = 500,
            relations = if (atWar) mapOf(Faction.KAELEN to DiplomaticRelation.WAR) else emptyMap()
        )
        return GameState(activeFaction = faction, playerStates = mapOf(faction to ps), units = units) to ps
    }

    @Test
    fun aiPrefersHeroMatchingItsAffinity() {
        // Vance's targetFaction is DOMINION → affinity outweighs the peacetime economy pick.
        val (state, ps) = heroState(Faction.DOMINION, atWar = false)
        assertEquals(HeroRegistry.VANCE, UtilityEvaluator.chooseHero(state, ps)?.id)
    }

    @Test
    fun aiAtWarWithoutAffinityRecruitsCombatHero() {
        // XYLAR has no affinity hero → posture decides: at war, damage wins.
        val (state, ps) = heroState(Faction.XYLAR, atWar = true)
        assertEquals(HeroRegistry.VANCE, UtilityEvaluator.chooseHero(state, ps)?.id)
    }

    @Test
    fun aiAtPeaceWithoutAffinityRecruitsEconomyHero() {
        val (state, ps) = heroState(Faction.XYLAR, atWar = false)
        assertEquals(HeroRegistry.ELARA, UtilityEvaluator.chooseHero(state, ps)?.id)
    }

    @Test
    fun aiWithWoundedFleetPrefersHealer() {
        val pos = HexCoord(0, 0, 0)
        val wounded = GameUnit(type = UnitType.CRUISER, faction = Faction.XYLAR, position = pos, currentHp = 5)
        val (state, ps) = heroState(Faction.XYLAR, atWar = false, units = mapOf(pos to wounded))
        assertEquals(HeroRegistry.NIX, UtilityEvaluator.chooseHero(state, ps)?.id)
    }

    @Test
    fun aiRecruitsNothingWhenBroke() {
        val ps = PlayerState(Faction.XYLAR, credits = 0)
        val state = GameState(activeFaction = Faction.XYLAR, playerStates = mapOf(Faction.XYLAR to ps))
        assertNull("No hero is affordable with 0 credits", UtilityEvaluator.chooseHero(state, ps))
    }

    @Test
    fun advancesTowardDistantObjectiveEvenBeyondMovement() = runBlocking {
        // DREADNOUGHT (movement 1 + XYLAR +1 = 2) four hexes from an enemy planet must still close in.
        val start = HexCoord(0, 0, 0)
        val planet = HexCoord(4, -4, 0)
        val tiles = line(
            HexCoord(0, 0, 0), HexCoord(1, -1, 0), HexCoord(2, -2, 0), HexCoord(3, -3, 0)
        ) + mapOf(planet to HexTile(planet, TerrainType.PLANET, systemLevel = 3, owner = Faction.TRADERS))
        val state = warState(
            Faction.XYLAR, aiCredits = 50, tiles = tiles,
            units = mapOf(start to GameUnit(type = UnitType.DREADNOUGHT, faction = Faction.XYLAR, position = start, currentHp = UnitType.DREADNOUGHT.maxHp))
        )
        val result = UtilityEvaluator.executeAITurn(state, Faction.XYLAR, reduce)
        val moved = result.units.values.first { it.faction == Faction.XYLAR }
        assertTrue("Unit should have advanced closer to the objective",
            moved.position.distanceTo(planet) < start.distanceTo(planet))
    }
}
