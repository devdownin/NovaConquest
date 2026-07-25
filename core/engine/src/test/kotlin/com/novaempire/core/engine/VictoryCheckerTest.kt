package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.MapArchetype
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val planet = com.novaempire.core.hex.HexCoord(0, 0, 0)

    private fun mapWithPlanet(owner: Faction?) = com.novaempire.core.domain.models.GameMap(
        tiles = mapOf(planet to com.novaempire.core.domain.models.HexTile(
            planet, com.novaempire.core.domain.models.TerrainType.PLANET, systemLevel = 2, owner = owner))
    )

    @Test
    fun mutualAnnihilationEndsInADraw() {
        // No faction holds a unit or a planet: nobody can ever act again, so the game must end
        // rather than grind on to turn 100 with an empty board.
        val state = GameState(
            turn = 12,
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION)),
            map = mapWithPlanet(owner = null)
        )
        val result = VictoryChecker.check(state)!!
        assertNull("a draw has no winner", result.winner)
        assertEquals("Mutual Annihilation — no empire survives", result.reason)
    }

    @Test
    fun aSettledDrawStaysSettled() {
        // The pass-through keys on the reason, not the winner — otherwise a draw would be
        // re-evaluated every turn because `winner` is null.
        val state = GameState(victoryReason = "Mutual Annihilation — no empire survives")
        val result = VictoryChecker.check(state)!!
        assertNull(result.winner)
        assertEquals("Mutual Annihilation — no empire survives", result.reason)
    }

    @Test
    fun timeLimitScoreCountsTerritoryNotJustCredits() {
        // Regression: the turn-100 winner was whoever hoarded the most credits, so a player who
        // never left home beat the one that actually conquered the galaxy.
        val state = GameState(
            turn = 100,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 300), // hoarder, no land
                Faction.TRADERS to PlayerState(Faction.TRADERS, credits = 100)    // holds a level-2 world
            ),
            map = mapWithPlanet(owner = Faction.TRADERS)
        )
        assertEquals(Faction.TRADERS, VictoryChecker.check(state)!!.winner)
    }

    @Test
    fun empireScoreRewardsFleetAndResearch() {
        val bare = PlayerState(Faction.DOMINION, credits = 100)
        val researched = PlayerState(Faction.DOMINION, credits = 100, techUnlocked = setOf("tech_hull_plating"))
        val state = GameState(playerStates = mapOf(Faction.DOMINION to bare))
        assertTrue(
            "unlocked research must raise the end-game score",
            VictoryChecker.empireScore(state, researched) > VictoryChecker.empireScore(state, bare)
        )
    }

    @Test
    fun zodiacVictoryIsNotAwardedToTheAncientNpc() {
        // V4: the sweep used to run over every Faction value, so ANCIENT_NPC — which has no
        // PlayerState and never takes a turn — could be declared galactic winner.
        val node = com.novaempire.core.hex.HexCoord(1, -1, 0)
        val state = GameState(
            playerStates = mapOf(Faction.DOMINION to PlayerState(Faction.DOMINION)),
            map = com.novaempire.core.domain.models.GameMap(
                tiles = mapOf(node to com.novaempire.core.domain.models.HexTile(
                    node, com.novaempire.core.domain.models.TerrainType.PLANET, systemLevel = 5,
                    owner = Faction.ANCIENT_NPC)),
                archetype = MapArchetype.ZODIAC,
                zodiacPlanets = setOf(node)
            )
        )
        val result = VictoryChecker.check(state)
        assertTrue(
            "ANCIENT_NPC must never win the Celestial Alignment",
            result == null || result.winner != Faction.ANCIENT_NPC
        )
    }

    @Test
    fun zodiacVictoryStillWorksForAPlayableFaction() {
        val node = com.novaempire.core.hex.HexCoord(1, -1, 0)
        val state = GameState(
            playerStates = mapOf(Faction.KAELEN to PlayerState(Faction.KAELEN)),
            map = com.novaempire.core.domain.models.GameMap(
                tiles = mapOf(node to com.novaempire.core.domain.models.HexTile(
                    node, com.novaempire.core.domain.models.TerrainType.PLANET, systemLevel = 5,
                    owner = Faction.KAELEN)),
                archetype = MapArchetype.ZODIAC,
                zodiacPlanets = setOf(node)
            )
        )
        assertEquals(Faction.KAELEN, VictoryChecker.check(state)!!.winner)
    }

    @Test
    fun existingWinnerPassedThrough() {
        val state = GameState(winner = Faction.DOMINION, victoryReason = "Test")
        val result = VictoryChecker.check(state)!!
        assertEquals(Faction.DOMINION, result.winner)
        assertEquals("Test", result.reason)
    }
}
