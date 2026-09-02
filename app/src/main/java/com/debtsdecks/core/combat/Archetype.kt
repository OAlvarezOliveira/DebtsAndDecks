package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import kotlin.math.min

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

/**
 * Computes the synergy tier (0..[DebtConfig.ARCHETYPE_TIER_MAX]) per archetype from the current
 * deck COMPOSITION (tag counts). Pure function over [deck] + [registry]; no state, no per-turn
 * evaluation — tiers are static per node/deck and recomputed at node entry / combat start.
 *
 * - LEVERAGE / LIQUIDITY: count cards that carry any economy tag of that archetype.
 * - PRESSURE: counts ONLY cards explicitly tagged `"pressure"`. Plain non-economy cards (no
 *   economy tag) DO signal PRESSURE inside [playerArchetype] for tie-breaking, but they MUST NOT
 *   advance the PRESSURE tier — only dedicated PRESSURE-tagged cards do (archetype-synergy spec
 *   trap: "plain non-economy ≠ PRESSURE tier").
 *
 * Formula: tier = min(ARCHETYPE_TIER_MAX, floor(tagCount / ARCHETYPE_TIER_TAGS_PER_TIER)).
 * Examples: 3 LEVERAGE cards -> floor(3/2)=1; 5 LIQUIDITY -> 2; 1 card -> 0; 6 -> 3 (capped).
 */
fun archetypeTiers(deck: List<String>, registry: CardRegistry): Map<Archetype, Int> {
    // Seed every archetype at 0 so the result is a complete map (a deck with no LEVERAGE cards
    // returns LEVERAGE=0, never a missing key) — consumers read tiers[t] without null-guards.
    val counts = mutableMapOf<Archetype, Int>().apply {
        for (a in Archetype.entries) this[a] = 0
    }
    for (cardId in deck) {
        val def = registry.get(cardId) ?: continue
        val tags = def.tags
        if (tags.any { it in LEVERAGE_TAGS }) {
            counts[Archetype.LEVERAGE] = counts.getValue(Archetype.LEVERAGE) + 1
        }
        if (tags.any { it in LIQUIDITY_TAGS }) {
            counts[Archetype.LIQUIDITY] = counts.getValue(Archetype.LIQUIDITY) + 1
        }
        if ("pressure" in tags) {
            counts[Archetype.PRESSURE] = counts.getValue(Archetype.PRESSURE) + 1
        }
    }
    return counts.mapValues { (_, count) ->
        min(DebtConfig.ARCHETYPE_TIER_MAX, count / DebtConfig.ARCHETYPE_TIER_TAGS_PER_TIER)
    }
}