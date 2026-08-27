package com.debtsdecks.core.simulation

import com.debtsdecks.core.combat.NodeConfig
import com.debtsdecks.core.combat.RunManager

/**
 * C8 balance-pass-1: competent-player NodePolicy — the MEASUREMENT floor for balance tuning, not a
 * design directive. Deterministic + stateless proxy for a greedy-but-competent player at the node.
 *
 * Priority ladder (design obs 1516):
 * 1. Shop EARLY (nodes ≤ 3) when the offer is affordable — the escalation curve cheapens early buys.
 * 2. Loan when gold-starved and safe (never above Execution; the loan is the conscious survival move).
 * 3. Repay when the debt band is hot and affordable.
 * 4. Thin the deck late (starters) when it's wide.
 * 5. Shop otherwise. 6. Free-pick fallback (always terminates the node).
 */
object NodePolicy {

    /**
     * One node decision; always leaves the run in COMBAT (every action ends the node).
     * [policy] resolves WHICH card gets bought/free-picked (its [RunPolicy.chooseReward]) so that
     * distinct policies (e.g. greedy vs leverage) actually diverge on deck composition — the
     * priority ladder below (shop-timing, loan, repay, thin) stays policy-agnostic.
     */
    fun act(run: RunManager, policy: RunPolicy) {
        val buyCost = NodeConfig.escalatedCost(NodeConfig.BUY_BASE, run.nodeIndex)
        val loanGold = NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, run.nodeIndex)
        val loanDebt = NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, run.nodeIndex)

        val shopNow = run.nodeShopChoices.isNotEmpty() && run.gold >= buyCost

        // Every action below may legitimately no-op (unaffordable / unsafe / Execution guard).
        // Chain with a guaranteed free-pick fallback so the node ALWAYS ends — a no-op that returns
        // would leave the sim spinning in NODE forever (caught as "run exceeded max actions").
        val acted =
            // 1. Front-load value while the shop is cheap AND affordable.
            (shopNow && run.nodeIndex <= 3 && run.buyCard(policy.chooseReward(run.nodeShopChoices))) ||
            // 2. Survival loan: gold-poor, safe band.
            (run.gold < LOAN_GOLD_NEED && run.debt + loanDebt <= SAFE_AFTER_LOAN && run.takeLoan()) ||
            // 3. Hot debt: repay when affordable (fee included).
            (run.debt >= REPAY_BAND && run.gold >= run.debt + feeAt(run) && run.repayViaNode()) ||
            // 4. Thin late wide decks (remove a starter if offered).
            (run.deckSize > THIN_DECK && run.nodeIndex >= THIN_NODE && run.gold >= removeAt(run) &&
                run.removeCardFromDeck(deterministicRemoval(run))) ||
            // 5. Shop.
            (shopNow && run.buyCard(policy.chooseReward(run.nodeShopChoices)))

        if (!acted) {
            // 6. Free pick (guaranteed termination).
            run.takeNodeFreePick(policy.chooseReward(run.rewardChoices))
        }
    }

    // C8 experiment (measurement floor): a debt-leveraging player takes the loan unless gold is
    // comfortable — the loan is THE progression engine (survive + buy), and the pivot wants wins to
    // involve real Debt (peak > 25). Conservative floor: still won't borrow into Execution.
    private const val LOAN_GOLD_NEED = 20
    private const val SAFE_AFTER_LOAN = 45
    private const val REPAY_BAND = 25
    private const val THIN_DECK = 14
    private const val THIN_NODE = 4

    private fun feeAt(run: RunManager): Int =
        NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, run.nodeIndex)

    private fun removeAt(run: RunManager): Int =
        NodeConfig.escalatedCost(NodeConfig.REMOVE_BASE, run.nodeIndex)

    /** Deterministic removal: prefer thinning a starter "defend" (weakest long-run), else first offer. */
    private fun deterministicRemoval(run: RunManager): String =
        run.nodeRemoveChoices.firstOrNull { it == "defend" } ?: run.nodeRemoveChoices.first()
}