package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TechRegistry
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
        val techs = TechRegistry.ALL_TECHS.map { it.id }.toSet()
        val state = stateWith(PlayerState(Faction.TRADERS, techUnlocked = techs))
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.TRADERS, result.winner)
        assertEquals("Technological Dominance", result.reason)
    }

    @Test
    fun techVictoryRequiresTheRealTechIds() {
        // Regression: the check counted entries instead of verifying them, so any set of the right
        // size — here pure filler — handed out a Technological Dominance win.
        val filler = (1..TechRegistry.ALL_TECHS.size).map { "t$it" }.toSet()
        assertNull(VictoryChecker.check(stateWith(PlayerState(Faction.TRADERS, techUnlocked = filler))))
    }

    @Test
    fun techVictoryNotAwardedWhenOneTechIsMissing() {
        val allButOne = TechRegistry.ALL_TECHS.map { it.id }.drop(1).toSet()
        assertNull(VictoryChecker.check(stateWith(PlayerState(Faction.TRADERS, techUnlocked = allButOne))))
    }

    @Test
    fun economicVictoryAt2500Credits() {
        val state = stateWith(PlayerState(Faction.SYNTH, credits = 2500))
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.SYNTH, result.winner)
        assertEquals("Economic Supremacy", result.reason)
    }

    @Test
    fun economicVictoryRequires2500NotLess() {
        val state = stateWith(PlayerState(Faction.SYNTH, credits = 2499))
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
