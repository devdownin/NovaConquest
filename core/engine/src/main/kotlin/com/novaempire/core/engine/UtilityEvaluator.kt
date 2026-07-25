package com.novaempire.core.engine

import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.Hero
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.HexTile
import com.novaempire.core.domain.models.TechBranch
import com.novaempire.core.domain.models.TechDefinition
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.hex.HexCoord
import com.novaempire.core.hex.HexPathfinder

object UtilityEvaluator : AIStrategy {

    /** A candidate action for a unit, ranked by its estimated utility. */
    private data class ScoredAction(val score: Double, val intent: GameIntent)

    /** Below this score an action isn't worth taking (the unit moves/regroups instead). */
    private const val ACTION_THRESHOLD = 1.0

    override suspend fun executeAITurn(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState {
        val aiFaction = faction
        var currentState = state

        // Strategic layer.
        currentState = evaluateDiplomacy(currentState, aiFaction, reduce)
        currentState = evaluateEconomyAndTech(currentState, aiFaction)
        currentState = evaluateHeroes(currentState, aiFaction)
        currentState = evaluateProduction(currentState, aiFaction)

        // Tactical layer: score candidate actions per unit and take the best. Iterate over a stable
        // snapshot of unit ids and re-fetch each unit from the live state (it may move or die mid-turn).
        val unitIds = currentState.units.values.filter { it.faction == aiFaction }.map { it.id }
        for (id in unitIds) {
            currentState = actUnit(currentState, id, aiFaction, reduce)
        }

        val refreshedUnits = currentState.units.mapValues {
            if (it.value.faction == aiFaction) it.value.copy(hasMoved = false, hasAttacked = false)
            else it.value
        }
        return currentState.copy(units = refreshedUnits)
    }

    /** Decide and execute one unit's turn: strike if worthwhile, else reposition then strike. */
    private fun actUnit(
        state: GameState,
        unitId: String,
        aiFaction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState {
        var s = state
        var unit = s.units.values.find { it.id == unitId } ?: return s

        // 1. Best combat action from the current position.
        if (!unit.hasAttacked) {
            val best = bestCombatAction(s, unit, aiFaction)
            if (best != null && best.score >= ACTION_THRESHOLD) {
                return reduce(s, best.intent) // combat consumes the whole turn (sets hasMoved + hasAttacked)
            }
        }

        // 2. Reposition: retreat if wounded & threatened, advance on the best objective, else regroup.
        unit = s.units.values.find { it.id == unitId } ?: return s
        if (!unit.hasMoved && unit.type.movement > 0) {
            val dest = chooseDestination(s, unit, aiFaction)
            if (dest != null && dest != unit.position) {
                s = reduce(s, GameIntent.MoveUnit(unit.position, dest))
                // 3. Opportunistic strike from the new position.
                val moved = s.units.values.find { it.id == unitId }
                if (moved != null && !moved.hasAttacked) {
                    val best = bestCombatAction(s, moved, aiFaction)
                    if (best != null && best.score >= ACTION_THRESHOLD) {
                        s = reduce(s, best.intent)
                    }
                }
            }
        }
        return s
    }

    /** Highest-utility attack / capture / siege the unit can perform right now, or null. */
    private fun bestCombatAction(state: GameState, unit: GameUnit, aiFaction: Faction): ScoredAction? {
        val actions = mutableListOf<ScoredAction>()
        val myDamage = estimatedAttack(state, unit, aiFaction)

        for (target in enemyUnitsOf(state, aiFaction)) {
            val dist = unit.position.distanceTo(target.position)
            if (dist > unit.type.range) continue
            var score = 8.0
            val kills = myDamage >= target.currentHp
            if (kills) score += 45.0 + target.type.cost
            else score += 18.0 * (myDamage.toDouble() / target.currentHp).coerceAtMost(1.0)
            score += (1.0 - target.currentHp.toDouble() / target.type.maxHp) * 12.0 // finish the wounded
            val countered = dist <= target.type.range
            if (!countered) score += 15.0 // out-ranging the enemy = free damage
            else if (!kills && target.type.attack >= unit.currentHp) score -= 30.0 // avoid suicidal chip
            actions += ScoredAction(score, GameIntent.AttackUnit(unit.position, target.position))
        }

        for (planet in enemyPlanetsOf(state, aiFaction)) {
            if (unit.position.distanceTo(planet.coord) > 1) continue
            if (planet.systemLevel == 0) {
                actions += ScoredAction(70.0, GameIntent.CapturePlanet(unit.position, planet.coord))
            } else {
                var score = 22.0 + planet.systemLevel * 4.0
                if (planet.systemLevel * 2 >= unit.currentHp) score -= 35.0 // don't die to planetary defences
                actions += ScoredAction(score, GameIntent.SiegePlanet(unit.position, planet.coord))
            }
        }
        return actions.maxByOrNull { it.score }
    }

    /** Deterministic damage estimate (mid-variance, no terrain) used for scoring. */
    private fun estimatedAttack(state: GameState, unit: GameUnit, faction: Faction): Int {
        val ps = state.playerStates[faction]
        val pct = BonusRegistry.sum(BonusType.ATTACK_PERCENT, ps, state.activeEvent)
        val flat = BonusRegistry.sum(BonusType.ATTACK_FLAT, ps, state.activeEvent)
        val percentBonus = if (pct > 0) maxOf(1, (unit.type.attack * pct / 100.0).toInt()) else 0
        return unit.type.attack + percentBonus + flat
    }

    /** Where should this unit move? Retreat > advance-on-objective > regroup. */
    private fun chooseDestination(state: GameState, unit: GameUnit, aiFaction: Faction): HexCoord? {
        val gridMap = GameGridMap(state, aiFaction)
        val movement = effectiveMovement(state, aiFaction, unit.type.movement)
        val enemies = enemyUnitsOf(state, aiFaction)

        val hpFrac = unit.currentHp.toFloat() / unit.type.maxHp
        val threatened = enemies.any { it.position.distanceTo(unit.position) <= 2 }
        if (hpFrac < 0.30f && threatened) {
            val haven = state.map.tiles.values
                .filter { it.owner == aiFaction && it.coord != unit.position }
                .minByOrNull { it.coord.distanceTo(unit.position) }
            if (haven != null) stepToward(state, unit, haven.coord, gridMap, movement)?.let { return it }
        }

        bestObjective(state, unit, aiFaction)?.let { objective ->
            stepToward(state, unit, objective, gridMap, movement)?.let { return it }
        }

        // Regroup toward the fleet centroid so lone units don't wander off to die.
        val allies = state.units.values.filter { it.faction == aiFaction && it.id != unit.id }
        if (allies.isNotEmpty()) {
            val centroid = fleetCentroid(allies)
            if (centroid != unit.position) stepToward(state, unit, centroid, gridMap, movement)?.let { return it }
        }
        return null
    }

    /** The most attractive objective coord (enemy planet / enemy unit / threatened own planet). */
    private fun bestObjective(state: GameState, unit: GameUnit, aiFaction: Faction): HexCoord? {
        data class Obj(val coord: HexCoord, val value: Double)
        val objs = mutableListOf<Obj>()

        for (planet in enemyPlanetsOf(state, aiFaction)) {
            objs += Obj(planet.coord, if (planet.systemLevel == 0) 65.0 else 30.0 + planet.systemLevel * 3.0)
        }
        for (target in enemyUnitsOf(state, aiFaction)) {
            val wounded = (1.0 - target.currentHp.toDouble() / target.type.maxHp) * 10.0
            objs += Obj(target.position, 12.0 + target.type.cost + wounded)
        }
        val enemies = enemyUnitsOf(state, aiFaction)
        for (planet in state.map.tiles.values.filter { it.owner == aiFaction }) {
            if (enemies.any { it.position.distanceTo(planet.coord) <= 2 }) objs += Obj(planet.coord, 45.0)
        }
        if (objs.isEmpty()) return null
        // Trade value off against distance so nearby, valuable objectives win.
        return objs.maxByOrNull { it.value - unit.position.distanceTo(it.coord) * 2.0 }?.coord
    }

    /** Walk as far along the path toward [goal] as this turn's movement allows. */
    private fun stepToward(state: GameState, unit: GameUnit, goal: HexCoord, gridMap: GameGridMap, movement: Int): HexCoord? {
        val approach = if (gridMap.isPassable(goal)) goal
        else HexCoord.directions.map { goal + it }
            .filter { gridMap.isPassable(it) }
            .minByOrNull { it.distanceTo(unit.position) } ?: return null
        if (approach == unit.position) return null
        val path = HexPathfinder.findPath(unit.position, approach, gridMap) ?: return null
        // Walk the path spending movement points per hex (difficult terrain costs more), and
        // return the furthest free hex still within budget — matches what the engine will accept.
        var budget = movement
        var dest: HexCoord? = null
        for (hex in path) {
            budget -= gridMap.enterCost(hex)
            if (budget < 0) break
            if (state.units[hex] == null) dest = hex
        }
        return dest
    }

    private fun fleetCentroid(allies: List<GameUnit>): HexCoord =
        HexCoord.round(
            allies.map { it.position.q }.average(),
            allies.map { it.position.r }.average(),
            allies.map { it.position.s }.average()
        )

    private fun enemyUnitsOf(state: GameState, aiFaction: Faction): List<GameUnit> {
        val rel = state.playerStates[aiFaction]?.relations
        return state.units.values.filter {
            it.faction != aiFaction &&
            (rel?.get(it.faction) == DiplomaticRelation.WAR || it.faction == Faction.ANCIENT_NPC)
        }
    }

    private fun enemyPlanetsOf(state: GameState, aiFaction: Faction): List<HexTile> {
        val rel = state.playerStates[aiFaction]?.relations
        return state.map.tiles.values.filter {
            it.terrain == TerrainType.PLANET && it.owner != aiFaction &&
            (it.owner == null || rel?.get(it.owner) != DiplomaticRelation.ALLIANCE)
        }
    }

    private fun effectiveMovement(state: GameState, faction: Faction, baseMovement: Int): Int =
        (baseMovement + BonusRegistry.sum(BonusType.MOVEMENT_MODIFIER, state.playerStates[faction], state.activeEvent))
            .coerceAtLeast(1)

    private fun evaluateHeroes(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        val selectedHero = chooseHero(state, playerState)

        if (selectedHero != null) {
            val newPlayerStates = state.playerStates.toMutableMap()
            newPlayerStates[faction] = playerState.copy(
                credits = playerState.credits - HeroCostCalculator.costFor(selectedHero, faction),
                recruitedHeroes = playerState.recruitedHeroes + selectedHero.id
            )
            return state.copy(playerStates = newPlayerStates)
        }
        return state
    }

    /**
     * Picks which hero to recruit among the affordable, not-yet-recruited ones. Replaces the old
     * fixed order (`KAEL > ELARA > VANCE > NIX`) that ignored both the faction and the situation:
     * a hero matching the faction's affinity ([Hero.targetFaction]) is strongly preferred, then the
     * pick follows posture — a faction at war wants Vance's damage, one at peace wants income/tech,
     * and a battered fleet wants Nix's healing. Cheaper heroes break ties.
     */
    internal fun chooseHero(state: GameState, playerState: PlayerState): Hero? {
        val affordable = HeroRegistry.ALL_HEROES.filter {
            !playerState.recruitedHeroes.contains(it.id) &&
                playerState.credits >= HeroCostCalculator.costFor(it, playerState.faction)
        }
        if (affordable.isEmpty()) return null

        val atWar = playerState.relations.values.any { it == DiplomaticRelation.WAR }
        val woundedFleet = state.units.values.any {
            it.faction == playerState.faction && it.currentHp < it.type.maxHp
        }
        return affordable.maxByOrNull { hero ->
            heroScore(hero, playerState.faction, atWar, woundedFleet) * 100 -
                HeroCostCalculator.costFor(hero, playerState.faction)
        }
    }

    private fun heroScore(hero: Hero, faction: Faction, atWar: Boolean, woundedFleet: Boolean): Int {
        // Affinity dominates: a hero aligned with the faction is the natural hire.
        val affinity = if (hero.targetFaction == faction) 10 else 0
        val situational = when (hero.id) {
            HeroRegistry.VANCE -> if (atWar) 8 else 2          // raw damage
            HeroRegistry.NIX -> if (woundedFleet) 8 else 1      // fleet healing
            HeroRegistry.ELARA -> if (atWar) 3 else 7           // income snowball
            HeroRegistry.KAEL -> if (atWar) 3 else 6            // tech discount
            else -> 2
        }
        return affinity + situational
    }

    private fun evaluateProduction(state: GameState, faction: Faction): GameState {
        val myPlanets = state.map.tiles.values.filter { it.owner == faction }
        if (myPlanets.isEmpty()) return state

        var nextState = state
        for (planet in myPlanets) {
            val pState = nextState.playerStates[faction] ?: continue
            if (pState.buildQueue.any { it.planetCoord == planet.coord }) continue
            val threatened = enemyUnitsOf(nextState, faction).any { it.position.distanceTo(planet.coord) <= 2 }
            val affordableUnit = chooseUnitToBuild(nextState, pState, threatened) ?: continue
            val turns = buildTurns(affordableUnit)
            val newPlayerStates = nextState.playerStates.toMutableMap()
            newPlayerStates[faction] = pState.copy(
                credits = pState.credits - affordableUnit.cost,
                buildQueue = pState.buildQueue + BuildOrder(affordableUnit, planet.coord, turns)
            )
            nextState = nextState.copy(playerStates = newPlayerStates)
        }
        return nextState
    }

    /**
     * Chooses what to lay down at a shipyard (P6). The old rule took the most expensive affordable
     * hull, so the AI emptied its treasury into dreadnoughts even at peace with nothing to fight.
     *
     * Now: a planet with enemies closing in gets a Defense Platform; at war the best raw fighter it
     * can afford; at peace the best value per credit, which favours cheap hulls and leaves money for
     * research and heroes. Defense Platforms are excluded from the peacetime pick — they cannot
     * expand, so stockpiling them while safe just freezes the empire.
     */
    internal fun chooseUnitToBuild(state: GameState, playerState: PlayerState, planetThreatened: Boolean): UnitType? {
        val affordable = UnitType.values().filter { it.cost <= playerState.credits }
        if (affordable.isEmpty()) return null

        if (planetThreatened) {
            affordable.firstOrNull { it == UnitType.DEFENSE_PLATFORM }?.let { return it }
        }
        val atWar = playerState.relations.values.any { it == DiplomaticRelation.WAR }
        return if (atWar) {
            affordable.maxByOrNull { combatScore(it) }
        } else {
            affordable.filter { it != UnitType.DEFENSE_PLATFORM }
                .maxByOrNull { combatScore(it).toDouble() / it.cost }
        }
    }

    /** Rough fighting value of a hull: firepower dominates, durability contributes. */
    private fun combatScore(type: UnitType): Int = type.attack * 2 + type.maxHp / 5

    private fun evaluateDiplomacy(
        state: GameState,
        faction: Faction,
        reduce: (GameState, GameIntent) -> GameState
    ): GameState {
        val aiPlayerState = state.playerStates[faction] ?: return state
        var nextState = state

        // Same strength metric the other side uses to weigh a proposal (DiplomacyEvaluator), so the
        // AI reasons about power exactly as its counterpart will.
        val aiPower = DiplomacyEvaluator.power(state, faction)

        for (otherFaction in Faction.values()) {
            if (otherFaction == faction || otherFaction == Faction.ANCIENT_NPC) continue
            state.playerStates[otherFaction] ?: continue
            val otherPower = DiplomacyEvaluator.power(state, otherFaction)

            val currentRelation = aiPlayerState.relations[otherFaction] ?: DiplomaticRelation.NEUTRAL

            when {
                otherPower > aiPower * 1.5 &&
                currentRelation != DiplomaticRelation.ALLIANCE &&
                currentRelation != DiplomaticRelation.WAR ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.ALLIANCE))

                aiPower > otherPower * 1.5 &&
                currentRelation != DiplomaticRelation.WAR &&
                currentRelation != DiplomaticRelation.ALLIANCE ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.WAR))

                currentRelation == DiplomaticRelation.WAR && otherPower > aiPower * 2.0 ->
                    nextState = reduce(nextState, GameIntent.ChangeRelation(otherFaction, DiplomaticRelation.NEUTRAL))
            }
        }
        return nextState
    }

    private fun evaluateEconomyAndTech(state: GameState, faction: Faction): GameState {
        val playerState = state.playerStates[faction] ?: return state
        // Route the AI through the same research pipeline as the player: queue one tech and pay
        // its cost, then let TurnManager tick it down over turns — no more instant unlocks.
        if (playerState.researchInProgress != null) return state
        val chosen = chooseResearchTech(state, playerState) ?: return state

        val cost = CostCalculator.techCost(chosen.id, playerState.techUnlocked, playerState, state.activeEvent, state.eventTargetFaction)
        val newPlayerStates = state.playerStates.toMutableMap()
        newPlayerStates[faction] = playerState.copy(
            credits = playerState.credits - cost,
            researchInProgress = ResearchProgress(chosen.id, chosen.tier + 1, costPaid = cost)
        )
        return state.copy(playerStates = newPlayerStates)
    }

    /**
     * Picks the tech to research from those available and affordable, weighted by the AI's
     * posture: a faction at war favours MILITARY, one at peace favours EXPANSION (economy);
     * EXPLORATION sits in between. Cheaper techs break ties. Replaces the old "first in
     * `ALL_TECHS`" pick that always rushed the military branch regardless of situation.
     */
    internal fun chooseResearchTech(state: GameState, playerState: PlayerState): TechDefinition? {
        fun cost(tech: TechDefinition) =
            CostCalculator.techCost(tech.id, playerState.techUnlocked, playerState, state.activeEvent, state.eventTargetFaction)
        val candidates = TechRegistry.ALL_TECHS.filter { tech ->
            val available = tech.requiresTechId == null || playerState.techUnlocked.contains(tech.requiresTechId)
            val unlocked = playerState.techUnlocked.contains(tech.id)
            available && !unlocked && playerState.credits >= cost(tech)
        }
        if (candidates.isEmpty()) return null
        val atWar = playerState.relations.values.any { it == DiplomaticRelation.WAR }
        return candidates.maxByOrNull { tech -> branchPriority(tech.branch, atWar) * 1000 - cost(tech) }
    }

    private fun branchPriority(branch: TechBranch, atWar: Boolean): Int = when (branch) {
        TechBranch.MILITARY -> if (atWar) 3 else 1
        TechBranch.EXPANSION -> if (atWar) 1 else 3
        TechBranch.EXPLORATION -> 2
    }
}
