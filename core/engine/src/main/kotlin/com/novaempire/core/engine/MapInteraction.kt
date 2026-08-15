package com.novaempire.core.engine

import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

/**
 * What activating a hex on the tactical map should do.
 *
 * Deliberately a description, not an effect: the UI decides how to render a preview or fire an
 * intent, while the *rules* for which of these applies live here, in a pure module CI tests.
 */
sealed class MapAction {
    /** The coordinate isn't part of the galaxy — the tap landed off the board. */
    object OutsideMap : MapAction()

    /** The hex is still under fog of war; nothing to do but acknowledge the input. */
    object Unexplored : MapAction()

    /** Clear the current selection. */
    object Deselect : MapAction()

    /** Make [coord] the new selection. */
    data class Select(val coord: HexCoord) : MapAction()

    /** Order the selected fleet at [from] to [to]. */
    data class Move(val from: HexCoord, val to: HexCoord) : MapAction()

    /** Open the combat preview before committing to an attack. */
    data class PreviewCombat(val attacker: HexCoord, val defender: HexCoord) : MapAction()

    /** Open the siege/capture confirmation. [isCapture] distinguishes an undefended world. */
    data class PreviewSiege(
        val attacker: HexCoord,
        val planet: HexCoord,
        val isCapture: Boolean
    ) : MapAction()
}

/**
 * The two-step selection rules of the tactical map: pick a fleet, then pick what it acts on.
 *
 * These used to live inside the tap handler of `TacticalMapScreen`, where nothing could test them
 * — `:app` has no test source set, and CI only gates the pure modules. They are the part of the
 * map most worth pinning down: five branches, each with its own preconditions, driven by two
 * different input paths (touch and keyboard).
 */
object MapInteraction {

    /**
     * Decides what activating [target] means, given the current [selected] hex.
     *
     * [reachable] is passed in rather than recomputed so that the highlight the player sees, the
     * drag preview, and this decision are one and the same set — recomputing it here would
     * reintroduce exactly the divergence between highlight and resolution that the movement
     * audit removed.
     */
    fun activate(
        state: GameState,
        selected: HexCoord?,
        target: HexCoord,
        reachable: Set<HexCoord>
    ): MapAction {
        val tile = state.map.tiles[target] ?: return MapAction.OutsideMap
        val explored = state.playerStates[state.activeFaction]?.exploredHexes ?: emptySet()
        if (target !in explored) return MapAction.Unexplored

        if (selected == target) return MapAction.Deselect

        val selectedUnit = selected?.let { state.units[it] }
        if (selected == null || selectedUnit == null || selectedUnit.faction != state.activeFaction) {
            return MapAction.Select(target)
        }

        val occupant = state.units[target]
        return when {
            // Enemy fleet within weapon range → confirm before firing.
            occupant != null && occupant.faction != state.activeFaction &&
                !selectedUnit.hasAttacked &&
                selected.distanceTo(target) <= selectedUnit.type.range ->
                MapAction.PreviewCombat(selected, target)

            // Adjacent world that isn't yours → siege it, or walk in if it has no defences left.
            //
            // Unowned worlds count. `MapFactory` seeds neutral planets at level 2-4 (and Zodiac
            // nodes at 5), `handleSiegePlanet` / `handleCapturePlanet` only ever refuse a planet
            // you *already own*, and both the range highlight and the side-panel button offer
            // them as targets. This branch used to demand `owner != null`, so tapping a
            // gold-ringed neutral fortress flew the fleet onto it instead — planets are passable
            // — leaving the highlight promising an action the tap would not perform.
            occupant == null && !selectedUnit.hasAttacked &&
                tile.terrain == TerrainType.PLANET &&
                tile.owner != state.activeFaction &&
                selected.distanceTo(target) == 1 ->
                MapAction.PreviewSiege(selected, target, isCapture = tile.systemLevel <= 0)

            // Empty hex the fleet can still reach. Outside that range this deliberately falls
            // through to Select: firing a move the reducer can only reject ("unreachable or too
            // far") turns a mis-tap into an error message instead of a new selection.
            occupant == null && !selectedUnit.hasMoved && target in reachable ->
                MapAction.Move(selected, target)

            else -> MapAction.Select(target)
        }
    }
}
