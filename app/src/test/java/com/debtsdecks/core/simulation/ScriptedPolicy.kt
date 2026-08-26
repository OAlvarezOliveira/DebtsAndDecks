package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.model.TurnPhase

/**
 * Pure, stateless scripted policy driving the headless run simulator. All functions are
 * side-effect free: they read [CombatState]/[CardDefinition] and return a decision value,
 * never mutating engine state. See run-simulation-harness design docs.
 */
object ScriptedPolicy {

    sealed interface CombatAction {
        data class Play(val instanceId: String, val targetId: String?) : CombatAction
        object EndTurn : CombatAction
    }

    /** One decision per PLAYER_ACTION phase call. Never mutates [state]. */
    fun chooseAction(state: CombatState): CombatAction {
        if (state.currentTurn != TurnPhase.PLAYER_ACTION) return CombatAction.EndTurn

        // Blocking turn: play the highest-block skill if available, else fall through to attack rule.
        if (shouldBlock(state)) {
            val blockCard = state.hand
                .filter { it.type == CardType.SKILL && it.baseBlock > 0 }
                .maxByOrNull { it.baseBlock }
            if (blockCard != null) {
                return CombatAction.Play(blockCard.instanceId, null)
            }
        }

        // Respect the game's playability gates: Liquidation cards (Ejecución/Refinanciar) are
        // unplayable at Debt <= 0 — playing Ejecución at zero Debt deals 0 damage and can deadlock
        // the run loop. Filter to cards the current Debt actually allows.
        val playable = state.hand.filter { it.isPlayable(state.debt) }
        val attacks = playable.filter { it.type == CardType.ATTACK }
        if (attacks.isEmpty()) return CombatAction.EndTurn

        // Zero-shortfall attacks preferred; only when none exist do we take on Debt (shortfall play).
        val affordable = attacks.filter { it.cost <= state.energy }
        val pool = if (affordable.isNotEmpty()) affordable else attacks

        val best = pool.maxWith(
            compareBy<CardInstance> { damagePerCost(it) }   // highest damage/cost
                .thenBy { it.baseDamage }                   // then highest raw damage
                .thenByDescending { it.instanceId }         // then lowest id (alphabetical)
        )
                // Debt-as-Leverage safety: never play a shortfall attack whose borrow would cross the
        // Execution line (debt > EXECUTION_THRESHOLD is an instant loss). End the turn instead.
        val wouldBorrow = best.cost > state.energy
        val debtAfter = state.debt + (best.cost - state.energy)
        if (wouldBorrow && debtAfter >= DebtConfig.EXECUTION_THRESHOLD) {
            return CombatAction.EndTurn
        }
        return CombatAction.Play(best.instanceId, enemyTargetId(state))
    }

    private fun damagePerCost(c: CardInstance): Double =
        if (c.cost <= 0) c.baseDamage.toDouble() else c.baseDamage.toDouble() / c.cost

    /** Target the first alive enemy (the policy plays single-target attacks at the first threat). */
    private fun enemyTargetId(state: CombatState): String? =
        state.enemies.firstOrNull { it.hp > 0 }?.id

    /** True if [state] warrants playing Defend this turn instead of an attack. */
    fun shouldBlock(state: CombatState): Boolean =
        predictedIncomingDamage(state) > (state.player.maxHp * 0.5)

    /**
     * Sum of predicted incoming damage, mirroring EnemyAI.executeIntent's formula:
     * `(intentDamage + strength) * (weak > 0 ? 0.75 : 1.0)`, repeated `intentParam` times for
     * MULTI_ATTACK intents.
     */
    fun predictedIncomingDamage(state: CombatState): Int {
        var total = 0
        for (enemy in state.enemies) {
            val attack = enemy.intentType == "ATTACK" || enemy.intentType == "MULTI_ATTACK"
            if (!attack) continue
            val perHit = ((enemy.intentDamage + enemy.strength) * if (enemy.weak > 0) 0.75 else 1.0).toInt()
            val hits = if (enemy.intentType == "MULTI_ATTACK") enemy.intentParam.coerceAtLeast(1) else 1
            total += perHit * hits
        }
        return total
    }

    /** Highest damage; ties broken by lowest cost, then list order. */
    fun chooseReward(choices: List<CardDefinition>): CardDefinition {
        if (choices.isEmpty()) error("chooseReward requires at least one offer")
        return choices.maxWith(
            compareBy<CardDefinition> { it.damage }        // highest damage
                .thenByDescending { it.cost }              // then lowest cost
                .thenByDescending { choices.indexOf(it) }  // then first in list order
        )
    }
}