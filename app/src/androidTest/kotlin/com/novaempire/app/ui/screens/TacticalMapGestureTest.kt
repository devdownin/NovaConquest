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

/**
 * Gestes de la carte tactique — sur appareil, pas sous Robolectric.
 *
 * Ces mêmes tests ont d'abord été écrits dans le jeu de tests unitaires, où ils échouaient sur
 * « Failed to inject touch input » et « Failed to perform RequestFocus action » : Robolectric
 * rend bien l'arbre de sémantique, mais n'assure pas l'injection d'entrées ni le focus fenêtre.
 * Les assertions de sémantique restent donc dans le jeu de tests unitaires, où elles gardent
 * chaque poussée ; ce qui exige de vrais événements vit ici.
 *
 * Contrepartie assumée : le job instrumenté ne tourne que sur la branche principale. Sur les
 * autres branches, seule la *compilation* de ces tests est vérifiée, par une étape dédiée. Les
 * *règles* qu'ils exercent, elles, restent couvertes en permanence par MapInteractionTest.
 */
@OptIn(ExperimentalTestApi::class)
class TacticalMapGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val origin = HexCoord(0, 0, 0)
    private val east = HexCoord(1, 0, -1)
    private val west = HexCoord(-1, 0, 1)

    private fun testState(capital: HexCoord = east): GameState {
        val tiles = mutableMapOf<HexCoord, HexTile>()
        for (q in -2..2) {
            for (r in maxOf(-2, -q - 2)..minOf(2, -q + 2)) {
                val coord = HexCoord(q, r, -q - r)
                tiles[coord] = HexTile(coord, TerrainType.EMPTY)
            }
        }
        tiles[east] = HexTile(east, TerrainType.PLANET, owner = Faction.DOMINION, systemLevel = 2)
        val explored = tiles.keys.toSet()
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
                // Caméra en identité : le centre du nœud est alors exactement l'hexagone (0,0,0),
                // donc un tap se vise avec  seul, sans hypothèse de densité.
                camera = MapCameraState().apply { initializeOnce(1f, Offset.Zero) }
            )
        }
    }

    private fun map() = rule.onNodeWithContentDescription("Secteur", substring = true)

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
}
