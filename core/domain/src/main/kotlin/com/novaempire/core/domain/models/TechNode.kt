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
    val baseCost: Int = 4,
    val requiresTechId: String? = null,
    val description: String = ""
)

object TechRegistry {
    const val DEEP_SCANNERS = "tech_deep_scanners"

    val ALL_TECHS = listOf(
        // Military Branch
        TechDefinition("tech_hull_plating", "Hull Plating", TechBranch.MILITARY, 1, description = "+3 HP on newly built units"),
        TechDefinition("tech_plasma_weapons", "Plasma Weapons", TechBranch.MILITARY, 2, requiresTechId = "tech_hull_plating", description = "+2 attack damage per strike"),
        TechDefinition("tech_siege_protocols", "Siege Protocols", TechBranch.MILITARY, 3, requiresTechId = "tech_plasma_weapons", description = "+1 siege damage per attack"),

        // Expansion Branch
        TechDefinition("tech_deep_scanners", "Deep Scanners", TechBranch.EXPANSION, 1, description = "+1 vision range for all units"),
        TechDefinition("tech_terraforming", "Terraforming", TechBranch.EXPANSION, 2, requiresTechId = "tech_deep_scanners", description = "Captured planets start at Level 2"),
        TechDefinition("tech_wormhole_nav", "Wormhole Navigation", TechBranch.EXPANSION, 3, requiresTechId = "tech_terraforming", description = "Unlocks wormhole transit between distant systems"),

        // Exploration Branch
        TechDefinition("tech_long_range_sensors", "Long Range Sensors", TechBranch.EXPLORATION, 1, description = "+1 vision range for Scouts"),
        TechDefinition("tech_anomaly_analysis", "Anomaly Analysis", TechBranch.EXPLORATION, 2, requiresTechId = "tech_long_range_sensors", description = "Galactic events end 2 turns sooner")
    )

    fun getTech(id: String): TechDefinition? = ALL_TECHS.find { it.id == id }

    fun baseCost(techId: String, unlockedTechs: Set<String>): Int {
        val tech = getTech(techId) ?: return 999
        val unlockedInBranch = ALL_TECHS.count { it.branch == tech.branch && unlockedTechs.contains(it.id) }
        return tech.baseCost + (4 * unlockedInBranch)
    }
}
