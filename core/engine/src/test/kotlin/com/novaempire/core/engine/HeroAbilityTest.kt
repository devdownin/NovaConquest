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
import org.junit.Assert.assertNotEquals
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
        units: Map<HexCoord, GameUnit> = emptyMap(),
        buildQueue: List<com.novaempire.core.domain.state.BuildOrder> = emptyList(),
        explored: Set<HexCoord> = emptySet()
    ) = GameState(
        activeFaction = Faction.DOMINION,
        humanFaction = Faction.DOMINION,
        playerStates = mapOf(
            Faction.DOMINION to PlayerState(
                Faction.DOMINION,
                credits = credits,
                recruitedHeroes = heroes,
                heroAbilitiesUsed = used,
                researchInProgress = research,
                buildQueue = buildQueue,
                exploredHexes = explored
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

    /**
     * Le piège de ce fichier : `handleUseHeroAbility` répartit sur `hero.id` et se termine par
     * `else -> "Unknown hero ability."`. Ajouter un héros au registre sans lui écrire de branche
     * produit donc un champion dont le bouton d'aptitude affiche une erreur — sans que rien ne
     * casse à la compilation. Ce test échoue à la place.
     */
    @Test
    fun `chaque aptitude du registre est branchee dans le moteur`() {
        for (hero in HeroRegistry.ALL_HEROES.filter { it.ability != null }) {
            val result = use(stateWith(heroes = setOf(hero.id)), hero.id)
            assertNotEquals(
                "${hero.id} est déclaré mais n'a pas de branche dans handleUseHeroAbility",
                "Unknown hero ability.",
                result.error
            )
        }
    }

    // ── Sarn (NOMADS) ─────────────────────────────────────────────────────────

    private fun movedCruiser(hasMoved: Boolean, used: Int) = GameUnit(
        type = UnitType.CRUISER, faction = Faction.DOMINION, position = coord,
        currentHp = UnitType.CRUISER.maxHp, hasMoved = hasMoved, movementUsed = used
    )

    @Test
    fun `Sarn rend leur mouvement aux unites deja deplacees`() {
        val state = stateWith(heroes = setOf(HeroRegistry.SARN), units = mapOf(coord to movedCruiser(true, 3)))

        val unit = use(state, HeroRegistry.SARN).newState.units[coord]!!
        assertFalse(unit.hasMoved)
        assertEquals(0, unit.movementUsed)
    }

    @Test
    fun `Sarn refuse d'agir si rien n'a bouge`() {
        // Même garde que Kael : une ressource à usage unique ne part pas sur un clic prématuré.
        val state = stateWith(heroes = setOf(HeroRegistry.SARN), units = mapOf(coord to movedCruiser(false, 0)))

        val result = use(state, HeroRegistry.SARN)
        assertNotNull(result.error)
        assertTrue("le héros reste disponible", player(result.newState).heroAbilitiesUsed.isEmpty())
    }

    // ── Ysar (KAELEN) ─────────────────────────────────────────────────────────

    @Test
    fun `Ysar cartographie la galaxie sans lever le brouillard`() {
        val state = stateWith(heroes = setOf(HeroRegistry.YSAR))

        val after = use(state, HeroRegistry.YSAR).newState
        assertEquals(state.map.tiles.keys, player(after).exploredHexes)
        // Exploré n'est pas visible : les flottes ennemies restent cachées.
        assertTrue(player(after).visibleHexes.isEmpty())
    }

    @Test
    fun `Ysar refuse d'agir sur une carte deja connue`() {
        val state = stateWith(heroes = setOf(HeroRegistry.YSAR), explored = setOf(coord))

        val result = use(state, HeroRegistry.YSAR)
        assertNotNull(result.error)
        assertTrue(player(result.newState).heroAbilitiesUsed.isEmpty())
    }

    // ── Vashk (XYLAR) ─────────────────────────────────────────────────────────

    @Test
    fun `Vashk fait sortir toute la production au tour suivant`() {
        val queue = listOf(
            com.novaempire.core.domain.state.BuildOrder(UnitType.DREADNOUGHT, coord, turnsRemaining = 5),
            com.novaempire.core.domain.state.BuildOrder(UnitType.CARRIER, coord, turnsRemaining = 4, blocked = true)
        )
        val state = stateWith(heroes = setOf(HeroRegistry.VASHK), buildQueue = queue)

        val after = player(use(state, HeroRegistry.VASHK).newState)
        assertTrue("toute la file est prête", after.buildQueue.all { it.turnsRemaining == 1 })
        assertTrue("un ordre bloqué repart proprement", after.buildQueue.none { it.blocked })
    }

    @Test
    fun `Vashk refuse d'agir sans production en cours`() {
        val result = use(stateWith(heroes = setOf(HeroRegistry.VASHK)), HeroRegistry.VASHK)
        assertNotNull(result.error)
        assertTrue(player(result.newState).heroAbilitiesUsed.isEmpty())
    }
}
