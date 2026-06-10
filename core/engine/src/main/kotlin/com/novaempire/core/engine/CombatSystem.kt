package com.novaempire.core.engine

import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

interface CombatSystem {
    fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): GameState
    fun siegePlanet(state: GameState, attackerCoord: HexCoord, planetCoord: HexCoord): GameState
    fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState
}
