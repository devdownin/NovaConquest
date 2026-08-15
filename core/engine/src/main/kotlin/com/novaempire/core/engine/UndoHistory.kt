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
        /** A turn holds a handful of actions; an unbounded stack would pin every galaxy forever. */
        const val DEFAULT_DEPTH = 20

        /**
         * Whether an intent can be taken back.
         *
         * Everything a player does inside their own turn is undoable. The turn boundary and the
         * game-lifecycle intents are not — they close the history instead.
         *
         * This is the *comfort* reading of undo, chosen deliberately: rolling a move back also
         * restores `exploredHexes`, but the player has already seen what the move revealed, so
         * scouting by move-look-undo is possible. The stricter alternative — refusing to undo a
         * move that uncovered fog — was rejected because it punishes the honest mis-tap, which is
         * the case this feature exists for.
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
