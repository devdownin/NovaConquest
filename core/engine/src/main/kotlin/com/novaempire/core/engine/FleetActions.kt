package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.state.GameState

/**
 * Ce qu'une flotte peut encore faire dans le tour.
 *
 * Le prédicat était recopié en quatre endroits sous la forme `!hasMoved && !hasAttacked`, ce qui
 * voulait dire « intacte ». Depuis le mouvement partiel, cette lecture est fausse dans les deux
 * sens : une flotte ayant dépensé un point sur cinq était comptée comme intacte alors qu'elle
 * l'était déjà moins, et une flotte à court de mouvement mais qui n'a pas tiré était comptée
 * comme finie alors qu'elle pouvait encore engager.
 *
 * SMART FOCUS existe pour amener le joueur sur une flotte qu'il peut **encore commander** — c'est
 * donc cette définition-là qui est retenue, pas « intacte ».
 */
object FleetActions {

    /**
     * Peut-elle encore se déplacer ?
     *
     * `hasMoved` est la seule autorité : le combat le pose sans toucher à [GameUnit.movementUsed],
     * si bien qu'un vaisseau ayant tiré garde des points au compteur sans pouvoir bouger — le
     * réducteur le refuserait.
     */
    fun canMove(state: GameState, unit: GameUnit): Boolean =
        !unit.hasMoved && MovementCalculator.remainingMovement(state, unit) > 0

    /** Reste-t-il un ordre à lui donner — déplacement ou tir ? */
    fun hasActionsLeft(state: GameState, unit: GameUnit): Boolean =
        canMove(state, unit) || !unit.hasAttacked

    /** Les flottes d'une faction qu'il reste à commander, dans un ordre stable. */
    fun idleFleets(state: GameState, faction: com.novaempire.core.domain.models.Faction): List<GameUnit> =
        state.units.values
            .filter { it.faction == faction && hasActionsLeft(state, it) }
            .sortedBy { it.id }
}
