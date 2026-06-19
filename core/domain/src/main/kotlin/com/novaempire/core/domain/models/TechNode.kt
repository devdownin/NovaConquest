package com.novaempire.core.domain.models

enum class TechBranch {
    MILITARY,
    EXPANSION,
    EXPLORATION
}

data class TechDefinition(
    val id: String,
    val name: String,
    val branch: TechBranch,
    val tier: Int,
    val baseCost: Int = 8,
    val requiresTechId: String? = null,
    val description: String = "",
    val bonuses: List<BonusModifier> = emptyList()
)

object TechRegistry {
    const val DEEP_SCANNERS = "tech_deep_scanners"

    val ALL_TECHS = listOf(
        // Military Branch
        TechDefinition("tech_hull_plating", "Hull Plating", TechBranch.MILITARY, 1,
            description = "+3 HP on newly built units",
            bonuses = listOf(BonusModifier(BonusType.UNIT_HP_ON_SPAWN, 3))),
        TechDefinition("tech_plasma_weapons", "Plasma Weapons", TechBranch.MILITARY, 2,
            requiresTechId = "tech_hull_plating",
            description = "+2 attack damage per strike",
            bonuses = listOf(BonusModifier(BonusType.ATTACK_FLAT, 2))),
        TechDefinition("tech_siege_protocols", "Siege Protocols", TechBranch.MILITARY, 3,
            requiresTechId = "tech_plasma_weapons",
            description = "+1 siege damage per attack",
            bonuses = listOf(BonusModifier(BonusType.SIEGE_DAMAGE, 1))),

        // Expansion Branch
        TechDefinition("tech_deep_scanners", "Deep Scanners", TechBranch.EXPANSION, 1,
            description = "+1 vision range for all units",
            bonuses = listOf(BonusModifier(BonusType.VISION_RANGE, 1))),
        TechDefinition("tech_terraforming", "Terraforming", TechBranch.EXPANSION, 2,
            requiresTechId = "tech_deep_scanners",
            description = "Captured planets start at Level 2",
            bonuses = listOf(BonusModifier(BonusType.CAPTURE_START_LEVEL, 1))),
        TechDefinition("tech_wormhole_nav", "Wormhole Navigation", TechBranch.EXPANSION, 3,
            requiresTechId = "tech_terraforming",
            description = "Unlocks wormhole transit between distant systems"),

        // Exploration Branch
        TechDefinition("tech_long_range_sensors", "Long Range Sensors", TechBranch.EXPLORATION, 1,
            description = "+1 vision range for Scouts",
            bonuses = listOf(BonusModifier(BonusType.SCOUT_VISION_RANGE, 1))),
        TechDefinition("tech_anomaly_analysis", "Anomaly Analysis", TechBranch.EXPLORATION, 2,
            requiresTechId = "tech_long_range_sensors",
            description = "Galactic events end 2 turns sooner"),

        // Tier 4 techs — late-game specialisations
        TechDefinition("tech_nano_armor", "Nano Armor", TechBranch.MILITARY, 4,
            requiresTechId = "tech_siege_protocols",
            description = "+5 HP on newly built units",
            bonuses = listOf(BonusModifier(BonusType.UNIT_HP_ON_SPAWN, 5))),
        TechDefinition("tech_stellar_mining", "Stellar Mining", TechBranch.EXPANSION, 4,
            requiresTechId = "tech_wormhole_nav",
            description = "+15 credits income per turn",
            bonuses = listOf(BonusModifier(BonusType.INCOME_FLAT, 15))),
        TechDefinition("tech_dark_matter_scan", "Dark Matter Scanning", TechBranch.EXPLORATION, 3,
            requiresTechId = "tech_anomaly_analysis",
            description = "+1 vision range for all units",
            bonuses = listOf(BonusModifier(BonusType.VISION_RANGE, 1))),
        TechDefinition("tech_quantum_relay", "Quantum Relay", TechBranch.EXPLORATION, 4,
            requiresTechId = "tech_dark_matter_scan",
            description = "+20% credits income",
            bonuses = listOf(BonusModifier(BonusType.INCOME_PERCENT, 20))),
    )

    fun getTech(id: String): TechDefinition? = ALL_TECHS.find { it.id == id }

    fun baseCost(techId: String, unlockedTechs: Set<String>): Int {
        val tech = getTech(techId) ?: return 999
        val unlockedInBranch = ALL_TECHS.count { it.branch == tech.branch && unlockedTechs.contains(it.id) }
        return tech.baseCost + (6 * unlockedInBranch)
    }

    fun bonusesFor(techIds: Set<String>): List<BonusModifier> =
        techIds.flatMap { id -> getTech(id)?.bonuses ?: emptyList() }
}
