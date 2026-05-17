package com.novaempire.core.domain.models

enum class UnitType(val cost: Int, val maxHp: Int, val attack: Int, val range: Int = 1) {
    SCOUT(1, 5, 2),
    FIGHTER(4, 12, 4),
    CRUISER(8, 25, 6),
    BATTLESHIP(15, 16, 8, range = 2), // Battleship stats from audit
    CARRIER(20, 18, 6) // Carrier stats from audit
}
