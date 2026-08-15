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
    val grantsTechId: String? = null
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
        )
    )

    fun find(id: String): GloryPerk? = ALL_PERKS.find { it.id == id }

    /** Total glory cost of [perkIds]; unknown ids cost nothing because they grant nothing. */
    fun totalCost(perkIds: Set<String>): Int = perkIds.sumOf { find(it)?.cost ?: 0 }
}
