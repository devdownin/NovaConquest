package com.novaempire.core.domain.models

import kotlinx.serialization.Serializable

/**
 * Something a player buys with glory points before launching a campaign mission.
 *
 * The effects are **data, not code**: a perk declares what it grants, and `handleStartCampaign`
 * applies every field the same way for every perk. A `when (perk.id)` would have been shorter to
 * write and would have grown a silent hole the first time someone added an entry and forgot the
 * branch — the same defect that left `CAPTURE_SPECIFIC_PLANET` unwinnable.
 *
 * Adding a perk whose effect does not fit these fields means adding a field here and one line in
 * the handler, which is exactly where the compiler will point.
 */
@Serializable
data class GloryPerk(
    val id: String,
    val name: String,
    val description: String,
    /** Glory points spent. Deducted once, at launch. */
    val cost: Int,
    /** Added to the player's treasury at mission start. */
    val bonusCredits: Int = 0,
    /** Tech unlocked from turn one, skipping its research cost. Must be a `TechRegistry` id. */
    val grantsTechId: String? = null,
    /**
     * A ship in service on turn one, placed at the capital.
     *
     * Not the same thing as the credits to buy one: production takes turns, so this changes the
     * opening tempo rather than the balance sheet.
     */
    val grantsUnitType: UnitType? = null,
    /**
     * A hero already under contract. Must be a `HeroRegistry` id, and must be a mercenary
     * (`targetFaction == ANCIENT_NPC`) — a faction's own champion serving a rival mission would
     * contradict the affinity pricing the hero system is built on.
     */
    val grantsHeroId: String? = null,
    /**
     * Marks the whole map explored from turn one. Fog of war still applies: terrain and worlds are
     * known, enemy positions are not. This buys *planning*, which no amount of credits can.
     */
    val revealsMap: Boolean = false
)

object GloryRegistry {

    val ALL_PERKS = listOf(
        GloryPerk(
            id = "perk_war_chest",
            name = "War Chest",
            description = "Requisitioned funds: +150 credits at mission start.",
            cost = 2,
            bonusCredits = 150
        ),
        GloryPerk(
            id = "perk_prototype_hull",
            name = "Prototype Hulls",
            description = "Hull Plating is already fielded: +3 HP on every unit you build.",
            cost = 3,
            grantsTechId = "tech_hull_plating"
        ),
        GloryPerk(
            id = "perk_forward_scouts",
            name = "Forward Scouts",
            description = "Deep Scanners are already deployed: +1 vision range for all units.",
            cost = 3,
            grantsTechId = "tech_deep_scanners"
        ),

        // The three below change *how* a mission is played, not how much of something you hold.
        // The first three above are deliberately plain — they proved the economy without inventing
        // anything, and a catalogue of nothing but numbers makes glory a second currency rather
        // than a choice.
        GloryPerk(
            id = "perk_vanguard",
            name = "Vanguard Cruiser",
            description = "A cruiser already in service on turn one — production time you do not have to spend.",
            cost = 3,
            grantsUnitType = UnitType.CRUISER
        ),
        GloryPerk(
            id = "perk_star_charts",
            name = "Captured Star Charts",
            description = "The galaxy is mapped from turn one. Terrain and worlds are known; enemy fleets are not.",
            cost = 2,
            revealsMap = true
        ),
        GloryPerk(
            id = "perk_seer_contract",
            name = "The Seer's Contract",
            description = "High Seer Nix serves from turn one: the fleet repairs 1 HP each turn.",
            cost = 4,
            // Nix is the mercenary of the roster, so this reads the same for every faction.
            grantsHeroId = "hero_nix"
        )
    )

    fun find(id: String): GloryPerk? = ALL_PERKS.find { it.id == id }

    /** Total glory cost of [perkIds]; unknown ids cost nothing because they grant nothing. */
    fun totalCost(perkIds: Set<String>): Int = perkIds.sumOf { find(it)?.cost ?: 0 }
}
