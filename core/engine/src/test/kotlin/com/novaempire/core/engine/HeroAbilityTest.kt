package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les aptitudes actives à usage unique. Seule celle de Nix était couverte ; Vance, Elara, Kael et
 * les trois gardes (héros non recruté, aptitude déjà utilisée, identifiant inconnu) ne l'étaient
 * pas — et le cas non testé de Kael était précisément celui qui gaspillait l'aptitude.
 */
class HeroAbilityTest {

    private val coord = HexCoord(0, 0, 0)

    private fun stateWith(
        heroes: Set<String>,
        used: Set<String> = emptySet(),
        credits: Int = 10,
        research: ResearchProgress? = null,
        units: Map<HexCoord, GameUnit> = emptyMap()
    ) = GameState(
        activeFaction = Faction.DOMINION,
        humanFaction = Faction.DOMINION,
        playerStates = mapOf(
            Faction.DOMINION to PlayerState(
                Faction.DOMINION,
                credits = credits,
                recruitedHeroes = heroes,
                heroAbilitiesUsed = used,
                researchInProgress = research
            )
        ),
        map = GameMap(tiles = mapOf(coord to HexTile(coord, TerrainType.EMPTY))),
        units = units
    )

    private fun use(state: GameState, heroId: String) =
        handleUseHeroAbility(state, GameIntent.UseHeroAbility(heroId))

    private fun player(state: GameState) = state.playerStates[Faction.DOMINION]!!

    // ── Gardes ────────────────────────────────────────────────────────────────

    @Test
    fun `un heros non recrute ne peut pas agir`() {
        val result = use(stateWith(heroes = emptySet()), HeroRegistry.VANCE)
        assertEquals("Hero not recruited.", result.error)
    }

    @Test
    fun `une aptitude deja utilisee est refusee`() {
        val state = stateWith(heroes = setOf(HeroRegistry.ELARA), used = setOf(HeroRegistry.ELARA))
        val result = use(state, HeroRegistry.ELARA)
        assertEquals("Ability already used this game.", result.error)
        assertEquals(10, player(result.newState).credits)
    }

    @Test
    fun `un identifiant inconnu est refuse sans rien consommer`() {
        val state = stateWith(heroes = setOf(HeroRegistry.VANCE))
        val result = use(state, "hero_inexistant")
        assertNotNull(result.error)
        assertTrue(player(result.newState).heroAbilitiesUsed.isEmpty())
    }

    // ── Elara ─────────────────────────────────────────────────────────────────

    @Test
    fun `elara verse ses credits et marque l'aptitude utilisee`() {
        val state = stateWith(heroes = setOf(HeroRegistry.ELARA), credits = 10)
        val after = player(use(state, HeroRegistry.ELARA).newState)

        assertEquals(10 + HeroRegistry.ELARA_ABILITY_CREDITS, after.credits)
        assertTrue(after.heroAbilitiesUsed.contains(HeroRegistry.ELARA))
    }

    // ── Vance ─────────────────────────────────────────────────────────────────

    @Test
    fun `vance rend son tir a la flotte sans lui rendre son deplacement`() {
        val spent = GameUnit(
            type = UnitType.CRUISER, faction = Faction.DOMINION, position = coord,
            currentHp = UnitType.CRUISER.maxHp, hasMoved = true, hasAttacked = true
        )
        val state = stateWith(heroes = setOf(HeroRegistry.VANCE), units = mapOf(coord to spent))

        val unit = use(state, HeroRegistry.VANCE).newState.units[coord]!!
        assertFalse("l'unité doit pouvoir tirer à nouveau", unit.hasAttacked)
        assertTrue("mais pas se redéplacer", unit.hasMoved)
    }

    @Test
    fun `vance ne rend pas son tir aux unites ennemies`() {
        val enemyCoord = HexCoord(1, -1, 0)
        val enemy = GameUnit(
            type = UnitType.CRUISER, faction = Faction.SYNTH, position = enemyCoord,
            currentHp = UnitType.CRUISER.maxHp, hasAttacked = true
        )
        val state = stateWith(heroes = setOf(HeroRegistry.VANCE), units = mapOf(enemyCoord to enemy))

        assertTrue(use(state, HeroRegistry.VANCE).newState.units[enemyCoord]!!.hasAttacked)
    }

    // ── Kael ──────────────────────────────────────────────────────────────────

    @Test
    fun `kael termine la recherche en cours`() {
        val state = stateWith(
            heroes = setOf(HeroRegistry.KAEL),
            research = ResearchProgress("tech_x", turnsRemaining = 4)
        )
        val after = player(use(state, HeroRegistry.KAEL).newState)

        assertTrue(after.techUnlocked.contains("tech_x"))
        assertNull(after.researchInProgress)
        assertTrue(after.heroAbilitiesUsed.contains(HeroRegistry.KAEL))
    }

    /** Le défaut : l'aptitude se marquait « utilisée » et ne produisait rien. */
    @Test
    fun `kael sans recherche en cours refuse au lieu de se gaspiller`() {
        val state = stateWith(heroes = setOf(HeroRegistry.KAEL), research = null)
        val result = use(state, HeroRegistry.KAEL)

        assertNotNull("l'action doit être refusée", result.error)
        assertTrue(
            "l'aptitude doit rester disponible",
            player(result.newState).heroAbilitiesUsed.isEmpty()
        )
    }

    // ── Nix ───────────────────────────────────────────────────────────────────

    @Test
    fun `nix repare la moitie de la coque sans depasser le maximum`() {
        val wounded = GameUnit(
            type = UnitType.CRUISER, faction = Faction.DOMINION, position = coord, currentHp = 1
        )
        val state = stateWith(heroes = setOf(HeroRegistry.NIX), units = mapOf(coord to wounded))

        val healed = use(state, HeroRegistry.NIX).newState.units[coord]!!
        assertEquals(1 + (UnitType.CRUISER.maxHp + 1) / 2, healed.currentHp)
        assertTrue(healed.currentHp < UnitType.CRUISER.maxHp)
    }

    @Test
    fun `une unite presque intacte ne depasse pas sa coque maximale`() {
        val nearlyFull = GameUnit(
            type = UnitType.CRUISER, faction = Faction.DOMINION, position = coord,
            currentHp = UnitType.CRUISER.maxHp - 1
        )
        val state = stateWith(heroes = setOf(HeroRegistry.NIX), units = mapOf(coord to nearlyFull))

        assertEquals(UnitType.CRUISER.maxHp, use(state, HeroRegistry.NIX).newState.units[coord]!!.currentHp)
    }

    // ── Cohérence modèle / moteur ─────────────────────────────────────────────

    /**
     * Chaque héros que le moteur sait faire agir doit décrire son aptitude dans le registre : c'est
     * ce qui permet à l'écran et aux notifications de lire le même texte au lieu de le recopier.
     */
    @Test
    fun `chaque heros expose son aptitude active`() {
        for (hero in HeroRegistry.ALL_HEROES) {
            assertNotNull("${hero.id} n'a pas d'aptitude décrite", hero.ability)
            assertTrue(hero.ability!!.name.isNotBlank())
            assertTrue(hero.ability!!.description.isNotBlank())
        }
    }
}
