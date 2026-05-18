package com.novaempire.core.domain.models
import kotlinx.serialization.Serializable

@Serializable
enum class DiplomaticRelation {
    NEUTRAL,
    ALLIANCE,
    WAR
}
