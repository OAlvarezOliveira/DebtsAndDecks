package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry

/**
 * C7 between-fight-node archetype signal: which of the three legible archetypes the player's deck
 * is leaning into, used to bias the node shop offer. Pure function over [deck] (run deck, card ids)
 * + [registry]; no state. Weights reflect how strongly a tag commits a build (design obs 1497).
 */
enum class Archetype { LEVERAGE, LIQUIDITY, PRESSURE }

private val LEVERAGE_TAGS = setOf("debt_scaling", "debt_payoff", "execution_damage")
private val LIQUIDITY_TAGS = setOf("debt_draw", "refinance", "add_debt", "gain_credit", "gold_scaled_debt", "hand_exhaust")
private val ECONOMY_TAGS = LEVERAGE_TAGS + LIQUIDITY_TAGS

private fun tagWeight(archetype: Archetype, tags: Set<String>): Int = when (archetype) {
    // Starters are neutral (they carry only "starter"); economy-tagged cards commit a build.
    // A plain non-starter card (no economy tag) is PRESSURE-signal.
    Archetype.LEVERAGE -> 2 * tags.count { it in LEVERAGE_TAGS } + if ("debt_scaling" in tags) 1 else 0
    Archetype.LIQUIDITY -> 2 * tags.count { it in LIQUIDITY_TAGS } + if ("debt_draw" in tags) 1 else 0
    Archetype.PRESSURE -> if ("starter" in tags) 0 else if (tags.none { it in ECONOMY_TAGS }) 1 else 0
}

/**
 * Scores the deck across the three archetypes. Starters count nothing (neutral). A deck with no
 * economy signal scores PRESSURE by its plain cards; ties break LEVERAGE > LIQUIDITY > PRESSURE
 * (deterministic enum order). Pure: identical inputs yield identical outputs.
 */
fun playerArchetype(deck: List<String>, registry: CardRegistry): Archetype {
    val scores = mutableMapOf<Archetype, Int>().withDefault { 0 }
    for (cardId in deck) {
        val def = registry.get(cardId) ?: continue
        for (a in Archetype.entries) {
            scores[a] = scores.getValue(a) + tagWeight(a, def.tags)
        }
    }
    if (scores.getValue(Archetype.LEVERAGE) == 0 && scores.getValue(Archetype.LIQUIDITY) == 0) {
        return Archetype.PRESSURE
    }
    return Archetype.entries
        .sortedWith(compareByDescending<Archetype> { scores.getValue(it) })
        .first()
}