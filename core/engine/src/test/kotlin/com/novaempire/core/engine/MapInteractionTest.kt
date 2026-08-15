package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Test

class MapInteractionTest {

    private val origin = HexCoord(0, 0, 0)
    private val east = HexCoord(1, 0, -1)
    private val farEast = HexCoord(3, 0, -3)

    /** A 4-radius board of empty space, fully explored by the active faction. */
    private fun board(
        units: Map<HexCoord, GameUnit> = emptyMap(),
        planets: Map<HexCoord, HexTile> = emptyMap(),
        explored: Set<HexCoord>? = null
    ): GameState {
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -4..4) {
            for (r in maxOf(-4, -q - 4)..minOf(4, -q + 4)) {
                val coord = HexCoord(q, r, -q - r)
                tiles[coord] = planets[coord] ?: HexTile(coord, TerrainType.EMPTY)
            }
        }
        return GameState(
            map = GameMap(tiles = tiles, radius = 4),
            units = units,
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(
                    faction = Faction.DOMINION,
                    exploredHexes = explored ?: tiles.keys.toSet()
                )
            )
        )
    }

    private fun unit(
        type: UnitType,
        faction: Faction,
        at: HexCoord,
        hasMoved: Boolean = false,
        hasAttacked: Boolean = false
    ) = GameUnit(
        type = type, faction = faction, position = at, currentHp = type.maxHp,
        hasMoved = hasMoved, hasAttacked = hasAttacked
    )

    private fun planet(at: HexCoord, owner: Faction?, level: Int) =
        HexTile(at, TerrainType.PLANET, owner = owner, systemLevel = level)

    // ── selection basics ────────────────────────────────────────────────────

    @Test
    fun aCoordinateOffTheBoardIsRejected() {
        assertEquals(
            MapAction.OutsideMap,
            MapInteraction.activate(board(), null, HexCoord(40, 0, -40), emptySet())
        )
    }

    @Test
    fun aHexUnderFogIsNotActedOn() {
        val state = board(explored = setOf(origin))
        assertEquals(
            MapAction.Unexplored,
            MapInteraction.activate(state, null, east, emptySet())
        )
    }

    @Test
    fun activatingTheSelectedHexAgainDeselects() {
        assertEquals(
            MapAction.Deselect,
            MapInteraction.activate(board(), origin, origin, emptySet())
        )
    }

    @Test
    fun withNothingSelectedAnyHexIsJustSelected() {
        assertEquals(
            MapAction.Select(east),
            MapInteraction.activate(board(), null, east, emptySet())
        )
    }

    @Test
    fun anEnemyFleetSelectedFirstGivesNoOrders() {
        // Selecting an enemy ship shows its stats; the next activation must not command it.
        val state = board(units = mapOf(origin to unit(UnitType.CRUISER, Faction.XYLAR, origin)))
        assertEquals(
            MapAction.Select(east),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    // ── movement ────────────────────────────────────────────────────────────

    @Test
    fun anEmptyHexInRangeOrdersAMove() {
        val state = board(units = mapOf(origin to unit(UnitType.SCOUT, Faction.DOMINION, origin)))
        assertEquals(
            MapAction.Move(origin, east),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anEmptyHexOutOfRangeReselectsInsteadOfFiringADoomedOrder() {
        val state = board(units = mapOf(origin to unit(UnitType.SCOUT, Faction.DOMINION, origin)))
        assertEquals(
            MapAction.Select(farEast),
            MapInteraction.activate(state, origin, farEast, reachable = setOf(east))
        )
    }

    @Test
    fun aFleetThatHasAlreadyMovedCannotMoveAgain() {
        val state = board(
            units = mapOf(origin to unit(UnitType.SCOUT, Faction.DOMINION, origin, hasMoved = true))
        )
        assertEquals(
            MapAction.Select(east),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    // ── combat ──────────────────────────────────────────────────────────────

    @Test
    fun anEnemyWithinWeaponRangeOpensTheCombatPreview() {
        val state = board(
            units = mapOf(
                origin to unit(UnitType.CRUISER, Faction.DOMINION, origin),
                east to unit(UnitType.SCOUT, Faction.XYLAR, east)
            )
        )
        assertEquals(
            MapAction.PreviewCombat(origin, east),
            MapInteraction.activate(state, origin, east, emptySet())
        )
    }

    @Test
    fun anEnemyBeyondWeaponRangeIsOnlySelected() {
        val attacker = unit(UnitType.CRUISER, Faction.DOMINION, origin)
        val state = board(
            units = mapOf(
                origin to attacker,
                farEast to unit(UnitType.SCOUT, Faction.XYLAR, farEast)
            )
        )
        // Guard the premise: this test is meaningless if a CRUISER can already reach that far.
        assertEquals(true, origin.distanceTo(farEast) > attacker.type.range)
        assertEquals(
            MapAction.Select(farEast),
            MapInteraction.activate(state, origin, farEast, emptySet())
        )
    }

    @Test
    fun aFleetThatHasFiredCannotAttackAgain() {
        val state = board(
            units = mapOf(
                origin to unit(UnitType.CRUISER, Faction.DOMINION, origin, hasAttacked = true),
                east to unit(UnitType.SCOUT, Faction.XYLAR, east)
            )
        )
        assertEquals(
            MapAction.Select(east),
            MapInteraction.activate(state, origin, east, emptySet())
        )
    }

    @Test
    fun afriendlyFleetIsSelectedRatherThanAttacked() {
        val state = board(
            units = mapOf(
                origin to unit(UnitType.CRUISER, Faction.DOMINION, origin),
                east to unit(UnitType.SCOUT, Faction.DOMINION, east)
            )
        )
        assertEquals(
            MapAction.Select(east),
            MapInteraction.activate(state, origin, east, emptySet())
        )
    }

    // ── planets ─────────────────────────────────────────────────────────────

    @Test
    fun anAdjacentUndefendedEnemyWorldIsCaptured() {
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(east to planet(east, Faction.XYLAR, level = 0))
        )
        assertEquals(
            MapAction.PreviewSiege(origin, east, isCapture = true),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anAdjacentDefendedEnemyWorldIsBesieged() {
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(east to planet(east, Faction.XYLAR, level = 3))
        )
        assertEquals(
            MapAction.PreviewSiege(origin, east, isCapture = false),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anAdjacentNeutralUndefendedWorldIsCaptured() {
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(east to planet(east, owner = null, level = 0))
        )
        assertEquals(
            MapAction.PreviewSiege(origin, east, isCapture = true),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anAdjacentNeutralFortressIsBesieged() {
        // MapFactory seeds unowned planets at level 2-4, so this is the common case on any map —
        // and the one the tap handler used to answer with "fly onto it" while the highlight,
        // the side-panel button and the reducer all offered a siege.
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(east to planet(east, owner = null, level = 4))
        )
        assertEquals(
            MapAction.PreviewSiege(origin, east, isCapture = false),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anOwnWorldIsMovedOntoRatherThanBesieged() {
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(east to planet(east, Faction.DOMINION, level = 2))
        )
        assertEquals(
            MapAction.Move(origin, east),
            MapInteraction.activate(state, origin, east, setOf(east))
        )
    }

    @Test
    fun anEnemyWorldTwoHexesAwayIsOnlySelected() {
        val far = HexCoord(2, 0, -2)
        val state = board(
            units = mapOf(origin to unit(UnitType.CRUISER, Faction.DOMINION, origin)),
            planets = mapOf(far to planet(far, Faction.XYLAR, level = 1))
        )
        assertEquals(
            MapAction.Select(far),
            MapInteraction.activate(state, origin, far, reachable = emptySet())
        )
    }
}
