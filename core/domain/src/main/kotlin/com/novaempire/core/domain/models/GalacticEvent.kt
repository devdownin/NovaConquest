package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class GalacticEvent(
    val displayName: String,
    val description: String,
    /**
     * Targeted events strike a single faction (drawn when the event fires) instead of the whole
     * galaxy — their bonus/malus applies only to [com.novaempire.core.domain.state.GameState.eventTargetFaction].
     * Global events (weather-like) affect everyone.
     */
    val isTargeted: Boolean = false
) {
    NONE("No Active Events", ""),
    ION_STORM("Ion Storm", "Global movement -1. Shields do not regenerate."),
    ECONOMIC_BOOM("Economic Boom", "A booming economy: +3 Credits income per turn.", isTargeted = true),
    SOLAR_FLARE("Solar Flare", "Vision range divided by 2."),
    ANCIENT_SIGNAL("Ancient Signal", "Ancient data cache: tech costs reduced by 25%.", isTargeted = true),
    PIRATE_RAID("Pirate Raid", "Pirates raid the trade routes: -5 Credits income per turn.", isTargeted = true),
    TECH_RUSH("Tech Rush", "A research surge: technology advances faster.", isTargeted = true)
}
