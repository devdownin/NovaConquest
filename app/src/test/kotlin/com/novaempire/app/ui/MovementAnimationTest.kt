package com.novaempire.app.ui

import androidx.compose.ui.geometry.Offset
import com.novaempire.app.ui.components.pointAlongPath
import com.novaempire.app.ui.screens.visiblePathSuffix
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux règles de l'animation de déplacement qui tiennent sans Compose : où se trouve une flotte
 * à un instant donné de son trajet, et quelle part de ce trajet le joueur a le droit de voir.
 *
 * Elles sont testées ici plutôt qu'à travers l'écran parce qu'aucune des deux n'a besoin d'une
 * composition — et parce que la seconde est une règle de brouillard de guerre, c'est-à-dire le genre
 * de chose qu'on ne veut pas découvrir cassée en jouant.
 */
class MovementAnimationTest {

    private val origin = HexCoord(0, 0, 0)
    private val east = HexCoord(1, 0, -1)
    private val farEast = HexCoord(2, 0, -2)
    private val farthestEast = HexCoord(3, 0, -3)

    // ── pointAlongPath ────────────────────────────────────────────────────────

    @Test
    fun endpointsAreExact() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f))
        assertEquals(Offset(0f, 0f), pointAlongPath(points, 0f))
        assertEquals(Offset(10f, 10f), pointAlongPath(points, 1f))
    }

    @Test
    fun midpointOfATwoSegmentPathIsTheJoint() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f))
        assertEquals(Offset(10f, 0f), pointAlongPath(points, 0.5f))
    }

    @Test
    fun progressIsSpreadPerSegment() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f))
        // 0,25 = moitié du premier segment, 0,75 = moitié du second.
        assertEquals(Offset(5f, 0f), pointAlongPath(points, 0.25f))
        assertEquals(Offset(10f, 5f), pointAlongPath(points, 0.75f))
    }

    @Test
    fun outOfRangeFractionIsClampedRatherThanThrowing() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f))
        // Un `Animatable` peut dépasser légèrement sa cible (ressort, reprise après annulation) :
        // extrapoler enverrait la flotte au-delà de son hex d'arrivée.
        assertEquals(Offset(0f, 0f), pointAlongPath(points, -0.5f))
        assertEquals(Offset(10f, 0f), pointAlongPath(points, 1.5f))
    }

    @Test
    fun emptyOrSinglePointPathDoesNotBreakRendering() {
        assertEquals(Offset.Zero, pointAlongPath(emptyList(), 0.5f))
        assertEquals(Offset(3f, 4f), pointAlongPath(listOf(Offset(3f, 4f)), 0.5f))
    }

    // ── visiblePathSuffix ─────────────────────────────────────────────────────

    @Test
    fun aFullyVisiblePathIsKeptWhole() {
        val path = listOf(origin, east, farEast)
        assertEquals(path, visiblePathSuffix(path, setOf(origin, east, farEast)))
    }

    @Test
    fun originOutOfSightIsTrimmedAway() {
        val path = listOf(origin, east, farEast, farthestEast)
        // Le joueur ne voit que les deux derniers hexs : la flotte doit sembler surgir en `farEast`,
        // pas venir de `origin` — sinon l'animation dit d'où vient l'ennemi.
        val shown = visiblePathSuffix(path, setOf(farEast, farthestEast))
        assertEquals(listOf(farEast, farthestEast), shown)
    }

    @Test
    fun aVisibilityGapDoesNotReopenTheStartOfThePath() {
        val path = listOf(origin, east, farEast, farthestEast)
        // `origin` est visible mais `east` ne l'est pas : reprendre au début tracerait un segment
        // au travers d'un hex non observé.
        val shown = visiblePathSuffix(path, setOf(origin, farEast, farthestEast))
        assertEquals(listOf(farEast, farthestEast), shown)
    }

    @Test
    fun anIsolatedArrivalLeavesNothingToAnimate() {
        val path = listOf(origin, east, farEast)
        val shown = visiblePathSuffix(path, setOf(farEast))
        // Un seul point : l'appelant s'en sert pour renoncer à l'animation et laisser la couche
        // statique dessiner la flotte à l'arrivée.
        assertEquals(listOf(farEast), shown)
        assertTrue(shown.size < 2)
    }
}
