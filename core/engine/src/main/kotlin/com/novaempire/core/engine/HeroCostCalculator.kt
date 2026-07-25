package com.novaempire.core.engine

import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.Hero

/**
 * What a faction actually pays to recruit a hero (H2).
 *
 * `Hero.targetFaction` existed but was never enforced: any faction hired anyone at the same price,
 * so the field was decoration. A **hard** affinity lock would have been worse than the disease —
 * only DOMINION, TRADERS and SYNTH have a matching hero, so NOMADS, KAELEN and XYLAR would have been
 * left with nothing but the mercenary. Instead affinity is priced:
 *
 *  - your own hero costs the listed price;
 *  - a hero sworn to another faction costs [OFF_FACTION_MULTIPLIER]× (they need convincing);
 *  - a mercenary — one whose allegiance is [Faction.ANCIENT_NPC], i.e. no living empire — serves
 *    anyone at the listed price.
 *
 * Every faction can therefore still field every hero, but its own is decisively the cheapest.
 */
object HeroCostCalculator {

    const val OFF_FACTION_MULTIPLIER = 2

    /** True when the hero swears to no living empire and hires out to anyone at base price. */
    fun isMercenary(hero: Hero): Boolean = hero.targetFaction == Faction.ANCIENT_NPC

    fun costFor(hero: Hero, faction: Faction): Int =
        if (isMercenary(hero) || hero.targetFaction == faction) hero.cost
        else hero.cost * OFF_FACTION_MULTIPLIER
}
