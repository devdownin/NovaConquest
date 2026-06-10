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
    val bonusAttack: Float = 0f
) {
    DOMINION("Terran Dominion", "Balanced military power. +10% Attack.", bonusAttack = 0.10f),
    TRADERS("Free Traders", "Masters of commerce. +5 Credits/turn.", bonusCredits = 5),
    SYNTH("Synth Collective", "Rapid advancement. -15% Tech cost.", bonusTechDiscount = 0.15f),
    NOMADS("Star Nomads", "Superior mobility. +1 Move, +1 Vision.", bonusMovement = 1, bonusVision = 1),
    KAELEN("Kaelen Hegemony", "Ancient knowledge. +2 Vision range.", bonusVision = 2),
    XYLAR("Xylar Swarm", "Aggressive swarm. +1 Move, +5% Attack.", bonusMovement = 1, bonusAttack = 0.05f),
    ANCIENT_NPC("Ancients", "Remnants of a forgotten age.");

    fun bonusModifiers(): List<BonusModifier> = buildList {
        if (bonusAttack > 0f) add(BonusModifier(BonusType.ATTACK_PERCENT, (bonusAttack * 100).toInt()))
        if (bonusCredits > 0) add(BonusModifier(BonusType.INCOME_FLAT, bonusCredits))
        if (bonusTechDiscount > 0f) add(BonusModifier(BonusType.TECH_COST_PERCENT, (bonusTechDiscount * 100).toInt()))
        if (bonusVision > 0) add(BonusModifier(BonusType.VISION_RANGE, bonusVision))
        if (bonusMovement != 0) add(BonusModifier(BonusType.MOVEMENT_MODIFIER, bonusMovement))
    }
}
