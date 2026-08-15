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
    /**
     * Free-form target for objectives that need one. For [CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET]
     * this is the hex written as `"q,r"` (see `VictoryChecker.parseTargetCoord`).
     */
    val targetString: String = ""
)

@Serializable
data class CampaignMission(
    val id: String,
    val name: String,
    val description: String,
    val mapArchetype: MapArchetype,
    val mapSize: MapSize = MapSize.MEDIUM,
    val playerFaction: Faction,
    val enemyFaction: Faction,
    /** Head start granted to the scripted enemy — the per-mission difficulty dial. */
    val enemyBonusCredits: Int = 0,
    /** Deadline in turns; 0 means none. Past it the mission is failed. */
    val turnLimit: Int = 0,
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
            turnLimit = 40,
            objective = CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 500)
        ),
        // Exercises CAPTURE_SPECIFIC_PLANET, which had no implementation until now. The target is
        // KAELEN's own starting world: spawnPointsFor(5) hands index 4 — (-5,5,0) — to KAELEN, so
        // the planet is guaranteed to exist and to be enemy-held from turn one.
        CampaignMission(
            id = "mission_3",
            name = "The Kaelen Gate",
            description = "The Hegemony's seat lies at -5,5. Grind its defences to nothing and take it.",
            mapArchetype = MapArchetype.STANDARD,
            playerFaction = Faction.DOMINION,
            enemyFaction = Faction.KAELEN,
            enemyBonusCredits = 60,
            turnLimit = 40,
            objective = CampaignObjective(
                CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET,
                targetString = "-5,5"
            )
        )
    )
}
