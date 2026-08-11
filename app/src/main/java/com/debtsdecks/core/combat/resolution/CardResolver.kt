package com.debtsdecks.core.combat.resolution

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.EnemyState

class CardResolver {
    data class ResolutionResult(
        val effects: List<Effect>,
        val logEntries: List<CombatLogEntry>
    )

    sealed interface Effect {
        data class Damage(val targetId: String, val amount: Int) : Effect
        data class Block(val amount: Int) : Effect
        data class Draw(val count: Int) : Effect
        data class StrengthGain(val targetId: String, val amount: Int) : Effect
        data class WeakApply(val targetId: String, val turns: Int) : Effect
        data class VulnerableApply(val targetId: String, val turns: Int) : Effect
        data class ExhaustSelf : Effect
    }

    fun resolve(
        card: CardInstance,
        targetId: String?,
        state: CombatState
    ): ResolutionResult {
        val effects = mutableListOf<Effect>()
        val logEntries = mutableListOf<CombatLogEntry>()
        val player = state.player
        val enemies = state.enemies.associateBy { it.id }

        when (card.type) {
            com.debtsdecks.core.model.CardType.ATTACK -> {
                val target = targetId ?: enemies.values.firstOrNull()?.id
                    ?: return ResolutionResult(emptyList(), listOf(CombatLogEntry.create("No valid target!", state.turnNumber)))

                val baseDamage = card.baseDamage + player.strength
                val enemy = enemies[target]!!
                val effectiveDamage = if (enemy.vulnerable > 0) (baseDamage * 1.5).toInt() else baseDamage
                val actualDamage = maxOf(0, effectiveDamage - enemy.block)

                effects.add(Effect.Damage(target, actualDamage))
                logEntries.add(CombatLogEntry.create("Dealt $actualDamage damage to ${enemy.name}!", state.turnNumber))

                if (card.baseWeakApply > 0) {
                    effects.add(Effect.WeakApply(target, card.baseWeakApply))
                    logEntries.add(CombatLogEntry.create("Applied Weak (${card.baseWeakApply})!", state.turnNumber))
                }
                if (card.baseVulnerableApply > 0) {
                    effects.add(Effect.VulnerableApply(target, card.baseVulnerableApply))
                    logEntries.add(CombatLogEntry.create("Applied Vulnerable (${card.baseVulnerableApply})!", state.turnNumber))
                }
            }
            com.debtsdecks.core.model.CardType.SKILL -> {
                if (card.baseBlock > 0) {
                    effects.add(Effect.Block(card.baseBlock))
                    logEntries.add(CombatLogEntry.create("Gained ${card.baseBlock} Block!", state.turnNumber))
                }
                if (card.baseDraw > 0) {
                    effects.add(Effect.Draw(card.baseDraw))
                    logEntries.add(CombatLogEntry.create("Drew ${card.baseDraw} card(s)!", state.turnNumber))
                }
                if (card.baseStrengthGain > 0) {
                    effects.add(Effect.StrengthGain(player.hashCode().toString(), card.baseStrengthGain))
                    logEntries.add(CombatLogEntry.create("Gained ${card.baseStrengthGain} Strength!", state.turnNumber))
                }
                if (card.baseWeakApply > 0 && targetId != null) {
                    effects.add(Effect.WeakApply(targetId, card.baseWeakApply))
                    logEntries.add(CombatLogEntry.create("Applied Weak (${card.baseWeakApply})!", state.turnNumber))
                }
                if (card.baseVulnerableApply > 0 && targetId != null) {
                    effects.add(Effect.VulnerableApply(targetId, card.baseVulnerableApply))
                    logEntries.add(CombatLogEntry.create("Applied Vulnerable (${card.baseVulnerableApply})!", state.turnNumber))
                }
            }
        }

        if (card.definition.tags.contains("exhaust")) {
            effects.add(Effect.ExhaustSelf)
        }

        return ResolutionResult(effects, logEntries)
    }
}