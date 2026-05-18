package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class Faction {
    DOMINION,
    TRADERS,
    SYNTH,
    NOMADS,
    KAELEN,
    XYLAR,
    ANCIENT_NPC
}
