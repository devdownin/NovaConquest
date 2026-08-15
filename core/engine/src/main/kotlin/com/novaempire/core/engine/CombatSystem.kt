package com.novaempire.core.engine

import com.novaempire.core.domain.state.CombatEvent
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

/**
 * Issue d'un échange de tirs : le nouvel état, et ce qu'il faut en montrer.
 *
 * L'événement voyageait jusqu'ici dans `GameState.lastCombatEvent`, un champ @Transient que
 * personne n'effaçait. Deux attaques identiques d'affilée — même attaquant, même cible, cible
 * survivante — produisaient donc deux événements *égaux* : la clé du LaunchedEffect de
 * l'interface ne changeait pas et la comparaison du moteur échouait, si bien que la seconde
 * attaque se déroulait sans laser, sans explosion, sans son ni notification.
 */
data class CombatOutcome(val state: GameState, val event: CombatEvent?)

interface CombatSystem {
    fun resolveCombat(state: GameState, attackerCoord: HexCoord, defenderCoord: HexCoord): CombatOutcome
    fun siegePlanet(state: GameState, attackerCoord: HexCoord, planetCoord: HexCoord): GameState
    fun capturePlanet(state: GameState, unitCoord: HexCoord, planetCoord: HexCoord): GameState
}
