package com.novaempire.app.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TacticalMapScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val origin = HexCoord(0, 0, 0)
    private val east = HexCoord(1, 0, -1)
    private val west = HexCoord(-1, 0, 1)
    private val unexplored = HexCoord(2, 0, -2)

    private fun testState(capital: HexCoord = east): GameState {
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
                    capitalCoord = capital,
                    exploredHexes = explored,
                    visibleHexes = explored
                )
            )
        )
    }

    private fun setMap(
        state: GameState,
        cursor: HexCoord?,
        canUndo: Boolean = false,
        isAiThinking: Boolean = false,
        onHexClick: (HexCoord) -> Unit = {},
        onMoveUnit: (HexCoord, HexCoord) -> Unit = { _, _ -> },
        onUndo: () -> Unit = {}
    ) {
        rule.setContent {
            TacticalMapScreen(
                canUndo = canUndo,
                isAiThinking = isAiThinking,
                gameState = state,
                visibleHexes = state.playerStates[state.activeFaction]?.visibleHexes ?: emptySet(),
                onHexClick = onHexClick,
                onMoveUnit = onMoveUnit,
                onAttackUnit = { _, _ -> },
                onOpenSystemManagement = {},
                onSiegePlanet = { _, _ -> },
                onCapturePlanet = { _, _ -> },
                onOpenAcademy = {},
                onClearSelection = {},
                onUndo = onUndo,
                initialSelectedHex = cursor,
                // Identity camera: the centre of the map node is then exactly hex (0,0,0), so a
                // tap can be aimed with `center` alone — no density or dp-to-px assumption.
                camera = MapCameraState().apply { initializeOnce(1f, Offset.Zero) }
            )
        }
    }

    private fun map() = rule.onNodeWithContentDescription("Secteur", substring = true)

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

    // ── wiring: the paths the pure MapInteraction suite cannot see ──────────
    //
    // The selection *rules* are covered by MapInteractionTest (18 cases) and the pixel-to-hex
    // maths by HexLayoutTest. What is only observable here is whether a real gesture reaches
    // them at all — the gesture detectors, the key handler, the callbacks.

    @Test
    fun tappingTheMapSelectsTheHexUnderTheFinger() {
        var clicked: HexCoord? = null
        setMap(testState(), cursor = null, onHexClick = { clicked = it })

        // With the identity camera the node's centre is hex (0,0,0), and nothing is selected yet
        // so no side panel covers it.
        map().performTouchInput { click(center) }

        assertEquals(origin, clicked)
    }

    @Test
    fun tapsAreIgnoredWhileTheAiIsPlaying() {
        var clicked: HexCoord? = null
        setMap(testState(), cursor = null, isAiThinking = true, onHexClick = { clicked = it })

        map().performTouchInput { click(center) }

        // The reducer would refuse the intent anyway; letting the tap through only produced a
        // queue of "AI is thinking" snackbars.
        assertNull(clicked)
    }

    @Test
    fun anArrowKeyWalksTheCursorToTheNeighbouringHex() {
        setMap(testState(capital = origin), cursor = null)
        val node = map()
        node.requestFocus()

        // The first press only lands the cursor on the capital…
        node.performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithContentDescription("Secteur 0, 0", substring = true).assertExists()

        // …the second actually steps west.
        node.performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithContentDescription("Secteur -1, 0", substring = true).assertExists()
    }

    @Test
    fun enterSelectsTheFleetUnderTheCursor() {
        var clicked: HexCoord? = null
        setMap(testState(capital = origin), cursor = null, onHexClick = { clicked = it })
        val node = map()
        node.requestFocus()

        node.performKeyInput { pressKey(Key.DirectionLeft) } // land on the fleet
        node.performKeyInput { pressKey(Key.Enter) }

        assertEquals(origin, clicked)
    }

    @Test
    fun enterOnAReachableHexOrdersTheMove() {
        var moved: Pair<HexCoord, HexCoord>? = null
        setMap(
            testState(capital = origin), cursor = null,
            onMoveUnit = { from, to -> moved = from to to }
        )
        val node = map()
        node.requestFocus()

        node.performKeyInput { pressKey(Key.DirectionLeft) } // land on the fleet
        node.performKeyInput { pressKey(Key.Enter) }         // select it
        node.performKeyInput { pressKey(Key.DirectionLeft) } // cursor one hex west
        node.performKeyInput { pressKey(Key.Enter) }         // act on it

        assertEquals(origin to west, moved)
    }

    @Test
    fun theUndoControlDispatchesWhenPressed() {
        var undone = false
        setMap(testState(), cursor = origin, canUndo = true, onUndo = { undone = true })

        rule.onNodeWithContentDescription("Annuler la dernière action").performClick()

        assertEquals(true, undone)
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
