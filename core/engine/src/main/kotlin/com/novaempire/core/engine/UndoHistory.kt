package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState

/**
 * The states a player can roll back to within their turn.
 *
 * Kept apart from [GameEngine] so the policy and the bookkeeping can be tested without driving
 * the engine's intent coroutine — the async path can only be probed by sleeping, which cannot
 * tell "nothing happened" apart from "not processed yet".
 *
 * Undo is cheap here because the reducer is pure: a snapshot is just the previous [GameState],
 * and unchanged sub-structures are shared rather than copied.
 *
 * Undo deliberately stops at the fog of war: an action that uncovers new ground is final, so the
 * feature cannot be turned into free reconnaissance. See [Companion.revealsNewTerritory].
 */
class UndoHistory(private val depth: Int = DEFAULT_DEPTH) {

    private val stack = ArrayDeque<GameState>()

    val canUndo: Boolean get() = stack.isNotEmpty()

    val size: Int get() = stack.size

    /** Remembers [state] as a point to come back to, dropping the oldest beyond [depth]. */
    fun record(state: GameState) {
        stack.addLast(state)
        while (stack.size > depth) stack.removeFirst()
    }

    /** The most recent recorded state, or null when there is nothing to take back. */
    fun rollback(): GameState? = stack.removeLastOrNull()

    fun clear() = stack.clear()

    companion object {

        /**
         * Whether the step from [before] to [after] uncovered ground the acting faction had never
         * seen.
         *
         * Undo may not survive such a step. Rolling one back would restore `exploredHexes`, but
         * not the player's memory: advance, look, undo would be free reconnaissance. Note this
         * has to close the **whole** history, not merely skip recording the revealing action —
         * returning to any earlier state would re-hide the same ground, so the exploit would only
         * be deferred by one action.
         *
         * Explored hexes only ever grow (`explored + visibleNow`), so comparing sizes is enough
         * and avoids walking two large sets.
         */
        fun revealsNewTerritory(before: GameState, after: GameState): Boolean {
            val faction = before.activeFaction
            val was = before.playerStates[faction]?.exploredHexes?.size ?: 0
            val now = after.playerStates[faction]?.exploredHexes?.size ?: 0
            return now > was
        }
        /** A turn holds a handful of actions; an unbounded stack would pin every galaxy forever. */
        const val DEFAULT_DEPTH = 20

        /**
         * Whether the *kind* of intent can be taken back at all.
         *
         * Everything a player does inside their own turn qualifies. The turn boundary and the
         * game-lifecycle intents do not — they close the history instead.
         *
         * This is only half the policy: an otherwise-undoable action still forfeits the history
         * if it uncovered fog. See [revealsNewTerritory].
         */
        fun isUndoable(intent: GameIntent): Boolean = when (intent) {
            is GameIntent.EndTurn,
            is GameIntent.Undo,
            is GameIntent.SelectFaction,
            is GameIntent.StartNewGame,
            is GameIntent.StartNewGameWithSize,
            is GameIntent.StartCampaign,
            is GameIntent.LoadGame -> false
            else -> true
        }
    }
}
