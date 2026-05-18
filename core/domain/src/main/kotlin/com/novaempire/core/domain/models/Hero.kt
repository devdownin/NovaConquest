package com.novaempire.core.domain.models

data class Hero(
    val id: String,
    val name: String,
    val targetFaction: Faction,
    val cost: Int,
    val bonusDescription: String
)

object HeroRegistry {
    val ALL_HEROES = listOf(
        Hero("hero_vance", "Commander Vance", Faction.DOMINION, 50, "+15% Fleet Attack"),
        Hero("hero_elara", "Captain Elara", Faction.TRADERS, 40, "+10% Trade Income"),
        Hero("hero_nix", "High Seer Nix", Faction.ANCIENT_NPC, 75, "Passive Fleet Healing"),
        Hero("hero_kael", "Architect Kael", Faction.SYNTH, 60, "-10% Tech Cost")
    )

    fun getHero(id: String) = ALL_HEROES.find { it.id == id }
}
