package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        // A Cruiser (~7 dmg) one-shots a 6-HP Scout.
        assertTrue("Enemy scout should be destroyed", result.units.values.none { it.faction == Faction.TRADERS })
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
