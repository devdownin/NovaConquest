package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.hex.HexPathfinder

// ── Vision ───────────────────────────────────────────────────────────────────

internal fun updateVision(
    state: GameState,
    factions: Collection<Faction> = Faction.values().asList()
): GameState {
    val updatedPlayers = state.playerStates.toMutableMap()
    for (faction in factions) {
        val playerState = updatedPlayers[faction] ?: PlayerState(faction)
        val visibleNow = VisionSystem.calculateVisibleHexes(state, faction)
        updatedPlayers[faction] = playerState.copy(
            visibleHexes = visibleNow,
            exploredHexes = playerState.exploredHexes + visibleNow
        )
    }
    return state.copy(playerStates = updatedPlayers)
}

// ── Shared helpers ────────────────────────────────────────────────────────────

private fun GameState.activePlayer(): PlayerState? = playerStates[activeFaction]

private fun GameState.withUpdatedPlayer(player: PlayerState): GameState {
    val map = playerStates.toMutableMap()
    map[activeFaction] = player
    return copy(playerStates = map)
}

// ── Intent handlers ───────────────────────────────────────────────────────────

internal fun handleMoveUnit(state: GameState, intent: GameIntent.MoveUnit): GameResult {
    val unit = state.units[intent.from] ?: return GameResult(state, "No unit at selected position.")
    IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
    IntentValidator.notMoved(unit)?.let { return GameResult(state, it) }

    val gridMap = GameGridMap(state, state.activeFaction)
    val moveMod = BonusRegistry.sum(BonusType.MOVEMENT_MODIFIER, state.activePlayer(), state.activeEvent)
    val effectiveMovement = (unit.type.movement + moveMod).coerceAtLeast(1)
    val path = HexPathfinder.findPath(intent.from, intent.to, gridMap, effectiveMovement)

    return if (path != null && path.isNotEmpty()) {
        val updatedUnits = state.units.toMutableMap()
        updatedUnits.remove(intent.from)
        updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
        GameResult(updateVision(state.copy(units = updatedUnits), setOf(unit.faction)))
    } else {
        GameResult(state, "Target position is unreachable or too far.")
    }
}

internal fun handleAttackUnit(state: GameState, intent: GameIntent.AttackUnit, deps: GameEngineDependencies): GameResult {
    val unit = state.units[intent.attacker] ?: return GameResult(state, "Attacker not found.")
    IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
    IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
    val defender = state.units[intent.defender] ?: return GameResult(state, "Target not found.")
    if (intent.attacker.distanceTo(intent.defender) > unit.type.range)
        return GameResult(state, "Target is out of range.")
    return GameResult(updateVision(
        deps.combatSystem.resolveCombat(state, intent.attacker, intent.defender),
        setOfNotNull(unit.faction, defender.faction)
    ))
}

internal fun handleResearchTech(state: GameState, intent: GameIntent.ResearchTech): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    if (playerState.researchInProgress != null) return GameResult(state, "Research already in progress.")
    val tech = TechRegistry.getTech(intent.techId) ?: return GameResult(state, "Technology not found.")
    if (tech.requiresTechId != null && !playerState.techUnlocked.contains(tech.requiresTechId))
        return GameResult(state, "Prerequisite technology not researched.")
    if (playerState.techUnlocked.contains(intent.techId))
        return GameResult(state, "Technology already researched.")
    val cost = CostCalculator.techCost(intent.techId, playerState.techUnlocked, playerState, state.activeEvent)
    IntentValidator.canAfford(playerState, cost)?.let { return GameResult(state, it) }
    return GameResult(state.withUpdatedPlayer(playerState.copy(
        credits = playerState.credits - cost,
        researchInProgress = ResearchProgress(intent.techId, tech.tier + 1)
    )))
}

internal fun handleBuildUnit(state: GameState, intent: GameIntent.BuildUnit): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    val planetCoord = intent.location ?: playerState.capitalCoord ?: return GameResult(state, "No valid spawn location.")
    IntentValidator.canAfford(playerState, intent.unitType.cost)?.let { return GameResult(state, it) }
    if (playerState.buildQueue.any { it.planetCoord == planetCoord })
        return GameResult(state, "Already producing a unit at this location.")
    val turns = buildTurns(intent.unitType)
    return GameResult(state.withUpdatedPlayer(playerState.copy(
        credits = playerState.credits - intent.unitType.cost,
        buildQueue = playerState.buildQueue + BuildOrder(intent.unitType, planetCoord, turns)
    )))
}

internal fun handleRecruitHero(state: GameState, intent: GameIntent.RecruitHero): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    val hero = HeroRegistry.getHero(intent.heroId) ?: return GameResult(state, "Hero not found.")
    IntentValidator.canAfford(playerState, hero.cost)?.let { return GameResult(state, it) }
    if (playerState.recruitedHeroes.contains(hero.id)) return GameResult(state, "Hero already recruited.")
    return GameResult(state.withUpdatedPlayer(playerState.copy(
        credits = playerState.credits - hero.cost,
        recruitedHeroes = playerState.recruitedHeroes + hero.id
    )))
}

internal fun handleChangeRelation(state: GameState, intent: GameIntent.ChangeRelation): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    val newPlayerStates = state.playerStates.toMutableMap()
    newPlayerStates[state.activeFaction] = playerState.copy(
        relations = playerState.relations.toMutableMap().also { it[intent.targetFaction] = intent.newRelation }
    )
    val targetState = newPlayerStates[intent.targetFaction]
    if (targetState != null) {
        newPlayerStates[intent.targetFaction] = targetState.copy(
            relations = targetState.relations.toMutableMap().also { it[state.activeFaction] = intent.newRelation }
        )
    }
    return GameResult(state.copy(playerStates = newPlayerStates))
}

internal fun handleSiegePlanet(state: GameState, intent: GameIntent.SiegePlanet, deps: GameEngineDependencies): GameResult {
    val unit = state.units[intent.attackerCoord] ?: return GameResult(state, "Attacker not found.")
    IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
    IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
    IntentValidator.isPlanet(state, intent.planetCoord)?.let { return GameResult(state, it) }
    if (state.map.tiles[intent.planetCoord]?.owner == state.activeFaction)
        return GameResult(state, "You cannot siege your own planet.")
    return GameResult(deps.combatSystem.siegePlanet(state, intent.attackerCoord, intent.planetCoord))
}

internal fun handleCapturePlanet(state: GameState, intent: GameIntent.CapturePlanet, deps: GameEngineDependencies): GameResult {
    val unit = state.units[intent.unitCoord] ?: return GameResult(state, "Unit not found.")
    IntentValidator.ownedByActive(unit, state.activeFaction)?.let { return GameResult(state, it) }
    IntentValidator.notAttacked(unit)?.let { return GameResult(state, it) }
    IntentValidator.isPlanet(state, intent.planetCoord)?.let { return GameResult(state, it) }
    val tile = state.map.tiles[intent.planetCoord]!!
    if (tile.systemLevel > 0) return GameResult(state, "Planet must be at level 0 to be captured.")
    if (tile.owner == state.activeFaction) return GameResult(state, "You already own this planet.")
    return GameResult(deps.combatSystem.capturePlanet(state, intent.unitCoord, intent.planetCoord))
}

internal fun handleUpgradeSystem(state: GameState, intent: GameIntent.UpgradeSystem): GameResult {
    val tile = state.map.tiles[intent.coord] ?: return GameResult(state, "Tile not found.")
    IntentValidator.isPlanet(state, intent.coord)?.let { return GameResult(state, it) }
    if (tile.owner != state.activeFaction) return GameResult(state, "You don't own this planet.")
    if (tile.systemLevel >= 5) return GameResult(state, "Planet already at maximum level.")
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    val upgradeCost = (tile.systemLevel + 1) * 15
    IntentValidator.canAfford(playerState, upgradeCost)?.let { return GameResult(state, it) }
    val newTiles = state.map.tiles.toMutableMap()
    newTiles[intent.coord] = tile.copy(systemLevel = tile.systemLevel + 1)
    return GameResult(state.withUpdatedPlayer(playerState.copy(credits = playerState.credits - upgradeCost))
        .copy(map = state.map.copy(tiles = newTiles)))
}

internal fun handleCancelBuild(state: GameState, intent: GameIntent.CancelBuild): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player state not found.")
    val order = playerState.buildQueue.firstOrNull { it.planetCoord == intent.planetCoord }
        ?: return GameResult(state, "No build order at this location.")
    val refund = order.unitType.cost / 2
    return GameResult(state.withUpdatedPlayer(playerState.copy(
        credits = playerState.credits + refund,
        buildQueue = playerState.buildQueue.filter { it.planetCoord != intent.planetCoord }
    )))
}

// ── Build-turn lookup ─────────────────────────────────────────────────────────

internal fun buildTurns(unitType: com.novaempire.core.domain.models.UnitType): Int = when (unitType) {
    com.novaempire.core.domain.models.UnitType.SCOUT,
    com.novaempire.core.domain.models.UnitType.FIGHTER -> 1
    com.novaempire.core.domain.models.UnitType.CRUISER,
    com.novaempire.core.domain.models.UnitType.BATTLESHIP,
    com.novaempire.core.domain.models.UnitType.CARRIER,
    com.novaempire.core.domain.models.UnitType.DEFENSE_PLATFORM -> 2
    com.novaempire.core.domain.models.UnitType.DREADNOUGHT -> 3
}
