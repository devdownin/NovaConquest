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

/**
 * What a mission hands its player before the first turn.
 *
 * Until now a mission could only vary map, factions, enemy purse and deadline — so every scenario
 * opened the same way, with the same ships and the same empty treasury. These are the levers that
 * make missions play at different *tempos*: a raid with three hulls and no economy is a different
 * game from a siege with one dreadnought, on the same map against the same enemy.
 *
 * Everything defaults to "nothing", so a mission that wants the standard opening writes nothing.
 */
@Serializable
data class MissionSetup(
    /** Replaces the standard starting treasury. Null keeps whatever a new board grants. */
    val startingCredits: Int? = null,
    /** Technologies already fielded on turn one. Must be `TechRegistry` ids. */
    val startingTechs: List<String> = emptyList(),
    /** Ships in service on turn one, placed at the player's capital. */
    val startingFleet: List<UnitType> = emptyList(),
    /**
     * Worlds already held, written as `"q,r"` like a capture objective.
     *
     * Only hexes that already carry a planet can be granted — see `applyLoadout`. Use coordinates
     * from `MapFactory.spawnPointsFor`, which are guaranteed to be planets on every seed.
     */
    val startingPlanets: List<String> = emptyList()
)

/** Who a scripted event falls on. Untargeted events ignore this. */
@Serializable
enum class EventTarget { PLAYER, ENEMY, GLOBAL }

/**
 * A galactic event fired at a fixed turn instead of rolled.
 *
 * `GalacticEvent` was purely random, so a mission could not build a dramatic beat — no "at turn 8
 * the ion storm hits". The effect machinery already exists; only the trigger was missing.
 *
 * A scripted beat **overrides** whatever is running. Waiting for a free slot would let a random
 * event swallow the mission's set piece, and it would vanish with no sign that anything was meant
 * to happen — the silent-failure shape this audit keeps finding.
 */
@Serializable
data class ScriptedEvent(
    /** Turn it fires on, matched exactly once when the round counter reaches it. */
    val turn: Int,
    val event: GalacticEvent,
    val duration: Int = 3,
    val target: EventTarget = EventTarget.GLOBAL
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
    val bonusObjectives: List<BonusObjective> = emptyList(),
    /** Scripted opening — see [MissionSetup]. Empty means the standard start. */
    val setup: MissionSetup = MissionSetup(),
    /** Dramatic beats fired at fixed turns — see [ScriptedEvent]. */
    val scriptedEvents: List<ScriptedEvent> = emptyList(),
    /**
     * Mission that must be completed first, or null when this one is available from the start.
     *
     * Enforced by `handleStartCampaign`, not only by the selection screen: gating in the UI alone
     * is the divergence this codebase keeps producing — the rule belongs to the engine, and the
     * screen merely shows it.
     */
    val requiresMissionId: String? = null,
    /** Shown before launch. Falls back to [description] when empty. */
    val briefing: String = "",
    /** Shown on the end screen when the mission is won. */
    val victoryText: String = "",
    /** Shown on the end screen when the mission is lost. */
    val defeatText: String = ""
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
            briefing = "Les avant-postes du Dominion s'étirent au-delà des routes connues. Le Xylar y " +
                "était déjà. Tenez quinze tours : la flotte de secours ne peut pas arriver plus tôt.",
            victoryText = "La nuée reflue. Ce que vous avez tenu n'était qu'un caillou au bord du vide — " +
                "mais le Dominion sait désormais que la frontière peut être tenue.",
            defeatText = "Le silence sur la fréquence de l'avant-poste dure depuis trois jours. " +
                "La frontière recule d'un secteur.",
            // Un pic dramatique reproductible à mi-parcours : la nuée frappe pendant que le joueur
            // croit avoir stabilisé sa ligne.
            scriptedEvents = listOf(
                ScriptedEvent(turn = 8, event = GalacticEvent.ION_STORM, duration = 3)
            ),
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
            requiresMissionId = "mission_1",
            briefing = "La nébuleuse de Cassian regorge de métaux rares, et la Hégémonie a déjà " +
                "affrété ses transports. Remplissez vos coffres avant elle — ou retirez-lui les mains.",
            victoryText = "Les coffres sont pleins et la Hégémonie repart les cales vides. " +
                "Le commerce, parfois, se fait sans un coup de feu.",
            defeatText = "Les convois kaelens quittent la nébuleuse chargés. Ce qu'ils emportent " +
                "financera la prochaine guerre — la vôtre.",
            // Deux beats qui se répondent : la ruée d'abord, la razzia ensuite, pile quand le joueur
            // a fini d'investir.
            scriptedEvents = listOf(
                ScriptedEvent(turn = 5, event = GalacticEvent.ECONOMIC_BOOM, duration = 4, target = EventTarget.PLAYER),
                ScriptedEvent(turn = 18, event = GalacticEvent.PIRATE_RAID, duration = 3, target = EventTarget.PLAYER)
            ),
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
            requiresMissionId = "mission_2",
            briefing = "Le siège de la Hégémonie est à dix secteurs, derrière tout ce qu'elle possède. " +
                "Vous partez avec une escadre et de quoi la réparer une fois. Il n'y aura pas de renforts.",
            victoryText = "La Porte est tombée. Ce que la Hégémonie gardait derrière elle depuis " +
                "quatre siècles vous appartient — et vous n'avez plus rien pour le défendre.",
            defeatText = "L'escadre n'a pas franchi le dernier verrou. Les archives de la Porte " +
                "resteront fermées encore longtemps.",
            // Deux beats à contretemps : l'éruption aveugle le joueur pendant l'approche, puis
            // l'ennemi reçoit une aide au moment du siège. La mission a une forme, pas une pente.
            scriptedEvents = listOf(
                ScriptedEvent(turn = 6, event = GalacticEvent.SOLAR_FLARE, duration = 3),
                ScriptedEvent(turn = 22, event = GalacticEvent.ECONOMIC_BOOM, duration = 5, target = EventTarget.ENEMY)
            ),
            // Tempo : un corps expéditionnaire, pas une économie. Le joueur arrive avec de quoi
            // frapper et presque rien pour reconstruire — la mission se joue sur ce qu'il amène.
            setup = MissionSetup(
                startingCredits = 40,
                startingTechs = listOf("tech_hull_plating"),
                startingFleet = listOf(UnitType.CRUISER, UnitType.CRUISER, UnitType.BATTLESHIP)
            ),
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
            briefing = "Deux enclumes, un marteau : la nuée frappe des deux côtés du corridor. " +
                "Tenez vingt tours sans laisser les caisses descendre sous trois cents.",
            victoryText = "Le corridor tient, et les comptes aussi. Rare combinaison.",
            defeatText = "Le corridor a cédé. Ce qui restait dans les caisses n'aura servi à personne.",
            // Trois vagues, de plus en plus rapprochées : la pression monte sans qu'aucune ligne de
            // code de moteur ne soit ajoutée.
            scriptedEvents = listOf(
                ScriptedEvent(turn = 6, event = GalacticEvent.PIRATE_RAID, duration = 3, target = EventTarget.PLAYER),
                ScriptedEvent(turn = 12, event = GalacticEvent.ION_STORM, duration = 3),
                ScriptedEvent(turn = 17, event = GalacticEvent.SOLAR_FLARE, duration = 4)
            ),
            // Tempo opposé à celui de la mission 3 : de quoi tenir, pas de quoi frapper. Le trésor
            // de départ est délibérément laissé au standard — augmenter les crédits ici affaiblirait
            // l'objectif économique de la mission elle-même.
            setup = MissionSetup(
                startingTechs = listOf("tech_deep_scanners"),
                startingFleet = listOf(UnitType.DEFENSE_PLATFORM, UnitType.DEFENSE_PLATFORM)
            ),
            objectives = listOf(
                CampaignObjective(CampaignObjectiveType.SURVIVE_TURNS, targetValue = 20),
                CampaignObjective(CampaignObjectiveType.ACCUMULATE_CREDITS, targetValue = 300)
            ),
            objectiveMode = ObjectiveMode.ALL
        )
    )
}
