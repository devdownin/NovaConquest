package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

class GameEngineTest {

    @Test
    fun testInitialState() {
        val engine = GameEngine()
        assertEquals(1, engine.state.value.turn)
        assertEquals(Faction.DOMINION, engine.state.value.activeFaction)
    }

    @Test
    fun testEndTurnIntent() = runBlocking {
        val engine = GameEngine()
        val initialTurn = engine.state.value.turn

        engine.processIntent(GameIntent.EndTurn)

        // Wait for coroutine to process AI turns and finish
        var attempts = 0
        // Wait for it to START thinking first because launch is asynchronous
        delay(50)
        while (engine.isAiThinking.value && attempts < 50) {
            delay(100)
            attempts++
        }
        // Give it a tiny bit more time to finish assigning the state
        delay(100)

        assertEquals(initialTurn + 1, engine.state.value.turn)
    }

    @Test
    fun testSelectFactionIntent() {
        val engine = GameEngine()
        engine.processIntent(GameIntent.SelectFaction(Faction.SYNTH))

        assertEquals(Faction.SYNTH, engine.state.value.activeFaction)
    }
}
