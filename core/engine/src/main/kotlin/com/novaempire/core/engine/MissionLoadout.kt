package com.novaempire.core.engine

import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.hex.HexCoord

/**
 * Everything a campaign launch can hand a faction before its first turn.
 *
 * Two very different things produce one of these — the mission's scripted starting conditions and
 * the glory perks the player bought — and they must be applied identically. Before this existed the
 * perks had their own application code inside `handleStartCampaign`; adding scripted setups would
 * have meant a second copy, and the two would have drifted the first time either grew a field.
 */
internal data class Loadout(
    /** Replaces the treasury outright. The mission's base; null leaves whatever the board gave. */
    val setCredits: Int? = null,
    /** Added on top of whatever the treasury holds. Perks stack on the mission's base this way. */
    val addCredits: Int = 0,
    val techs: List<String> = emptyList(),
    val heroes: List<String> = emptyList(),
    val units: List<UnitType> = emptyList(),
    val planets: List<HexCoord> = emptyList(),
    val revealMap: Boolean = false
) {
    val isEmpty: Boolean
        get() = setCredits == null && addCredits == 0 && techs.isEmpty() && heroes.isEmpty() &&
            units.isEmpty() && planets.isEmpty() && !revealMap
}

/**
 * Reads a mission's scripted opening into the shared shape.
 *
 * Unparsable planet coordinates are dropped here rather than failing the launch — the same
 * tolerance `parseTargetCoord` applies to capture objectives, for the same reason: a typo in
 * mission data must not throw in the middle of starting a game. A registry test catches it instead.
 */
internal fun com.novaempire.core.domain.models.MissionSetup.toLoadout(): Loadout = Loadout(
    setCredits = startingCredits,
    techs = startingTechs,
    units = startingFleet,
    planets = startingPlanets.mapNotNull { VictoryChecker.parseTargetCoord(it) }
)

/**
 * Hands [loadout] to [faction], returning the new state.
 *
 * Order matters and is fixed here rather than at the call sites: the treasury is set before it is
 * added to, so a mission's base and a perk's bonus compose predictably however they are combined.
 */
internal fun applyLoadout(state: GameState, faction: com.novaempire.core.domain.models.Faction, loadout: Loadout): GameState {
    if (loadout.isEmpty) return state

    var next = state

    val players = next.playerStates.toMutableMap()
    players[faction]?.let { p ->
        players[faction] = p.copy(
            credits = (loadout.setCredits ?: p.credits) + loadout.addCredits,
            techUnlocked = p.techUnlocked + loadout.techs,
            recruitedHeroes = p.recruitedHeroes + loadout.heroes,
            // Explored, not visible: the fog still hides fleets, so this grants planning rather
            // than intelligence. `updateVision` only ever adds to exploredHexes, so seeding it
            // here survives every later recompute.
            exploredHexes = if (loadout.revealMap) p.exploredHexes + next.map.tiles.keys else p.exploredHexes
        )
    }
    next = next.copy(playerStates = players)

    // Pre-owned worlds. Only hexes that already carry a planet are handed over: turning arbitrary
    // terrain into a world would let a mission drop one inside an asteroid field, which
    // `MapFactory`'s connectivity pass never promised to keep reachable. Mission data should use
    // coordinates from `MapFactory.spawnPointsFor`, the same convention CAPTURE_SPECIFIC_PLANET
    // already follows — an unusable coordinate leaves the world alone rather than failing a launch
    // that has already reset the board.
    if (loadout.planets.isNotEmpty()) {
        val tiles = next.map.tiles.toMutableMap()
        for (coord in loadout.planets) {
            val tile = tiles[coord] ?: continue
            if (tile.terrain != TerrainType.PLANET) continue
            tiles[coord] = tile.copy(owner = faction)
        }
        next = next.copy(map = next.map.copy(tiles = tiles))
    }

    // Ships touch the board, so they are placed once the player state has settled. A capital ringed
    // by ships or asteroids simply yields nowhere to stand and the extra hull is dropped: refusing
    // the launch at this point — board already built — would be worse. `UnitPlacement` is shared
    // with the build queue so the two can never disagree about what a free hex is.
    val capital = next.playerStates[faction]?.capitalCoord
    if (capital != null && loadout.units.isNotEmpty()) {
        val units = next.units.toMutableMap()
        for (type in loadout.units) {
            val hex = UnitPlacement.freeHexNear(next.copy(units = units), capital) ?: continue
            units[hex] = GameUnit(type = type, faction = faction, position = hex, currentHp = type.maxHp)
        }
        next = next.copy(units = units)
    }

    return next
}
