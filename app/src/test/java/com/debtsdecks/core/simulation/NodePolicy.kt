package com.debtsdecks.core.simulation

import com.debtsdecks.core.combat.NodeConfig
import com.debtsdecks.core.combat.RunManager

/**
 * C7 node policy for the headless simulator (test-source). Deterministic, stateless proxy for "what
 * does a competent player do at the between-fight node". Mirrors the design's survival-first logic:
 * repay when the debt band is hot, take the loan when gold-starved (but never above Execution),
 * thin the deck late, otherwise shop (archetype-biased offer from the run).
 */
object NodePolicy {

    /** One node decision; returns the RunManager action that was actually taken (some are no-ops). */
    fun act(run: RunManager) {
        val loanGold = NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, run.nodeIndex)
        val loanDebt = NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, run.nodeIndex)

        when {
            // Survive: gold-starved and the loan won't cross Execution (guard inside takeLoan too).
            run.gold < 8 && run.debt + loanDebt <= DebtThresholdSafe -> run.takeLoan()
            // Debt is the hot band: repay it (affordability is checked inside; falls through if broke).
            run.debt >= 20 -> run.repayViaNode() || buyOrRemove(run)
            // Late game deck too wide: thin it (only if affordable).
            run.deckSize > 14 && run.nodeIndex >= 3 -> run.removeCardFromDeck(deterministicRemoval(run)) || buyOrRemove(run)
            // Default: shop (free pick is never chosen by this policy — the sim's deck-greedy line).
            else -> buyOrRemove(run)
        }
    }

    private val DebtThresholdSafe = 45

    /** Deterministic removal: prefer thinning a starter "defend" (weakest long-run), else last pick. */
    private fun deterministicRemoval(run: RunManager): String =
        run.nodeRemoveChoices.firstOrNull { it == "defend" } ?: run.nodeRemoveChoices.first()

    /** Buy the shop's first card when affordable, else free-pick. Guarantees the node always ends. */
    private fun buyOrRemove(run: RunManager): Boolean {
        val cost = NodeConfig.escalatedCost(NodeConfig.BUY_BASE, run.nodeIndex)
        val card = run.nodeShopChoices.firstOrNull()
        return if (card != null && run.gold >= cost) {
            run.buyCard(card)
        } else {
            val pick = run.rewardChoices.firstOrNull() ?: error("node has no free pick offer")
            run.takeNodeFreePick(pick)
            true
        }
    }
}