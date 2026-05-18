package com.novaempire.core.domain.models

enum class GalacticEvent(val displayName: String, val description: String) {
    NONE("No Active Events", ""),
    ION_STORM("Ion Storm", "Global movement -1. Shields do not regenerate."),
    ECONOMIC_BOOM("Economic Boom", "+3 Credits per system owned."),
    SOLAR_FLARE("Solar Flare", "Vision range divided by 2."),
    ANCIENT_SIGNAL("Ancient Signal", "Tech costs reduced by 25%. Wormholes unstable.")
}
