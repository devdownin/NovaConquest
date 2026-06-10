package com.novaempire.core.domain.models

data class Hero(
    val id: String,
    val name: String,
    val targetFaction: Faction,
    val cost: Int,
    val bonusDescription: String,
    val bonuses: List<BonusModifier> = emptyList()
)

object HeroRegistry {
    const val VANCE = "hero_vance"
    const val ELARA = "hero_elara"
    const val NIX   = "hero_nix"
    const val KAEL  = "hero_kael"

    val ALL_HEROES = listOf(
        Hero(VANCE, "Commander Vance", Faction.DOMINION, 50, "+15% Fleet Attack",
            listOf(BonusModifier(BonusType.ATTACK_PERCENT, 15))),
        Hero(ELARA, "Captain Elara", Faction.TRADERS, 40, "+10% Trade Income",
            listOf(BonusModifier(BonusType.INCOME_PERCENT, 10), BonusModifier(BonusType.INCOME_FLAT, 2))),
        Hero(NIX, "High Seer Nix", Faction.ANCIENT_NPC, 75, "Passive Fleet Healing"),
        Hero(KAEL, "Architect Kael", Faction.SYNTH, 60, "-10% Tech Cost",
            listOf(BonusModifier(BonusType.TECH_COST_PERCENT, 10))),
    )

    fun getHero(id: String) = ALL_HEROES.find { it.id == id }

    fun bonusesFor(heroIds: Set<String>): List<BonusModifier> =
        heroIds.flatMap { id -> getHero(id)?.bonuses ?: emptyList() }
}
