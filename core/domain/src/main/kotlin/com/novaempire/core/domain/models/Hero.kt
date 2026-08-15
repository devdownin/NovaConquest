package com.novaempire.core.domain.models

/**
 * Aptitude active d'un héros : utilisable **une fois par partie**.
 *
 * Vit dans le modèle, pas dans l'écran : le libellé était auparavant recopié dans une table de
 * `HeroAcademyScreen` et une deuxième fois dans les notifications du moteur, sans rien pour tenir
 * les trois versions d'accord. C'est exactement le défaut H1 (liste de héros dupliquée), qui
 * subsistait pour les aptitudes.
 */
data class HeroAbility(val name: String, val description: String)

data class Hero(
    val id: String,
    val name: String,
    val targetFaction: Faction,
    val cost: Int,
    /** Effet passif, permanent tant que le héros est recruté. */
    val bonusDescription: String,
    val bonuses: List<BonusModifier> = emptyList(),
    /** Aptitude active à usage unique, ou `null` si le héros n'en a pas. */
    val ability: HeroAbility? = null
)

object HeroRegistry {
    const val VANCE = "hero_vance"
    const val ELARA = "hero_elara"
    const val NIX   = "hero_nix"
    const val KAEL  = "hero_kael"

    /** Crédits versés par l'aptitude d'Elara. */
    const val ELARA_ABILITY_CREDITS = 80

    val ALL_HEROES = listOf(
        Hero(VANCE, "Commander Vance", Faction.DOMINION, 50, "+15% Fleet Attack",
            listOf(BonusModifier(BonusType.ATTACK_PERCENT, 15)),
            HeroAbility("Frappe de Suppression", "all fleet units may fire again this turn")),
        Hero(ELARA, "Captain Elara", Faction.TRADERS, 40, "+10% Trade Income",
            listOf(BonusModifier(BonusType.INCOME_PERCENT, 10), BonusModifier(BonusType.INCOME_FLAT, 2)),
            HeroAbility("Convoi Commercial", "gain +$ELARA_ABILITY_CREDITS Credits immediately")),
        // Le soin passif de Nix passait par un cas particulier dans TurnManager, seul héros à ne pas
        // décrire son passif comme un BonusModifier. Il suit désormais le même rail que les autres.
        Hero(NIX, "High Seer Nix", Faction.ANCIENT_NPC, 75, "Passive Fleet Healing (+1 HP/turn)",
            listOf(BonusModifier(BonusType.FLEET_REPAIR_PER_TURN, 1)),
            HeroAbility("Refuge Stellaire", "repair half the hull of every friendly unit")),
        Hero(KAEL, "Architect Kael", Faction.SYNTH, 60, "-10% Tech Cost",
            listOf(BonusModifier(BonusType.TECH_COST_PERCENT, 10)),
            HeroAbility("Prototype", "complete current research instantly")),
    )

    fun getHero(id: String) = ALL_HEROES.find { it.id == id }

    fun bonusesFor(heroIds: Set<String>): List<BonusModifier> =
        heroIds.flatMap { id -> getHero(id)?.bonuses ?: emptyList() }
}
