package com.debtsdecks.core.combat

/**
 * C7 between-fight-node economy constants (design obs 1497 rev2). Single source of truth for node
 * costs, healing, escalation and offer sizes; engine + tests + sim all reference it.
 */
object NodeConfig {

    /** Flat HP restored on entering a node (part of the "rest"; no button, it just happens). */
    const val HEAL_AMOUNT: Int = 5

    /** Per-node multiplier applied to node-action base costs (node n → base × ESCALATION^(n-1)). */
    const val ESCALATION: Double = 1.5

    /** Base gold cost of buying a card from the shop (before escalation). */
    const val BUY_BASE: Int = 8

    /** Base gold cost of removing a card from the deck (before escalation). */
    const val REMOVE_BASE: Int = 10

    /** Base gold gained by taking a node LOAN (before escalation). */
    const val LOAN_GOLD_BASE: Int = 12

    /** Base debt added by taking a node LOAN (before escalation). */
    const val LOAN_DEBT_BASE: Int = 8

    /** Base service fee (gold, ON TOP of 1:1) charged to repay Debt at a node (before escalation). */
    const val REPAY_FEE_BASE: Int = 3

    /** How many cards the shop offers when buying. */
    const val SHOP_OFFER_SIZE: Int = 3

    /** How many random deck cards are offered when removing. */
    const val REMOVE_OFFER_SIZE: Int = 3

    /** Escalated floor cost of a [base] cost at 1-based [nodeIndex]. */
    fun escalatedCost(base: Int, nodeIndex: Int): Int =
        (base * Math.pow(ESCALATION, (nodeIndex - 1).toDouble())).toInt()
}