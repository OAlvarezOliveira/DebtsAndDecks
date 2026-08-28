package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase

/**
 * FV deliverable 1: the policy variant that RESPONDS to the new verbs. Identical to
 * [LeveragePolicy] except for three reactions the baseline (the non-responding control) never
 * takes, so the E1 gap measures the verbs, not the policies:
 *
 * - **FORECLOSE**: when the bailiff is announced and Debt is at/above its threshold, play the
 *   hand's debt-repaying card FIRST — the seizure ignores Block; the only way to answer a
 *   deadline is to pay before it.
 * - **HEDGE**: while the hedge is announced, never take a loan — the enemy's block is scaled by
 *   your Debt, so borrowing feeds it.
 * - **Reward pick**: buy debt-repaying cards (`debtRepay > 0`) with priority, so the deadline is
 *   actually answerable when it comes (the baseline never buys them).
 */
object RespondingPolicy : RunPolicy {

    val LEVERAGE_TARGET: Int get() = HarnessBands.leverageTarget

    private fun forecloseAnnounced(state: CombatState): Boolean =
        state.enemies.any { it.intentType == IntentType.FORECLOSE }

    private fun forecloseThreshold(state: CombatState): Int =
        state.enemies.filter { it.intentType == IntentType.FORECLOSE }.maxOfOrNull { it.intentParam } ?: 0

    private fun hedgeAnnounced(state: CombatState): Boolean =
        state.enemies.any { it.intentType == IntentType.HEDGE }

    override fun chooseAction(state: CombatState): ScriptedPolicy.CombatAction {
        if (state.currentTurn != TurnPhase.PLAYER_ACTION) return ScriptedPolicy.CombatAction.EndTurn

        // FV E1 — FORECLOSE response: pay down before the deadline. Block does not help.
        if (forecloseAnnounced(state) && state.debt >= forecloseThreshold(state)) {
            val repay = state.hand
                .filter { it.isPlayable(state.debt) && it.definition.debtRepay > 0 }
                .maxByOrNull { it.definition.debtRepay }
            if (repay != null) {
                return ScriptedPolicy.CombatAction.Play(repay.instanceId, null)
            }
        }

        // Same defensive rule as the baseline: predicted incoming damage decides blocking.
        if (ScriptedPolicy.shouldBlock(state)) {
            val blockCard = state.hand
                .filter { it.type == CardType.SKILL && it.baseBlock > 0 }
                .maxByOrNull { it.baseBlock }
            if (blockCard != null) {
                return ScriptedPolicy.CombatAction.Play(blockCard.instanceId, null)
            }
        }

        val playable = state.hand.filter { it.isPlayable(state.debt) }
        val attacks = playable.filter { it.type == CardType.ATTACK }

        // Leverage strategy: below target, take out a loan (add-debt card) when there is nothing
        // better to spend the energy on. FV E1 — HEDGE response: never during an announced hedge,
        // the enemy's block is scaled by your Debt.
        if (state.debt < LEVERAGE_TARGET && attacks.isNotEmpty() && state.energy > 0 && !hedgeAnnounced(state)) {
            val affordableAttack = attacks.any { it.cost <= state.energy }
            val loan = playable.firstOrNull {
                it.definition.tags.contains("add_debt") &&
                    it.type == CardType.SKILL && it.targetType == TargetType.SELF &&
                    it.cost > 0 && it.cost <= state.energy
            }
            if (loan != null && (!affordableAttack || state.debt < 15)) {
                return ScriptedPolicy.CombatAction.Play(loan.instanceId, null)
            }
        }

        if (attacks.isEmpty()) return ScriptedPolicy.CombatAction.EndTurn

        if (state.debt >= LEVERAGE_TARGET) {
            val affordable = attacks.filter { it.cost <= state.energy }
            if (affordable.isEmpty()) return ScriptedPolicy.CombatAction.EndTurn
            val bestAffordable = affordable.maxWith(
                compareBy<CardInstance> { projectedDamage(it, state.debt) }
                    .thenBy { projectedDamage(it, state.debt) / it.cost }
                    .thenBy { it.cardId }
            )
            return ScriptedPolicy.CombatAction.Play(bestAffordable.instanceId, ScriptedPolicy.enemyTargetId(state))
        }

        var best: CardInstance? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (attack in attacks) {
            val shortfall = (attack.cost - state.energy).coerceAtLeast(0)
            val debtAfter = state.debt + shortfall
            if (debtAfter > DebtConfig.EXECUTION_THRESHOLD) continue
            val projected = projectedDamage(attack, debtAfter).toDouble()
            val score = if (attack.cost > 0) projected / attack.cost else projected * 10.0
            if (score > bestScore) {
                bestScore = score
                best = attack
            }
        }
        if (best == null) return ScriptedPolicy.CombatAction.EndTurn
        return ScriptedPolicy.CombatAction.Play(best.instanceId, ScriptedPolicy.enemyTargetId(state))
    }

    private fun projectedDamage(attack: CardInstance, debt: Int): Int {
        val flat = debt / DebtConfig.LEVERAGE_DIVISOR
        return when {
            attack.definition.tags.contains("debt_payoff") -> debt / DebtConfig.DEBT_PAYOFF_DIVISOR + flat
            attack.definition.tags.contains("debt_scaling") -> attack.baseDamage + debt / DebtConfig.DEBT_SCALING_ATTACK_DIVISOR + flat
            else -> attack.baseDamage + flat
        }
    }

    override fun chooseReward(choices: List<CardDefinition>): CardDefinition {
        if (choices.isEmpty()) error("chooseReward requires at least one offer")
        // C4-aware pick, with the FV1 reply in the mix: debt-repaying cards are bought early so a
        // FORECLOSE deadline can actually be paid; the baseline never buys them.
        return choices.maxWith(
            compareBy<CardDefinition> {
                when {
                    it.tags.contains("debt_payoff") -> 4
                    it.debtRepay > 0 -> 3
                    it.tags.contains("debt_scaling") -> 2
                    it.tags.contains("debt_draw") -> 1
                    it.type == CardType.ATTACK -> 1
                    else -> 0
                }
            }
                .thenBy { it.damage }
                .thenByDescending { it.cost }
                .thenByDescending { choices.indexOf(it) }
        )
    }
}