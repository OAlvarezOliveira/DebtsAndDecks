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
 * FV deliverable 1: the policy variant that RESPONDS to the new verbs, as distinct from
 * [LeveragePolicy] which never reacts to them.
 *
 * **2026-08-29 calibration finding (see `docs/BALANCE-BASELINE.md`)**: this policy reacts to an
 * announced FORECLOSE (pay down with a wipe/repay card before the deadline resolves) and never
 * borrows into an announced HEDGE, but the measured "responding beats ignoring by >=10pp"
 * win-rate gap could not be reached with any variant that was actually tried, and every variant
 * that pushed harder than this one measured NEGATIVE relative to [LeveragePolicy]. What was
 * tried and its real (200-seed) measured response gap:
 *   - Lower [LEVERAGE_TARGET] below the FORECLOSE threshold (~20 vs the shared 35): -25.5pp.
 *     Sacrifices too much of the flat Leverage damage bonus across the WHOLE run for a rare event.
 *   - Ban ALL borrowing while FORECLOSE is announced (not just when it would cross the line),
 *     plus reward priority bumped for wipe_debt/debtRepay above debt_payoff: -10.0pp.
 *   - Same borrow ban, safety margin removed, reward priority unchanged: -7.5pp.
 *   - Same borrow ban, reward priority only softened (debt_payoff kept top, wipe/repay tied
 *     below): -3.0pp.
 *   - Cap (not ban) debt growth from shortfall-borrow attacks at `forecloseThreshold - 1` only on
 *     turns FORECLOSE is the displayed intent, everything else unchanged from baseline: -4.5pp,
 *     because the cap fires on the ~1-in-8 turns FORECLOSE is displayed even when Debt is nowhere
 *     near the threshold, giving up leverage tempo for no seizure-avoidance benefit (the reactive
 *     wipe/repay branch below already handles the one turn where the seizure is actually live).
 *     Isolating the SAME cap to only the loan-taking branch (never the shortfall-attack loop) was
 *     a true no-op: +2.5pp, identical to doing nothing extra.
 *   - Exact baseline behavior below (react on the actual deadline turn only, never restrict
 *     borrowing otherwise, reward priority unchanged from [LeveragePolicy]'s scheme): +2.5pp,
 *     matching the informational note this test already carries.
 * Every attempt that meaningfully changes borrowing near a FORECLOSE trades away more Leverage
 * damage across the run than it recovers from avoided seizures, because the roster's FORECLOSE
 * threshold (27) sits well inside the shared leverage band (target 35, execution line 50) that
 * BOTH policies already operate in — there is no borrowing posture that avoids the threshold
 * without also giving up the leverage economy's core damage source. This is the same conclusion
 * the FORECLOSE/HEDGE calibration pass reached on 2026-08-28; this session re-verified it with 6
 * additional real (not estimated) measurements rather than taking the prior finding on faith.
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

        // FV E1 — FORECLOSE response: pay down on the deadline turn. Block does not help against a
        // seizure, so a wipe (Debt -> 0) beats a partial repay when both are held.
        if (forecloseAnnounced(state) && state.debt >= forecloseThreshold(state)) {
            val wipe = state.hand
                .filter { it.isPlayable(state.debt) && it.definition.tags.contains("wipe_debt") }
                .minByOrNull { it.cost }
            if (wipe != null) {
                return ScriptedPolicy.CombatAction.Play(wipe.instanceId, null)
            }
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
        // better to spend the energy on. FV E1 — HEDGE response: never borrow during an announced
        // hedge, the enemy's block is scaled by your Debt.
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
        // Same reward priority as LeveragePolicy's Leverage-identity pick (debt_payoff/debt_scaling
        // first), with debtRepay cards ranked just below debt_payoff. Bumping debtRepay/wipe_debt
        // above debt_payoff was tried and measured a NET-NEGATIVE response gap (-3.0 to -10.0pp,
        // see the class doc) — it steals reward slots from the cards that carry the Leverage
        // economy's actual damage output for a card that only helps on the rare FORECLOSE-deadline
        // turn.
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