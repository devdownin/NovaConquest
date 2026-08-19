package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Ce que l'échange rapporte au joueur, au-delà de « touché » ou « détruit ».
 *
 * Le résultat d'un tir ne se lisait qu'en comparant deux fois les points de vie dans le panneau
 * latéral. L'événement porte maintenant de quoi l'afficher sur place.
 */
class CombatHitReportTest {

    private val attackerCoord = HexCoord(0, 0, 0)
    private val defenderCoord = HexCoord(1, -1, 0)

    private val fixedRng = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextFloat(): Float = 0.5f
    }

    private fun state(attacker: GameUnit, defender: GameUnit) = GameState(
        units = mapOf(attackerCoord to attacker, defenderCoord to defender),
        playerStates = mapOf(
            attacker.faction to PlayerState(attacker.faction),
            defender.faction to PlayerState(defender.faction)
        )
    )

    private fun unit(type: UnitType, faction: Faction, coord: HexCoord, hp: Int = -1) =
        GameUnit(
            type = type, faction = faction, position = coord,
            currentHp = if (hp >= 0) hp else type.maxHp
        )

    @Test
    fun theDefendersHitCarriesTheDamageAndBothHpValues() {
        val defender = unit(UnitType.DREADNOUGHT, Faction.TRADERS, defenderCoord)
        val outcome = CombatResolver.resolveCombatWithRng(
            state(unit(UnitType.FIGHTER, Faction.DOMINION, attackerCoord), defender),
            attackerCoord, defenderCoord, fixedRng
        )

        val hit = outcome.event?.defenderHit
        assertTrue("le tir doit rapporter ce que la cible a encaissé", hit != null)
        assertEquals(UnitType.DREADNOUGHT.maxHp, hit!!.hpBefore)
        assertEquals(UnitType.DREADNOUGHT.maxHp, hit.maxHp)
        assertEquals("les points de vie tombent des dégâts encaissés", hit.hpBefore - hit.damage, hit.hpAfter)
        assertTrue("un tir fait toujours au moins un point", hit.damage >= 1)
    }

    @Test
    fun aDestroyedTargetStillReportsWhatKilledIt() {
        // La cible quitte l'état : sans `hpBefore` dans l'événement, l'interface n'aurait plus rien
        // à afficher au moment précis où le joueur veut savoir ce qui s'est passé.
        val defender = unit(UnitType.SCOUT, Faction.TRADERS, defenderCoord)
        val outcome = CombatResolver.resolveCombatWithRng(
            state(unit(UnitType.BATTLESHIP, Faction.DOMINION, attackerCoord), defender),
            attackerCoord, defenderCoord, fixedRng
        )

        assertEquals(true, outcome.event?.targetDestroyed)
        assertNull("la cible a bien disparu du plateau", outcome.state.units[defenderCoord])
        val hit = outcome.event?.defenderHit
        assertTrue(hit != null)
        assertEquals("elle tombe à zéro, pas en négatif", 0, hit!!.hpAfter)
        assertEquals(UnitType.SCOUT.maxHp, hit.hpBefore)
    }

    @Test
    fun aRetaliationIsReportedOnTheAttacker() {
        // CRUISER plutôt que FIGHTER : il encaisse la riposte du dreadnought sans mourir, ce qui
        // isole ce que le test veut montrer — la riposte est rapportée — du cas de surkill, traité
        // juste en dessous.
        val outcome = CombatResolver.resolveCombatWithRng(
            state(
                unit(UnitType.CRUISER, Faction.DOMINION, attackerCoord),
                unit(UnitType.DREADNOUGHT, Faction.TRADERS, defenderCoord)
            ),
            attackerCoord, defenderCoord, fixedRng
        )

        val hit = outcome.event?.attackerHit
        assertTrue("la cible survivante riposte, et ça se voit", hit != null)
        assertEquals(UnitType.CRUISER.maxHp, hit!!.hpBefore)
        assertEquals("les points de vie tombent des dégâts encaissés", hit.hpBefore - hit.damage, hit.hpAfter)
    }

    @Test
    fun anOverkillReportsTheFullBlowNotJustWhatWasLeft() {
        // Le fighter n'a pas assez de coque pour absorber la riposte du dreadnought. Les dégâts
        // rapportés restent ceux du coup — c'est ce que le joueur veut lire au-dessus de son
        // vaisseau — tandis que les points de vie s'arrêtent à zéro plutôt que de passer négatifs.
        val outcome = CombatResolver.resolveCombatWithRng(
            state(
                unit(UnitType.FIGHTER, Faction.DOMINION, attackerCoord),
                unit(UnitType.DREADNOUGHT, Faction.TRADERS, defenderCoord)
            ),
            attackerCoord, defenderCoord, fixedRng
        )

        val hit = outcome.event?.attackerHit
        assertTrue(hit != null)
        assertTrue(
            "le cas n'a d'intérêt que si la riposte dépasse la coque, reçu ${hit!!.damage} pour ${hit.hpBefore}",
            hit.damage > hit.hpBefore
        )
        assertEquals("les points de vie s'arrêtent à zéro", 0, hit.hpAfter)
        assertNull("l'attaquant abattu quitte le plateau", outcome.state.units[attackerCoord])
    }

    @Test
    fun aDestroyedTargetDoesNotRetaliate() {
        val outcome = CombatResolver.resolveCombatWithRng(
            state(
                unit(UnitType.BATTLESHIP, Faction.DOMINION, attackerCoord),
                unit(UnitType.SCOUT, Faction.TRADERS, defenderCoord)
            ),
            attackerCoord, defenderCoord, fixedRng
        )

        // `null` et non « riposte à zéro » : les deux se dessinent différemment.
        assertNull("un vaisseau détruit ne rend pas le tir", outcome.event?.attackerHit)
    }
}
