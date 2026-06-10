package com.novaempire.core.domain.models

enum class BonusType {
    ATTACK_PERCENT,
    ATTACK_FLAT,
    INCOME_FLAT,
    INCOME_PERCENT,
    TECH_COST_PERCENT,
    VISION_RANGE,
    SCOUT_VISION_RANGE,
    /** Value 50 = 0.5x multiplier; 0 = no override (treated as 1.0x by callers). */
    VISION_RANGE_MULT_PCT,
    UNIT_HP_ON_SPAWN,
    SIEGE_DAMAGE,
    /** Delta on top of the base capture level (1). terraforming = 1 → start at level 2. */
    CAPTURE_START_LEVEL,
    RESEARCH_SPEED,
    MOVEMENT_MODIFIER,
}

data class BonusModifier(val type: BonusType, val value: Int)
