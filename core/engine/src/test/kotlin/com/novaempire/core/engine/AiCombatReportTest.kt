package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.hex.HexCoord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les tirs d'un tour d'IA doivent parvenir à l'interface.
 *
 * `executeAITurn` reçoit un `reduce` qui ne rendait que l'état, si bien que chaque `combatEvent`
 * produit par l'IA était calculé puis jeté : le joueur voyait ses vaisseaux perdre des points de vie
 * sans jamais voir le tir. Ces tests fixent le contrat de ce qui remonte — et surtout de ce qui ne
 * remonte pas, car un tir animé au fond du brouillard trahirait la position de deux flottes.
 */
class AiCombatReportTest {

    private val humanCoord = HexCoord(0, 0, 0)
    private val aiCoord = HexCoord(1, -1, 0)
    private val farAiCoord = HexCoord(6, -6, 0)
    private val farEnemyCoord = HexCoord(7, -6, -1)

    /** Une IA qui tire une fois, par le `reduce` qu'on lui confie, puis s'arrête. */
    private class AttackOnceAI(
        private val attacker: HexCoord,
        private val defender: HexCoord
    ) : AIStrategy {
        override suspend fun executeAITurn(
            state: GameState,
            faction: Faction,
            reduce: (GameState, GameIntent) -> GameState
        ): GameState = reduce(state, GameIntent.AttackUnit(attacker, defender))
    }

    /**
     * Un plateau où XYLAR (IA) peut tirer sur DOMINION (joueur).
     *
     * [extraUnits] sert à poser un second couple de combattants hors de tout regard humain.
     */
    private fun board(extraUnits: Map<HexCoord, GameUnit> = emptyMap()): GameState {
        val coords = listOf(humanCoord, aiCoord, farAiCoord, farEnemyCoord)
        return GameState(
            activeFaction = Faction.DOMINION,
            humanFaction = Faction.DOMINION,
            playerStates = mapOf(
                Faction.DOMINION to PlayerState(Faction.DOMINION, credits = 10),
                Faction.XYLAR to PlayerState(Faction.XYLAR, credits = 10)
            ),
            map = GameMap(tiles = coords.associateWith { HexTile(it, TerrainType.EMPTY) }),
            units = mapOf(
                humanCoord to GameUnit(
                    type = UnitType.CRUISER, faction = Faction.DOMINION,
                    position = humanCoord, currentHp = UnitType.CRUISER.maxHp
                ),
                aiCoord to GameUnit(
                    type = UnitType.CRUISER, faction = Faction.XYLAR,
                    position = aiCoord, currentHp = UnitType.CRUISER.maxHp
                )
            ) + extraUnits
        )
    }

    /**
     * Joue une fin de tour complète et rend les effets émis.
     *
     * L'attente porte sur deux conditions plutôt que sur un délai : le nouveau tour publié, puis
     * l'IA au repos. `isAiThinking` seul ne suffirait pas — le tour peut se terminer avant que le
     * test n'ait eu le temps de le voir commencer, et attendre un passage à `true` déjà manqué
     * bloquerait jusqu'au délai de garde.
     */
    private fun playOneTurn(ai: AIStrategy, state: GameState): List<GameEffect> = runBlocking {
        val engine = GameEngine(ai)
        val collected = mutableListOf<GameEffect>()
        // `_effects` n'a aucun tampon : un abonné arrivé après coup ne verrait rien. Ce `delay`
        // attend l'abonnement, pas le résultat — celui-là est attendu par condition.
        val job = launch { engine.effects.collect { collected += it } }
        delay(100)

        engine.processIntent(GameIntent.LoadGame(state))
        val startTurn = state.turn
        engine.processIntent(GameIntent.EndTurn)
        withTimeout(5_000) {
            engine.state.first { it.turn > startTurn }
            engine.isAiThinking.first { !it }
        }

        job.cancel()
        engine.dispose()
        collected
    }

    @Test
    fun anAiAttackOnTheHumanIsAnimated() {
        val combats = playOneTurn(AttackOnceAI(aiCoord, humanCoord), board())
            .filterIsInstance<GameEffect.CombatResolved>()

        assertEquals("le tir de l'IA doit remonter, une seule fois", 1, combats.size)
        assertEquals(aiCoord, combats.single().event.attackerCoord)
        assertEquals(humanCoord, combats.single().event.defenderCoord)
    }

    @Test
    fun anAiAttackShakesTheCameraLikeTheHumansOwn() {
        val effects = playOneTurn(AttackOnceAI(aiCoord, humanCoord), board())

        assertTrue(
            "la secousse accompagne le tir de l'IA comme celui du joueur",
            effects.any { it is GameEffect.ShakeCamera }
        )
    }

    @Test
    fun theLogNamesBothShipsRatherThanFallingBackToUnit() {
        val lines = playOneTurn(AttackOnceAI(aiCoord, humanCoord), board())
            .filterIsInstance<GameEffect.ShowNotification>()
            .map { it.message }
            .filter { it.contains("CRUISER") }

        assertTrue("le tir doit laisser une ligne de journal", lines.isNotEmpty())
        // Les noms sont relevés sur l'état d'avant le tir. Les lire après ferait ressortir une
        // cible détruite en « UNIT », puisqu'elle a déjà quitté l'état.
        assertTrue("aucun nom ne doit être perdu, reçu $lines", lines.none { it.contains("UNIT") })
    }

    @Test
    fun aFightBetweenTwoFleetsTheHumanCannotSeeIsNotShown() {
        val hidden = mapOf(
            farAiCoord to GameUnit(
                type = UnitType.CRUISER, faction = Faction.XYLAR,
                position = farAiCoord, currentHp = UnitType.CRUISER.maxHp
            ),
            farEnemyCoord to GameUnit(
                type = UnitType.CRUISER, faction = Faction.KAELEN,
                position = farEnemyCoord, currentHp = UnitType.CRUISER.maxHp
            )
        )

        val combats = playOneTurn(AttackOnceAI(farAiCoord, farEnemyCoord), board(hidden))
            .filterIsInstance<GameEffect.CombatResolved>()

        assertEquals(
            "un tir dont aucun des deux bouts n'est observé ne doit pas être animé",
            emptyList<GameEffect.CombatResolved>(),
            combats
        )
    }
}
