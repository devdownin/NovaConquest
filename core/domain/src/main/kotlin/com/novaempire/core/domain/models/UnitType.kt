package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class UnitType(
    val cost: Int,
    val maxHp: Int,
    val attack: Int,
    val range: Int = 1,
    val movement: Int = 3,
    val upkeepCost: Int = 0
) {
    SCOUT(3, 6, 2, movement = 5, upkeepCost = 0),
    FIGHTER(8, 12, 4, movement = 4, upkeepCost = 2),
    CRUISER(10, 25, 6, movement = 3, upkeepCost = 3),
    BATTLESHIP(18, 35, 10, range = 2, movement = 2, upkeepCost = 3),
    CARRIER(25, 30, 8, range = 2, movement = 2, upkeepCost = 3),
    DREADNOUGHT(40, 60, 15, range = 3, movement = 1, upkeepCost = 5),
    DEFENSE_PLATFORM(15, 40, 12, range = 2, movement = 0, upkeepCost = 2)
}
