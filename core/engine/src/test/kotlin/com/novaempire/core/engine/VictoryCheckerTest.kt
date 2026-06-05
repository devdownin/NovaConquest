package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VictoryCheckerTest {

    private fun stateWith(vararg players: PlayerState) = GameState(
        playerStates = players.associateBy { it.faction }
    )

    @Test
    fun noVictoryOnFreshState() {
        val state = stateWith(PlayerState(Faction.DOMINION, credits = 10))
        assertNull(VictoryChecker.check(state))
    }

    @Test
    fun techVictoryWhenAllTechsUnlocked() {
        val techs = (1..8).map { "t$it" }.toSet()
        val state = stateWith(PlayerState(Faction.TRADERS, techUnlocked = techs))
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.TRADERS, result.winner)
        assertEquals("Technological Dominance", result.reason)
    }

    @Test
    fun economicVictoryAt1000Credits() {
        val state = stateWith(PlayerState(Faction.SYNTH, credits = 1000))
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.SYNTH, result.winner)
        assertEquals("Economic Supremacy", result.reason)
    }

    @Test
    fun economicVictoryRequires1000NotLess() {
        val state = stateWith(PlayerState(Faction.SYNTH, credits = 999))
        assertNull(VictoryChecker.check(state))
    }

    @Test
    fun timeLimitVictoryHighestCreditsWins() {
        val state = GameState(
            turn = 100,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 100),
                Faction.TRADERS to PlayerState(Faction.TRADERS, credits = 200)
            )
        )
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.TRADERS, result.winner)
    }

    @Test
    fun existingWinnerPassedThrough() {
        val state = GameState(winner = Faction.DOMINION, victoryReason = "Test")
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.DOMINION, result.winner)
        assertEquals("Test", result.reason)
    }
}
