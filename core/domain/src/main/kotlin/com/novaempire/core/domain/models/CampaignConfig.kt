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

/** How a mission's list of required objectives combines into "won". */
@Serializable
enum class ObjectiveMode {
    /** Every objective must be met — a checklist. */
    ALL,

    /** Any one objective ends the mission — parallel routes to the same victory. */
    ANY
}

/**
 * An objective that never gates victory and pays glory when it is met.
 *
 * Checked **at the moment the mission is won**, not tracked across the run: "hold 300 credits" means
 * holding them at the end, not having passed through 300 at some point. That matches how the primary
 * objectives already read the current state, and it keeps a side objective from needing bookkeeping
 * inside `GameState` — which would have to be serialized, defaulted, and migrated.
 */
@Serializable
data class BonusObjective(
    val objective: CampaignObjective,
    /** Extra glory paid on top of the mission's own reward. First completion only, like the rest. */
    val gloryReward: Int
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
    /**
     * Glory awarded the **first** time this mission is completed. Later replays award nothing, so
     * the easiest mission cannot be farmed for perks.
     */
    val gloryReward: Int = 0,
    /**
     * What must be achieved, combined per [objectiveMode]. Must not be empty: an empty checklist
     * under [ObjectiveMode.ALL] reads as "everything is done" and wins the mission on turn one.
     * `CampaignTest` guards this.
     */
    val objectives: List<CampaignObjective>,
    val objectiveMode: ObjectiveMode = ObjectiveMode.ALL,
    /** Optional side goals, each paying its own glory. Never required to win. */
    val bonusObjectives: List<BonusObjective> = emptyList()
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
            gloryReward = 2,
            objectives = listOf(CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, targetValue = 15)),
            // A first mission should teach the idea without punishing anyone who ignores it: hold a
            // treasury while surviving, worth one point.
            bonusObjectives = listOf(
                BonusObjective(
                    CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 120),
                    gloryReward = 1
                )
            )
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
            gloryReward = 3,
            // Two ways out: bank the money, or remove the rival who was going to take it. ANY makes
            // the mission a choice of strategy rather than a single script.
            objectives = listOf(
                CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 500),
                CampaignObjective(CampaignObjectiveType.DEFEAT_FACTION)
            ),
            objectiveMode = ObjectiveMode.ANY
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
            gloryReward = 4,
            objectives = listOf(
                CampaignObjective(CampaignObjectiveType.CAPTURE_SPECIFIC_PLANET, targetString = "-5,5")
            ),
            bonusObjectives = listOf(
                // Taking the gate quickly is worth more than taking it at all costs.
                BonusObjective(
                    CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 250),
                    gloryReward = 2
                )
            )
        ),
        // Exercises ObjectiveMode.ALL — a checklist rather than a single goal. Deliberately carries
        // no coordinate: a wrong hex is the one data error the integrity tests cannot catch, and
        // this mission exists to prove the combination logic, not the map.
        CampaignMission(
            id = "mission_4",
            name = "Twin Anvils",
            description = "Hold the line and fill the vaults: outlast the Swarm for 20 turns without letting the treasury fall below 300.",
            mapArchetype = MapArchetype.NEBULA_EXPANSE,
            playerFaction = Faction.DOMINION,
            enemyFaction = Faction.XYLAR,
            enemyBonusCredits = 80,
            turnLimit = 35,
            gloryReward = 4,
            objectives = listOf(
                CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, targetValue = 20),
                CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 300)
            ),
            objectiveMode = ObjectiveMode.ALL
        )
    )
}
