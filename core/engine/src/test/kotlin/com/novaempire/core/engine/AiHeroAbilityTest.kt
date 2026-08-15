package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les critères de déclenchement des aptitudes côté IA.
 *
 * L'IA recrutait des héros mais n'utilisait aucune de leurs aptitudes : quatre effets à usage
 * unique réservés de fait au joueur humain. Ces tests verrouillent le déclenchement — et surtout le
 * **non**-déclenchement, une aptitude jouée trop tôt étant définitivement perdue.
 */
class AiHeroAbilityTest {

    private val ai = Faction.SYNTH
    private val foe = Faction.DOMINION

    private fun state(
        heroes: Set<String>,
        used: Set<String> = emptySet(),
        credits: Int = 100,
        research: ResearchProgress? = null,
        units: Map<HexCoord, GameUnit> = emptyMap(),
        atWar: Boolean = true
    ): GameState {
        val tiles = units.keys.associateWith { HexTile(it, TerrainType.EMPTY) }
        return GameState(
            activeFaction = ai,
            humanFaction = foe,
            playerStates = mapOf(
                ai to PlayerState(
                    ai,
                    credits = credits,
                    recruitedHeroes = heroes,
                    heroAbilitiesUsed = used,
                    researchInProgress = research,
                    relations = if (atWar) mapOf(foe to DiplomaticRelation.WAR) else emptyMap()
                )
            ),
            map = GameMap(tiles = tiles),
            units = units
        )
    }

    private fun unit(
        faction: Faction,
        q: Int,
        hp: Int = UnitType.CRUISER.maxHp,
        attacked: Boolean = false
    ): Pair<HexCoord, GameUnit> {
        val c = HexCoord(q, -q, 0)
        return c to GameUnit(
            type = UnitType.CRUISER, faction = faction, position = c,
            currentHp = hp, hasAttacked = attacked
        )
    }

    // ── Kael ──────────────────────────────────────────────────────────────────

    @Test
    fun `kael attend une recherche assez longue`() {
        val long = state(setOf(HeroRegistry.KAEL), research = ResearchProgress("t", turnsRemaining = 4))
        assertTrue(UtilityEvaluator.isAbilityWorthwhile(long, ai, HeroRegistry.KAEL))

        val almostDone = state(setOf(HeroRegistry.KAEL), research = ResearchProgress("t", turnsRemaining = 1))
        assertFalse(
            "griller l'aptitude pour un seul tour de recherche la gâche",
            UtilityEvaluator.isAbilityWorthwhile(almostDone, ai, HeroRegistry.KAEL)
        )
    }

    @Test
    fun `kael ne se declenche pas sans recherche`() {
        val idle = state(setOf(HeroRegistry.KAEL), research = null)
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(idle, ai, HeroRegistry.KAEL))
    }

    // ── Nix ───────────────────────────────────────────────────────────────────

    @Test
    fun `nix intervient sur une flotte reellement amochee`() {
        val battered = state(
            setOf(HeroRegistry.NIX),
            units = mapOf(unit(ai, 0, hp = 3), unit(ai, 1, hp = 4))
        )
        assertTrue(UtilityEvaluator.isAbilityWorthwhile(battered, ai, HeroRegistry.NIX))
    }

    @Test
    fun `nix ne repare pas des eraflures`() {
        val scratched = state(
            setOf(HeroRegistry.NIX),
            units = mapOf(
                unit(ai, 0, hp = UnitType.CRUISER.maxHp - 1),
                unit(ai, 1, hp = UnitType.CRUISER.maxHp - 2)
            )
        )
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(scratched, ai, HeroRegistry.NIX))
    }

    @Test
    fun `nix ne se declenche pas pour une unite isolee`() {
        val lone = state(setOf(HeroRegistry.NIX), units = mapOf(unit(ai, 0, hp = 1)))
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(lone, ai, HeroRegistry.NIX))
    }

    // ── Elara ─────────────────────────────────────────────────────────────────

    @Test
    fun `elara sort le convoi quand les caisses sont vides`() {
        assertTrue(
            UtilityEvaluator.isAbilityWorthwhile(state(setOf(HeroRegistry.ELARA), credits = 3), ai, HeroRegistry.ELARA)
        )
        assertFalse(
            "inutile de convoyer des crédits quand il y en a déjà",
            UtilityEvaluator.isAbilityWorthwhile(state(setOf(HeroRegistry.ELARA), credits = 200), ai, HeroRegistry.ELARA)
        )
    }

    // ── Vance ─────────────────────────────────────────────────────────────────

    @Test
    fun `vance frappe quand plusieurs unites ont tire et qu'une cible reste a portee`() {
        val s = state(
            setOf(HeroRegistry.VANCE),
            units = mapOf(
                unit(ai, 0, attacked = true),
                unit(ai, 1, attacked = true),
                unit(foe, 2)
            )
        )
        assertTrue(UtilityEvaluator.shouldUseSuppressiveStrike(s, ai))
    }

    @Test
    fun `vance ne frappe pas sans cible a portee`() {
        val s = state(
            setOf(HeroRegistry.VANCE),
            units = mapOf(
                unit(ai, 0, attacked = true),
                unit(ai, 1, attacked = true),
                unit(foe, 9)
            )
        )
        assertFalse(
            "rendre son tir à une flotte qui n'atteint personne gâche l'aptitude",
            UtilityEvaluator.shouldUseSuppressiveStrike(s, ai)
        )
    }

    /** Hors guerre, la cible n'est pas attaquable : rendre son tir à la flotte ne sert à rien. */
    @Test
    fun `vance ne frappe pas sans etat de guerre`() {
        val s = state(
            setOf(HeroRegistry.VANCE),
            units = mapOf(
                unit(ai, 0, attacked = true),
                unit(ai, 1, attacked = true),
                unit(foe, 2)
            ),
            atWar = false
        )
        assertFalse(UtilityEvaluator.shouldUseSuppressiveStrike(s, ai))
    }

    @Test
    fun `vance ne frappe pas pour une seule unite`() {
        val s = state(
            setOf(HeroRegistry.VANCE),
            units = mapOf(unit(ai, 0, attacked = true), unit(foe, 1))
        )
        assertFalse(UtilityEvaluator.shouldUseSuppressiveStrike(s, ai))
    }

    @Test
    fun `une aptitude deja utilisee ne se rejoue pas`() {
        val s = state(
            setOf(HeroRegistry.VANCE),
            used = setOf(HeroRegistry.VANCE),
            units = mapOf(
                unit(ai, 0, attacked = true),
                unit(ai, 1, attacked = true),
                unit(foe, 2)
            )
        )
        assertFalse(UtilityEvaluator.shouldUseSuppressiveStrike(s, ai))
    }

    @Test
    fun `un heros non recrute n'ouvre aucune aptitude`() {
        val s = state(emptySet(), units = mapOf(unit(ai, 0, attacked = true), unit(foe, 1)))
        assertFalse(UtilityEvaluator.shouldUseSuppressiveStrike(s, ai))
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(s, ai, HeroRegistry.NIX))
    }

    // ── Sarn, Ysar, Vashk ─────────────────────────────────────────────────────

    private fun moved(faction: Faction, q: Int): Pair<HexCoord, GameUnit> {
        val c = HexCoord(q, -q, 0)
        return c to GameUnit(
            type = UnitType.CRUISER, faction = faction, position = c,
            currentHp = UnitType.CRUISER.maxHp, hasMoved = true, movementUsed = 3
        )
    }

    @Test
    fun `sarn saute quand plusieurs unites ont deja bouge`() {
        val s = state(heroes = setOf(HeroRegistry.SARN), units = mapOf(moved(ai, 0), moved(ai, 1)))
        assertTrue(UtilityEvaluator.shouldUseCaravanJump(s, ai))
    }

    @Test
    fun `sarn ne saute pas avant que la flotte ait bouge`() {
        // Déclenchée dans la phase stratégique, l'aptitude partirait à vide : rien n'a encore bougé.
        val s = state(heroes = setOf(HeroRegistry.SARN), units = mapOf(unit(ai, 0), unit(ai, 1)))
        assertFalse(UtilityEvaluator.shouldUseCaravanJump(s, ai))
    }

    @Test
    fun `sarn ne saute pas pour une seule unite`() {
        val s = state(heroes = setOf(HeroRegistry.SARN), units = mapOf(moved(ai, 0)))
        assertFalse(UtilityEvaluator.shouldUseCaravanJump(s, ai))
    }

    @Test
    fun `ysar consulte les archives quand la galaxie reste a decouvrir`() {
        val tiles = (0..9).associate { q ->
            val c = HexCoord(q, -q, 0)
            c to HexTile(c, TerrainType.EMPTY)
        }
        val s = state(heroes = setOf(HeroRegistry.YSAR)).let { it.copy(map = it.map.copy(tiles = tiles)) }
        assertTrue(UtilityEvaluator.isAbilityWorthwhile(s, ai, HeroRegistry.YSAR))
    }

    @Test
    fun `ysar ne consulte pas des archives sur une carte deja parcourue`() {
        val tiles = (0..9).associate { q ->
            val c = HexCoord(q, -q, 0)
            c to HexTile(c, TerrainType.EMPTY)
        }
        val s = state(heroes = setOf(HeroRegistry.YSAR)).let { base ->
            val ps = base.playerStates[ai]!!.copy(exploredHexes = tiles.keys)
            base.copy(map = base.map.copy(tiles = tiles), playerStates = mapOf(ai to ps))
        }
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(s, ai, HeroRegistry.YSAR))
    }

    @Test
    fun `vashk fait eclore une file qui attend vraiment`() {
        val s = state(heroes = setOf(HeroRegistry.VASHK)).let { base ->
            val ps = base.playerStates[ai]!!.copy(
                buildQueue = listOf(
                    com.novaempire.core.domain.state.BuildOrder(UnitType.DREADNOUGHT, HexCoord(0, 0, 0), 5)
                )
            )
            base.copy(playerStates = mapOf(ai to ps))
        }
        assertTrue(UtilityEvaluator.isAbilityWorthwhile(s, ai, HeroRegistry.VASHK))
    }

    @Test
    fun `vashk n'eclot pas pour un ordre presque termine`() {
        // Gagner un tour ne vaut pas un usage unique.
        val s = state(heroes = setOf(HeroRegistry.VASHK)).let { base ->
            val ps = base.playerStates[ai]!!.copy(
                buildQueue = listOf(
                    com.novaempire.core.domain.state.BuildOrder(UnitType.SCOUT, HexCoord(0, 0, 0), 1)
                )
            )
            base.copy(playerStates = mapOf(ai to ps))
        }
        assertFalse(UtilityEvaluator.isAbilityWorthwhile(s, ai, HeroRegistry.VASHK))
    }

    @Test
    fun `vashk n'eclot pas sans production`() {
        assertFalse(
            UtilityEvaluator.isAbilityWorthwhile(state(heroes = setOf(HeroRegistry.VASHK)), ai, HeroRegistry.VASHK)
        )
    }
}
