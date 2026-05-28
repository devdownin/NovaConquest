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
    SCOUT(1, 5, 2, movement = 5),
    FIGHTER(4, 12, 4, movement = 4),
    CRUISER(8, 25, 6, movement = 3),
    BATTLESHIP(15, 16, 8, range = 2, movement = 2),
    CARRIER(20, 18, 6, movement = 3)
}
