package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TurnPhase

/**
 * Experimental what-if policy for the balance simulator: deliberately takes on Debt to reach a
 * target leverage band, trading short-term safety for Leverage-scaled attack damage. This measures
 * the pivot's upside that the conservative [ScriptedPolicy] never explores (it avoids debt, so
 * floor(debt/5) rarely activates).
 *
 * Behavior:
 * - Still blocks when incoming damage threatens > 50% HP (same rule as baseline).
 * - Otherwise plays the attack with the best projected damage-per-cost after Borrowing up to a
 *   target ring (`leverageTarget`), never crossing [DebtConfig.EXECUTION_THRESHOLD].
 * - Prefers zero-shortfall attacks as long as debt is already above target (no more borrowing).
 */
object LeveragePolicy : RunPolicy {

    /** Borrow up to this Debt ceiling; beyond it, stop taking Debt for Leverage. */
    const val LEVERAGE_TARGET: Int = 35

    override fun chooseAction(state: CombatState): ScriptedPolicy.CombatAction {
        if (state.currentTurn != TurnPhase.PLAYER_ACTION) return ScriptedPolicy.CombatAction.EndTurn

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
        // better to spend the energy on. Loans CONSUME energy (cost > 0) so the turn advances and
        // the loop cannot deadlock; free loans (cost 0) are never auto-played for the same reason.
        if (state.debt < LEVERAGE_TARGET && attacks.isNotEmpty() && state.energy > 0) {
            val affordableAttack = attacks.any { it.cost <= state.energy }
            val loan = playable.firstOrNull {
                // SELF-targeting SKILL loans only: ATTACK add_debt cards (e.g. reworked
                // bounced_check) need a target; a null target on an ENEMY card is silently
                // rejected by the engine and would deadlock the loop (hand never changes).
                it.definition.tags.contains("add_debt") &&
                    it.type == CardType.SKILL && it.targetType == com.debtsdecks.core.model.TargetType.SELF &&
                    it.cost > 0 && it.cost <= state.energy
            }
            // Early band building: below debt 15 even an affordable attack is weak (small flat
            // leverage), so take the loan FIRST — the projected attack at the higher post-borrow
            // debt beats the one available now. Above 15, only default to a loan when no attack
            // is affordable (the "you have nothing better" case). Loans consume energy (cost > 0),
            // so the turn always advances and the policy cannot deadlock on them.
            if (loan != null && (!affordableAttack || state.debt < 15)) {
                return ScriptedPolicy.CombatAction.Play(loan.instanceId, null)
            }
        }

        if (attacks.isEmpty()) return ScriptedPolicy.CombatAction.EndTurn

        // If Debt is already at the leverage target, only play affordable attacks (no more borrowing).
        // Otherwise pick the attack that wins the most by taking on the shortfall as Debt.
        if (state.debt >= LEVERAGE_TARGET) {
            val affordable = attacks.filter { it.cost <= state.energy }
            if (affordable.isEmpty()) return ScriptedPolicy.CombatAction.EndTurn
            val bestAffordable = affordable.maxWith(
                compareBy<CardInstance> { projectedDamage(it, state.debt) }
                    .thenBy { projectedDamage(it, state.debt) / it.cost }
                    .thenBy { it.cardId }   // highest definition id; instanceId is a random UUID
            // Same caveat as ScriptedPolicy: not a total order. Two instances of the same card
            // tie all the way down and maxWith keeps the first in list order, so determinism
            // rests on the hand being built deterministically. See HarnessDeterminismTest.
            )
            return ScriptedPolicy.CombatAction.Play(bestAffordable.instanceId, ScriptedPolicy.enemyTargetId(state))
        }

        // Still below target: this is where Leverage earns its name. Prefer the attack that gains
        // the most Leverage-scaled damage per Debt spent, borrowing the shortfall as Debt — even
        // when an affordable attack exists — as long as the resulting Debt stays under Execution.
        // Projected damage = base + C4 tags + floor((debt + shortfall) / 5) at the new debt level.
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

    /**
     * C4-aware projected damage of [attack] at [debt], mirroring CardResolver's branches:
     * flat leverage bonus floor(debt/5) on every hit, plus `debt_scaling` extra floor(debt/10),
     * or `debt_payoff` damage floor(debt/2) + flat bonus (single-hit payoff branch, no hits
     * multiplication — the resolver returns early for these cards).
     */
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
        // C4-aware pick: prefer payoff cards (debt_payoff/debt_scaling = the Leverage identity),
        // then attacks, then raw damage. debt_draw is Liquidity tempo (still useful).
        return choices.maxWith(
            compareBy<CardDefinition> {
                when {
                    it.tags.contains("debt_payoff") -> 3
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