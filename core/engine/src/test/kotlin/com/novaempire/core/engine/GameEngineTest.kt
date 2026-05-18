package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineTest {

    @Test
    fun testInitialState() {
        val engine = GameEngine()
        assertEquals(1, engine.state.value.turn)
        assertEquals(Faction.DOMINION, engine.state.value.activeFaction)
    }

    @Test
    fun testEndTurnIntent() {
        val engine = GameEngine()
        val initialTurn = engine.state.value.turn

        engine.processIntent(GameIntent.EndTurn)

        assertEquals(initialTurn + 1, engine.state.value.turn)
    }

    @Test
    fun testSelectFactionIntent() {
        val engine = GameEngine()
        engine.processIntent(GameIntent.SelectFaction(Faction.SYNTH))

        assertEquals(Faction.SYNTH, engine.state.value.activeFaction)
    }
}
