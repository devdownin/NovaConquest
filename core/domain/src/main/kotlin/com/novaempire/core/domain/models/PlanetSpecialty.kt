package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class PlanetSpecialty(val displayName: String, val description: String) {
    FORGE_WORLD("Forge World", "Units built here complete 1 turn faster"),
    RESEARCH_HUB("Research Hub", "+1 research progress per turn"),
    TRADE_POST("Trade Post", "+8 credits income per turn"),
}
