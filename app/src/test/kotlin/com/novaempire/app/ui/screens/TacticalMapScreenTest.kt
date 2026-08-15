package com.novaempire.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screen-level tests for the tactical map, run on the JVM through Robolectric.
 *
 * `:app` had no test source set at all: every UI change was gated by "does it compile" and
 * nothing more. These cover the wiring that the pure [com.novaempire.core.engine.MapInteraction]
 * suite cannot see — that the screen actually composes, and that it exposes the accessibility
 * contract a screen-reader user depends on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TacticalMapScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val origin = HexCoord(0, 0, 0)
    private val east = HexCoord(1, 0, -1)
    private val unexplored = HexCoord(2, 0, -2)

    private fun testState(): GameState {
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -2..2) {
            for (r in maxOf(-2, -q - 2)..minOf(2, -q + 2)) {
                val coord = HexCoord(q, r, -q - r)
                tiles[coord] = HexTile(coord, TerrainType.EMPTY)
            }
        }
        tiles[east] = HexTile(east, TerrainType.PLANET, owner = Faction.DOMINION, systemLevel = 2)
        val explored = tiles.keys - unexplored
        return GameState(
            map = GameMap(tiles = tiles, radius = 2),
            units = mapOf(
                origin to GameUnit(
                    type = UnitType.CRUISER, faction = Faction.DOMINION,
                    position = origin, currentHp = UnitType.CRUISER.maxHp
                )
            ),
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(
                    faction = Faction.DOMINION,
                    capitalCoord = east,
                    exploredHexes = explored,
                    visibleHexes = explored
                )
            )
        )
    }

    private fun setMap(state: GameState, cursor: HexCoord?, canUndo: Boolean = false) {
        rule.setContent {
            TacticalMapScreen(
                canUndo = canUndo,
                gameState = state,
                visibleHexes = state.playerStates[state.activeFaction]?.visibleHexes ?: emptySet(),
                onHexClick = {},
                onMoveUnit = { _, _ -> },
                onAttackUnit = { _, _ -> },
                onOpenSystemManagement = {},
                onSiegePlanet = { _, _ -> },
                onCapturePlanet = { _, _ -> },
                onOpenAcademy = {},
                onClearSelection = {},
                initialSelectedHex = cursor
            )
        }
    }

    @Test
    fun theMapComposesAndAnnouncesTheHexUnderTheCursor() {
        setMap(testState(), origin)
        rule.onNodeWithContentDescription("Secteur 0, 0", substring = true).assertExists()
    }

    @Test
    fun theAnnouncementNamesTheTerrainAndTheFleet() {
        setMap(testState(), origin)
        rule.onNodeWithContentDescription("espace vide", substring = true).assertExists()
        rule.onNodeWithContentDescription("CRUISER", substring = true).assertExists()
    }

    @Test
    fun theAnnouncementSaysWhatEnterWouldDo() {
        setMap(testState(), origin)
        rule.onNodeWithContentDescription("Entrée pour", substring = true).assertExists()
    }

    @Test
    fun aHexUnderFogIsAnnouncedAsUnexplored() {
        // Fog must not leak through the accessibility layer: what the eye can't see, TalkBack
        // must not read out either.
        setMap(testState(), unexplored)
        rule.onNodeWithContentDescription("inexploré", substring = true).assertExists()
    }

    @Test
    fun withNoCursorTheMapStillAnnouncesHowToDriveIt() {
        setMap(testState(), null)
        rule.onNodeWithContentDescription("Carte tactique", substring = true).assertExists()
    }

    @Test
    fun theUndoControlStatesWhetherThereIsAnythingToTakeBack() {
        setMap(testState(), origin, canUndo = false)
        rule.onNodeWithContentDescription("Rien à annuler").assertExists()
    }

    @Test
    fun theUndoControlOffersTheActionOnceThereIsHistory() {
        setMap(testState(), origin, canUndo = true)
        rule.onNodeWithContentDescription("Annuler la dernière action").assertExists()
    }
}
