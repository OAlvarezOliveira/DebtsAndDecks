package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState

/**
 * WU7 harness policy: a PRESSURE-archetype player. Combat play is delegated to [ScriptedPolicy]
 * (competent baseline); the archetype is expressed purely through reward selection — it biases
 * node/shop picks toward PRESSURE-tagged cards so the deck accumulates the PRESSURE synergy tier
 * (weak/vulnerable escalation, +20% low-HP damage at T2+) that [CardResolver] applies in combat.
 *
 * Used by the T7.4 PRESSURE-parity harness band: a PRESSURE-only run must land within 10pp of the
 * LEVERAGE-only run, proving no archetype is strictly dominant.
 */
object PressurePolicy : RunPolicy {
    override fun chooseAction(state: CombatState): ScriptedPolicy.CombatAction =
        ScriptedPolicy.chooseAction(state)

    override fun chooseReward(choices: List<CardDefinition>): CardDefinition {
        if (choices.isEmpty()) error("chooseReward requires at least one offer")
        return choices.maxWith(
            compareBy<CardDefinition> {
                when {
                    it.tags.contains("pressure") -> 3
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
