package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.state.GameState

object TurnManager {

    fun advanceTurn(state: GameState): GameState {
        val allFactions = Faction.values().filter { it != Faction.ANCIENT_NPC }
        val nextIndex = (allFactions.indexOf(state.activeFaction) + 1) % allFactions.size
        val nextFaction = allFactions[nextIndex]

        var nextState = state.copy(activeFaction = nextFaction)

        if (nextIndex == 0) {
            nextState = nextState.copy(turn = state.turn + 1)
            nextState = EventSystem.tick(nextState)
        }

        // Nix hero: heal all units of the faction that just ended its turn
        val activePlayerState = state.playerStates[state.activeFaction]
        if (activePlayerState?.recruitedHeroes?.contains("hero_nix") == true) {
            nextState = nextState.copy(
                units = nextState.units.mapValues { (_, unit) ->
                    if (unit.faction == state.activeFaction && unit.currentHp < unit.type.maxHp)
                        unit.copy(currentHp = minOf(unit.type.maxHp, unit.currentHp + 1))
                    else unit
                }
            )
        }

        // Income for the faction starting its turn
        val nextPlayerState = nextState.playerStates[nextFaction]
        if (nextPlayerState != null) {
            var income = 10
            if (nextPlayerState.recruitedHeroes.contains("hero_elara")) {
                income += (income * 0.10).toInt() + 2
            }
            if (nextState.activeEvent == GalacticEvent.ECONOMIC_BOOM) income += 3
            income += nextFaction.bonusCredits

            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[nextFaction] = nextPlayerState.copy(credits = nextPlayerState.credits + income)
            nextState = nextState.copy(playerStates = newPlayerStates)
        }

        return nextState
    }
}
