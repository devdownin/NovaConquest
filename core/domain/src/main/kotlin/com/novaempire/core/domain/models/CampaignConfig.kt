package com.novaempire.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class CampaignObjectiveType {
    SURVIVE_TURNS,
    CAPTURE_SPECIFIC_PLANET,
    ACCUMULATE_CREDITS,
    DEFEAT_FACTION
}

@Serializable
data class CampaignObjective(
    val type: CampaignObjectiveType,
    val targetValue: Int = 0,
    val targetString: String = ""
)

@Serializable
data class CampaignMission(
    val id: String,
    val name: String,
    val description: String,
    val mapArchetype: MapArchetype,
    val playerFaction: Faction,
    val enemyFaction: Faction,
    val enemyBonusCredits: Int = 0,
    val objective: CampaignObjective
)

object CampaignRegistry {
    val MISSIONS = listOf(
        CampaignMission(
            id = "mission_1",
            name = "The Awakening",
            description = "The Dominion expands into the outer rim. Survive the initial Xylar Swarm attacks for 15 turns.",
            mapArchetype = MapArchetype.STANDARD,
            playerFaction = Faction.DOMINION,
            enemyFaction = Faction.XYLAR,
            objective = CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, targetValue = 15)
        ),
        CampaignMission(
            id = "mission_2",
            name = "Stolen Riches",
            description = "The Free Traders have discovered a lucrative nebula. Accumulate 500 Credits before the Kaelen Hegemony steals them.",
            mapArchetype = MapArchetype.ZODIAC,
            playerFaction = Faction.TRADERS,
            enemyFaction = Faction.KAELEN,
            enemyBonusCredits = 100,
            objective = CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 500)
        )
    )
}
