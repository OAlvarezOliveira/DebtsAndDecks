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
                it.definition.tags.contains("add_debt") && it.cost > 0 && it.cost <= state.energy
            }
            // Prefer the loan when all attacks are too expensive to play profitably right now;
            // otherwise attack normally. This models "borrow to build the band" without looping.
            if (loan != null && !affordableAttack) {
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
                compareBy<CardInstance> { ScriptedPolicy.damagePerCost(it) }
                    .thenBy { -it.baseDamage }
                    .thenBy { it.instanceId }
            )
            return ScriptedPolicy.CombatAction.Play(bestAffordable.instanceId, ScriptedPolicy.enemyTargetId(state))
        }

        // Still below target: this is where Leverage earns its name. Prefer the attack that gains
        // the most Leverage-scaled damage per Debt spent, borrowing the shortfall as Debt — even
        // when an affordable attack exists — as long as the resulting Debt stays under Execution.
        // Projected damage = base + strength + floor((debt + shortfall) / 5) at the new debt level.
        var best: CardInstance? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (attack in attacks) {
            val shortfall = (attack.cost - state.energy).coerceAtLeast(0)
            val debtAfter = state.debt + shortfall
            if (debtAfter > DebtConfig.EXECUTION_THRESHOLD) continue
            val leverageBonus = debtAfter / 5
            val projected = attack.baseDamage + leverageBonus.toDouble()
            val score = if (attack.cost > 0) projected / attack.cost else projected * 10.0
            if (score > bestScore) {
                bestScore = score
                best = attack
            }
        }
        if (best == null) return ScriptedPolicy.CombatAction.EndTurn
        return ScriptedPolicy.CombatAction.Play(best.instanceId, ScriptedPolicy.enemyTargetId(state))
    }

    override fun chooseReward(choices: List<CardDefinition>): CardDefinition {
        if (choices.isEmpty()) error("chooseReward requires at least one offer")
        // Slight preference for attack cards (Leverage wants damage), then same tie-breaks.
        return choices.maxWith(
            compareBy<CardDefinition> { if (it.type == CardType.ATTACK) 1 else 0 }
                .thenBy { it.damage }
                .thenByDescending { it.cost }
                .thenByDescending { choices.indexOf(it) }
        )
    }
}