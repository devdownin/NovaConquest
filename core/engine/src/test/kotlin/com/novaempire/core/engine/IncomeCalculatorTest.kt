package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.PlanetSpecialty
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IncomeCalculatorTest {

    private val planet = HexCoord(0, 0, 0)

    private fun state(
        faction: Faction,
        specialty: PlanetSpecialty? = null,
        event: GalacticEvent = GalacticEvent.NONE,
        eventTarget: Faction? = null,
        units: Map<HexCoord, GameUnit> = emptyMap(),
        heroes: Set<String> = emptySet()
    ) = GameState(
        activeFaction = faction,
        playerStates = mapOf(
            faction to PlayerState(faction, credits = 0, recruitedHeroes = heroes),
            Faction.KAELEN to PlayerState(Faction.KAELEN)
        ),
        map = GameMap(tiles = mapOf(planet to HexTile(planet, TerrainType.PLANET, systemLevel = 1, owner = faction, specialty = specialty))),
        units = units,
        activeEvent = event,
        eventTargetFaction = eventTarget
    )

    @Test
    fun baseIncomeCountsPlanets() {
        // base 6 + planet (5 + level 1 * 2 = 7) = 13; DOMINION has no income bonus.
        assertEquals(13, IncomeCalculator.perTurn(state(Faction.DOMINION), Faction.DOMINION))
    }

    @Test
    fun tradePostSpecialtyAddsEight() {
        val without = IncomeCalculator.perTurn(state(Faction.DOMINION), Faction.DOMINION)
        val with = IncomeCalculator.perTurn(
            state(Faction.DOMINION, specialty = PlanetSpecialty.TRADE_POST), Faction.DOMINION
        )
        assertEquals(without + 8, with)
    }

    @Test
    fun fleetUpkeepIsSubtracted() {
        val u = HexCoord(1, -1, 0)
        val withFleet = IncomeCalculator.perTurn(
            state(Faction.DOMINION, units = mapOf(u to GameUnit(type = UnitType.CRUISER, faction = Faction.DOMINION, position = u, currentHp = 25))),
            Faction.DOMINION
        )
        val bare = IncomeCalculator.perTurn(state(Faction.DOMINION), Faction.DOMINION)
        assertEquals("Cruiser upkeep (3) must be deducted", bare - UnitType.CRUISER.upkeepCost, withFleet)
    }

    @Test
    fun targetedEventOnlyAffectsItsTarget() {
        // PIRATE_RAID is targeted (-5 income): the target loses credits, others are untouched.
        val bare = IncomeCalculator.perTurn(state(Faction.DOMINION), Faction.DOMINION)
        val raided = IncomeCalculator.perTurn(
            state(Faction.DOMINION, event = GalacticEvent.PIRATE_RAID, eventTarget = Faction.DOMINION), Faction.DOMINION
        )
        val spared = IncomeCalculator.perTurn(
            state(Faction.DOMINION, event = GalacticEvent.PIRATE_RAID, eventTarget = Faction.KAELEN), Faction.DOMINION
        )
        assertTrue("targeted faction loses income", raided < bare)
        assertEquals("non-targeted faction keeps full income", bare, spared)
    }

    @Test
    fun elaraHeroIncreasesIncome() {
        val bare = IncomeCalculator.perTurn(state(Faction.DOMINION), Faction.DOMINION)
        val withElara = IncomeCalculator.perTurn(
            state(Faction.DOMINION, heroes = setOf(com.novaempire.core.domain.models.HeroRegistry.ELARA)), Faction.DOMINION
        )
        assertTrue("Elara should raise income", withElara > bare)
    }

    @Test
    fun matchesCreditsGrantedByTurnManager() {
        // The whole point of the shared calculator: the preview equals what advanceTurn pays out.
        // DOMINION (index 0) ends its turn → TRADERS (index 1) starts and collects income. Staying
        // inside one round avoids the turn-wrap event roll, keeping the comparison deterministic.
        val tradersPlanet = HexCoord(1, -1, 0)
        val s = GameState(
            activeFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 0),
                Faction.TRADERS to PlayerState(Faction.TRADERS, credits = 0)
            ),
            map = GameMap(tiles = mapOf(
                tradersPlanet to HexTile(tradersPlanet, TerrainType.PLANET, systemLevel = 2, owner = Faction.TRADERS)
            ))
        )
        val expected = IncomeCalculator.perTurn(s, Faction.TRADERS)
        val after = TurnManager.advanceTurn(s, Random(0))
        assertEquals(Faction.TRADERS, after.activeFaction)
        assertEquals(expected, after.playerStates[Faction.TRADERS]!!.credits)
    }
}
