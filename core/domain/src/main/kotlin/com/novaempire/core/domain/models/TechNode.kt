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
    val tier: Int, // 1 to 5
    val baseCost: Int = 4,
    val requiresTechId: String? = null // For linear progression within a branch
)

object TechRegistry {
    val ALL_TECHS = listOf(
        // Military Branch
        TechDefinition("tech_hull_plating", "Hull Plating", TechBranch.MILITARY, 1),
        TechDefinition("tech_plasma_weapons", "Plasma Weapons", TechBranch.MILITARY, 2, requiresTechId = "tech_hull_plating"),
        TechDefinition("tech_siege_protocols", "Siege Protocols", TechBranch.MILITARY, 3, requiresTechId = "tech_plasma_weapons"),

        // Expansion Branch
        TechDefinition("tech_deep_scanners", "Deep Scanners", TechBranch.EXPANSION, 1),
        TechDefinition("tech_terraforming", "Terraforming", TechBranch.EXPANSION, 2, requiresTechId = "tech_deep_scanners"),
        TechDefinition("tech_wormhole_nav", "Wormhole Navigation", TechBranch.EXPANSION, 3, requiresTechId = "tech_terraforming"),

        // Exploration Branch
        TechDefinition("tech_long_range_sensors", "Long Range Sensors", TechBranch.EXPLORATION, 1),
        TechDefinition("tech_anomaly_analysis", "Anomaly Analysis", TechBranch.EXPLORATION, 2, requiresTechId = "tech_long_range_sensors")
    )

    fun getTech(id: String): TechDefinition? = ALL_TECHS.find { it.id == id }

    fun calculateCost(techId: String, unlockedTechs: Set<String>): Int {
        val tech = getTech(techId) ?: return 999
        val unlockedInBranch = ALL_TECHS.filter { it.branch == tech.branch && unlockedTechs.contains(it.id) }.size
        return tech.baseCost + (4 * unlockedInBranch) // Base 4C + 4C per tech owned in the same branch
    }
}
