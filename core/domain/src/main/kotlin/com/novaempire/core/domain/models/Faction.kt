package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class Faction(
    val displayName: String,
    val description: String,
    val bonusCredits: Int = 0,
    val bonusMovement: Int = 0,
    val bonusTechDiscount: Float = 0f,
    val bonusVision: Int = 0,
    val bonusAttack: Float = 0f,
    /**
     * Identity mechanics beyond the five legacy numeric fields (which the UI reads directly for
     * combat/movement/income previews). These are applied engine-side through [bonusModifiers].
     */
    val extraBonuses: List<BonusModifier> = emptyList()
) {
    // Elite military: hits harder AND ships come off the line tougher.
    DOMINION("Terran Dominion", "Elite fleets. +10% Attack; newly built ships gain +3 HP.",
        bonusAttack = 0.10f,
        extraBonuses = listOf(BonusModifier(BonusType.UNIT_HP_ON_SPAWN, 3))),
    // Mercantile: flat income plus a percentage multiplier — a snowballing economy.
    TRADERS("Free Traders", "Masters of commerce. +5 Credits/turn and +15% income.",
        bonusCredits = 5,
        extraBonuses = listOf(BonusModifier(BonusType.INCOME_PERCENT, 15))),
    // Research: cheapest techs and they finish sooner.
    SYNTH("Synth Collective", "Rapid advancement. -15% tech cost; research completes faster.",
        bonusTechDiscount = 0.15f,
        extraBonuses = listOf(BonusModifier(BonusType.RESEARCH_SPEED, 1))),
    // Wandering fleets: fast and cheap to sustain — big roaming armadas.
    NOMADS("Star Nomads", "Superior mobility. +1 Move and reduced fleet upkeep.",
        bonusMovement = 1,
        extraBonuses = listOf(BonusModifier(BonusType.UPKEEP_MODIFIER, -1))),
    // Ancient seers: unmatched sight and they rebuild captured worlds a step higher.
    KAELEN("Kaelen Hegemony", "Ancient knowledge. +2 Vision; captured worlds start one level higher.",
        bonusVision = 2,
        extraBonuses = listOf(BonusModifier(BonusType.CAPTURE_START_LEVEL, 1))),
    // The Swarm: mobile, aggressive, and out-produces everyone.
    XYLAR("Xylar Swarm", "Aggressive swarm. +1 Move, +5% Attack; units build faster.",
        bonusMovement = 1, bonusAttack = 0.05f,
        extraBonuses = listOf(BonusModifier(BonusType.PRODUCTION_SPEED, 1))),
    ANCIENT_NPC("Ancients", "Remnants of a forgotten age.");

    fun bonusModifiers(): List<BonusModifier> = buildList {
        if (bonusAttack > 0f) add(BonusModifier(BonusType.ATTACK_PERCENT, (bonusAttack * 100).toInt()))
        if (bonusCredits > 0) add(BonusModifier(BonusType.INCOME_FLAT, bonusCredits))
        if (bonusTechDiscount > 0f) add(BonusModifier(BonusType.TECH_COST_PERCENT, (bonusTechDiscount * 100).toInt()))
        if (bonusVision > 0) add(BonusModifier(BonusType.VISION_RANGE, bonusVision))
        if (bonusMovement != 0) add(BonusModifier(BonusType.MOVEMENT_MODIFIER, bonusMovement))
        addAll(extraBonuses)
    }
}
