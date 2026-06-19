package com.novaempire.core.engine

import com.novaempire.core.domain.models.BonusType
import com.novaempire.core.domain.models.DiplomaticRelation
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.HeroRegistry
import com.novaempire.core.domain.models.TechRegistry
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.BuildOrder
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.domain.state.PlayerState
import com.novaempire.core.domain.state.ResearchProgress
import com.novaempire.core.hex.HexPathfinder
import kotlin.random.Random

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

    if (path == null || path.isEmpty()) return GameResult(state, "Target position is unreachable or too far.")

    val preExplored = state.playerStates[unit.faction]?.exploredHexes ?: emptySet()
    val updatedUnits = state.units.toMutableMap()
    updatedUnits.remove(intent.from)
    updatedUnits[intent.to] = unit.copy(position = intent.to, hasMoved = true)
    val movedState = updateVision(state.copy(units = updatedUnits), setOf(unit.faction))
    val freshHexes = (movedState.playerStates[unit.faction]?.exploredHexes ?: emptySet()) - preExplored
    return if (freshHexes.isNotEmpty() && Random.nextFloat() < 0.3f) {
        applyExplorationDiscovery(movedState, unit.faction)
    } else {
        GameResult(movedState)
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

// ── Exploration discovery ─────────────────────────────────────────────────────

private fun applyExplorationDiscovery(state: GameState, faction: Faction): GameResult {
    val playerState = state.playerStates[faction] ?: return GameResult(state)
    return when (Random.nextInt(3)) {
        0 -> {
            val newPStates = state.playerStates.toMutableMap()
            newPStates[faction] = playerState.copy(credits = playerState.credits + 30)
            GameResult(state.copy(playerStates = newPStates), notification = "DISCOVERY: Ancient cache found — +30 Credits")
        }
        1 -> {
            val research = playerState.researchInProgress
            if (research != null && research.turnsRemaining > 1) {
                val newPStates = state.playerStates.toMutableMap()
                newPStates[faction] = playerState.copy(researchInProgress = research.copy(turnsRemaining = research.turnsRemaining - 1))
                GameResult(state.copy(playerStates = newPStates), notification = "DISCOVERY: Tech data recovered — research accelerated")
            } else GameResult(state, notification = "DISCOVERY: Star map fragment found")
        }
        else -> GameResult(state, notification = "DISCOVERY: Anomalous signal — location logged")
    }
}

// ── Carrier transport ─────────────────────────────────────────────────────────

internal fun handleLoadUnit(state: GameState, intent: GameIntent.LoadUnit): GameResult {
    val carrier = state.units[intent.carrierCoord] ?: return GameResult(state, "Carrier not found.")
    if (carrier.type != UnitType.CARRIER) return GameResult(state, "Only Carriers can load units.")
    IntentValidator.ownedByActive(carrier, state.activeFaction)?.let { return GameResult(state, it) }
    if (carrier.cargo.size >= 2) return GameResult(state, "Carrier at max capacity (2 units).")
    if (intent.carrierCoord.distanceTo(intent.unitCoord) > 1) return GameResult(state, "Unit not adjacent to carrier.")
    val unit = state.units[intent.unitCoord] ?: return GameResult(state, "Unit not found.")
    if (unit.faction != state.activeFaction) return GameResult(state, "Cannot load enemy units.")
    if (unit.type != UnitType.SCOUT && unit.type != UnitType.FIGHTER) return GameResult(state, "Only Scouts and Fighters can be loaded.")
    val newUnits = state.units.toMutableMap()
    newUnits.remove(intent.unitCoord)
    newUnits[intent.carrierCoord] = carrier.copy(cargo = carrier.cargo + unit.type)
    return GameResult(state.copy(units = newUnits))
}

internal fun handleDeployUnit(state: GameState, intent: GameIntent.DeployUnit): GameResult {
    val carrier = state.units[intent.carrierCoord] ?: return GameResult(state, "Carrier not found.")
    if (carrier.type != UnitType.CARRIER) return GameResult(state, "Not a carrier.")
    IntentValidator.ownedByActive(carrier, state.activeFaction)?.let { return GameResult(state, it) }
    if (intent.unitIndex < 0 || intent.unitIndex >= carrier.cargo.size) return GameResult(state, "Invalid cargo slot.")
    if (intent.carrierCoord.distanceTo(intent.deployCoord) > 2) return GameResult(state, "Deploy target too far (max 2 hexes).")
    if (state.units[intent.deployCoord] != null) return GameResult(state, "Target hex occupied.")
    val tile = state.map.tiles[intent.deployCoord]
    if (tile == null || !tile.terrain.isPassable) return GameResult(state, "Cannot deploy to impassable terrain.")
    val deployedType = carrier.cargo[intent.unitIndex]
    val newUnit = GameUnit(type = deployedType, faction = state.activeFaction, position = intent.deployCoord,
        currentHp = deployedType.maxHp, hasMoved = true)
    val newCargo = carrier.cargo.toMutableList().also { it.removeAt(intent.unitIndex) }
    val newUnits = state.units.toMutableMap()
    newUnits[intent.carrierCoord] = carrier.copy(cargo = newCargo)
    newUnits[intent.deployCoord] = newUnit
    return GameResult(updateVision(state.copy(units = newUnits), setOf(state.activeFaction)))
}

// ── Hero active abilities ─────────────────────────────────────────────────────

internal fun handleUseHeroAbility(state: GameState, intent: GameIntent.UseHeroAbility): GameResult {
    val playerState = state.activePlayer() ?: return GameResult(state, "Player not found.")
    if (!playerState.recruitedHeroes.contains(intent.heroId)) return GameResult(state, "Hero not recruited.")
    if (playerState.heroAbilitiesUsed.contains(intent.heroId)) return GameResult(state, "Ability already used this game.")
    val markUsed = playerState.heroAbilitiesUsed + intent.heroId
    return when (intent.heroId) {
        HeroRegistry.VANCE -> {
            val newUnits = state.units.mapValues { (_, u) ->
                if (u.faction == state.activeFaction && u.hasAttacked) u.copy(hasAttacked = false) else u
            }
            GameResult(state.withUpdatedPlayer(playerState.copy(heroAbilitiesUsed = markUsed)).copy(units = newUnits),
                notification = "VANCE: Frappe de Suppression — all fleet units may fire again")
        }
        HeroRegistry.ELARA -> {
            GameResult(state.withUpdatedPlayer(playerState.copy(credits = playerState.credits + 80, heroAbilitiesUsed = markUsed)),
                notification = "ELARA: Convoi Commercial — +80 Credits")
        }
        HeroRegistry.NIX -> {
            val newUnits = state.units.mapValues { (_, u) ->
                if (u.faction == state.activeFaction) u.copy(currentHp = u.type.maxHp) else u
            }
            GameResult(state.withUpdatedPlayer(playerState.copy(heroAbilitiesUsed = markUsed)).copy(units = newUnits),
                notification = "NIX: Refuge Stellaire — all units fully healed")
        }
        HeroRegistry.KAEL -> {
            val research = playerState.researchInProgress
            val newPlayer = if (research != null) playerState.copy(
                techUnlocked = playerState.techUnlocked + research.techId,
                researchInProgress = null,
                heroAbilitiesUsed = markUsed
            ) else playerState.copy(heroAbilitiesUsed = markUsed)
            val msg = if (research != null) "KAEL: Prototype — ${research.techId} research completed instantly"
                      else "KAEL: Prototype — no research in progress"
            GameResult(state.withUpdatedPlayer(newPlayer), notification = msg)
        }
        else -> GameResult(state, "Unknown hero ability.")
    }
}

// ── Build-turn lookup ─────────────────────────────────────────────────────────

internal fun buildTurns(unitType: com.novaempire.core.domain.models.UnitType): Int = when (unitType) {
    com.novaempire.core.domain.models.UnitType.SCOUT,
    com.novaempire.core.domain.models.UnitType.FIGHTER -> 2
    com.novaempire.core.domain.models.UnitType.CRUISER,
    com.novaempire.core.domain.models.UnitType.BATTLESHIP,
    com.novaempire.core.domain.models.UnitType.CARRIER,
    com.novaempire.core.domain.models.UnitType.DEFENSE_PLATFORM -> 2
    com.novaempire.core.domain.models.UnitType.DREADNOUGHT -> 3
}
