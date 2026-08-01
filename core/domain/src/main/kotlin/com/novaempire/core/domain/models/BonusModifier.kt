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
    /** Extra progress per turn on every build order (adds to the base 1, like a Forge World). */
    PRODUCTION_SPEED,
    /** Flat delta applied to each unit's per-turn upkeep (negative = cheaper). Floored at 0/unit. */
    UPKEEP_MODIFIER,
    /** Hull points every friendly unit regains at the end of its faction's turn. */
    FLEET_REPAIR_PER_TURN,
}

data class BonusModifier(val type: BonusType, val value: Int)
