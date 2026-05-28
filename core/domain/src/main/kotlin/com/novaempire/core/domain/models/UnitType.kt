package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class UnitType(
    val cost: Int,
    val maxHp: Int,
    val attack: Int,
    val range: Int = 1,
    val movement: Int = 3
) {
    SCOUT(2, 6, 2, movement = 5),
    FIGHTER(4, 12, 4, movement = 4),
    CRUISER(10, 25, 6, movement = 3),
    BATTLESHIP(18, 35, 10, range = 2, movement = 2),
    CARRIER(25, 30, 8, range = 2, movement = 2),
    DREADNOUGHT(40, 60, 15, range = 3, movement = 1),
    DEFENSE_PLATFORM(15, 40, 12, range = 2, movement = 0)
}
