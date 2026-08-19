package com.novaempire.core.domain.state
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent
import com.novaempire.core.domain.models.GameMap
import com.novaempire.core.domain.models.GameUnit
import com.novaempire.core.domain.models.UnitType
import com.novaempire.core.hex.HexCoord

@Serializable
data class CombatEvent(
    val attackerCoord: HexCoord,
    val defenderCoord: HexCoord,
    val targetDestroyed: Boolean,
    /** Ce que le défenseur a encaissé du tir. */
    val defenderHit: CombatHit? = null,
    /**
     * Ce que l'attaquant a encaissé en retour.
     *
     * `null` veut dire « pas de riposte » — le défenseur est mort, ou l'attaquant tirait de plus
     * loin que sa portée. C'est une information différente d'une riposte à zéro dégât, et
     * l'interface les montre différemment.
     */
    val attackerHit: CombatHit? = null
)

/**
 * Ce qu'un camp a encaissé dans un échange : de quoi afficher un chiffre et vider une barre.
 *
 * [hpBefore] accompagne [hpAfter] parce que l'unité touchée peut avoir quitté l'état au moment où
 * l'interface anime le tir — une cible détruite n'a plus de points de vie à consulter.
 */
@Serializable
data class CombatHit(
    val damage: Int,
    val hpBefore: Int,
    val hpAfter: Int,
    val maxHp: Int
)

@Serializable
data class GameState(
    val version: Int = 1,
    // Le thème vivait ici, donc dans chaque sauvegarde. C'est une préférence d'application : elle
    // est désormais persistée à part (`SettingsStore`), ce qui la rend disponible dès le menu
    // principal et la sort du format de sauvegarde. Les anciennes sauvegardes portent encore la clé
    // `themeConfig` ; `ignoreUnknownKeys` la laisse tomber sans migration.
    val turn: Int = 1,
    val activeFaction: Faction = Faction.DOMINION,
    val humanFaction: Faction = Faction.DOMINION,
    val playerStates: Map<Faction, PlayerState> = emptyMap(),
    val campaignState: com.novaempire.core.domain.state.CampaignState = com.novaempire.core.domain.state.CampaignState(),
    val map: GameMap = GameMap(),
    val units: Map<HexCoord, GameUnit> = emptyMap(),
    val activeEvent: GalacticEvent = GalacticEvent.NONE,
    val eventDurationRemaining: Int = 0,
    /** For a targeted event, the single faction it affects; null for global events / no event. */
    val eventTargetFaction: Faction? = null,
    val winner: Faction? = null,
    val victoryReason: String? = null,
    val dominationTurns: Map<Faction, Int> = emptyMap()
)

@Serializable
data class ResearchProgress(
    val techId: String,
    val turnsRemaining: Int,
    /** Credits actually spent to start this research — refunded (in part) if it is cancelled. */
    val costPaid: Int = 0
)

@Serializable
data class BuildOrder(
    val unitType: UnitType,
    val planetCoord: HexCoord,
    val turnsRemaining: Int,
    /**
     * True when the order finished but had nowhere to place the ship (every candidate hex occupied).
     * It retries each turn; the flag lets the UI say so instead of showing an order that silently
     * never completes. Defaulted so existing saves keep loading.
     */
    val blocked: Boolean = false
)

@Serializable
data class PlayerState(
    val faction: Faction,
    val credits: Int = 10,
    val techUnlocked: Set<String> = emptySet(),
    val researchInProgress: ResearchProgress? = null,
    val buildQueue: List<BuildOrder> = emptyList(),
    val exploredHexes: Set<HexCoord> = emptySet(),
    @Transient val visibleHexes: Set<HexCoord> = emptySet(),
    val capitalCoord: HexCoord? = null,
    val recruitedHeroes: Set<String> = emptySet(),
    val heroAbilitiesUsed: Set<String> = emptySet(),
    val relations: Map<Faction, com.novaempire.core.domain.models.DiplomaticRelation> = emptyMap()
)
