package com.debtsdecks.core.combat.resolution

import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.TargetType
import kotlin.math.floor

/**
 * [l10n] was wired in this constructor in the combat-progression-and-i18n Phase 4a DI slice and
 * is consumed as of Phase 4b-iii: all log strings below resolve via `l10n.get()`/`l10n.format()`.
 */
class CardResolver(private val l10n: Localizer) {
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
        data class PoisonApply(val targetId: String, val amount: Int) : Effect
        data class ThornsGain(val amount: Int) : Effect
        data class RegenGain(val amount: Int) : Effect
        data class SelfDamage(val amount: Int) : Effect
        object ExhaustSelf : Effect
        object AddCopyToDiscard : Effect

        // --- Debt-resource-mechanic (Phase 4: card rework) ---
        /** Reduces Debt by [amount] (clamped to 0 by the applier). Emitted once per SKILL/POWER
         *  play, or once per landed hit for ATTACK cards (see [debtRepay] on CardDefinition). */
        data class RepayDebt(val amount: Int) : Effect
        /** Grants [amount] Gold. Emitted at most once per play, regardless of card type/hit count. */
        data class GainGold(val amount: Int) : Effect
        /** Wipes Debt to 0. Emitted for cards tagged `"wipe_debt"` (Chapter 11). */
        object WipeDebt : Effect
        /** Activates the per-combat Escrow Shield (halves Debt gained from borrowing). Emitted
         *  for cards tagged `"escrow_shield_activate"` (Escrow Shield). */
        object EscrowShieldActivate : Effect
        /** Adds [amount] Debt directly to the player (a "penalty/loan" source, e.g. Subprime Loan,
         *  Bounced Check). Never halved by the Escrow Shield — only Credit-shortfall borrowing is. */
        data class AddDebt(val amount: Int) : Effect
        /** Grants [amount] Credit/energy for the current turn (e.g. Golden Credit). */
        data class GainCredit(val amount: Int) : Effect
        /** Costs one card from the player's hand (exhausted) in exchange for value (e.g. Asset Auction). */
        object ExhaustFromHand : Effect
    }

    /**
     * [xValue] is only meaningful for cards tagged "x_cost": it's the amount of energy the
     * player had before paying for this card, which becomes the number of times an X-cost
     * card repeats its effect (e.g. Whirlwind hits every enemy once per energy spent).
     */
    fun resolve(
        card: CardInstance,
        targetId: String?,
        state: CombatState,
        xValue: Int = 0
    ): ResolutionResult {
        val effects = mutableListOf<Effect>()
        val logEntries = mutableListOf<CombatLogEntry>()
        val player = state.player
        val enemies = state.enemies.associateBy { it.id }

        when (card.type) {
            com.debtsdecks.core.model.CardType.ATTACK -> {
                val targets: List<String> = if (card.targetType == TargetType.ALL_ENEMIES) {
                    enemies.keys.toList()
                } else {
                    listOfNotNull(targetId ?: enemies.values.firstOrNull()?.id)
                }

                if (targets.isEmpty()) {
                    return ResolutionResult(
                        emptyList(),
                        listOf(CombatLogEntry.create(l10n.get("log.no_valid_target"), state.turnNumber))
                    )
                }

                val repeatCount = if (card.definition.tags.contains("x_cost")) xValue else maxOf(1, card.baseHits)
                var landedHits = 0
                repeat(repeatCount) {
                    for (t in targets) {
                        val enemy = enemies[t] ?: continue
                        val baseDamage = ((card.baseDamage + player.strength) * if (player.weak > 0) 0.75 else 1.0).toInt()
                        val effectiveDamage = if (enemy.vulnerable > 0) (baseDamage * 1.5).toInt() else baseDamage
                        effects.add(Effect.Damage(t, effectiveDamage))
                        landedHits++
                    }
                }
                // Debt-repaying attacks (Wage Garnishment, Collections Call) repay per landed hit,
                // not once per play — a 3-hit card at debtRepay=2 repays 2 Debt three times.
                if (card.definition.debtRepay > 0 && landedHits > 0) {
                    repeat(landedHits) { effects.add(Effect.RepayDebt(card.definition.debtRepay)) }
                    logEntries.add(
                        CombatLogEntry.create(
                            l10n.format("log.repay_per_hit", card.definition.debtRepay, landedHits),
                            state.turnNumber
                        )
                    )
                }
                if (repeatCount <= 0) {
                    logEntries.add(
                        CombatLogEntry.create(l10n.format("log.card_fizzles", l10n.get(card.name)), state.turnNumber)
                    )
                }

                if (card.baseWeakApply > 0) {
                    targets.forEach { effects.add(Effect.WeakApply(it, card.baseWeakApply)) }
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_weak", card.baseWeakApply), state.turnNumber))
                }
                if (card.baseVulnerableApply > 0) {
                    targets.forEach { effects.add(Effect.VulnerableApply(it, card.baseVulnerableApply)) }
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_vulnerable", card.baseVulnerableApply), state.turnNumber))
                }
                if (card.basePoisonApply > 0) {
                    targets.forEach { effects.add(Effect.PoisonApply(it, card.basePoisonApply)) }
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_poison", card.basePoisonApply), state.turnNumber))
                }
            }
            com.debtsdecks.core.model.CardType.SKILL, com.debtsdecks.core.model.CardType.POWER -> {
                if (card.baseBlock > 0) {
                    effects.add(Effect.Block(card.baseBlock))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.gained_block", card.baseBlock), state.turnNumber))
                }
                if (card.baseDraw > 0) {
                    effects.add(Effect.Draw(card.baseDraw))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.drew_cards", card.baseDraw), state.turnNumber))
                }
                // Compound Interest scales Strength off current Debt instead of a flat amount:
                // 1 Strength per 10 Debt, floor-rounded. Replaces the flat baseStrengthGain path
                // entirely for tagged cards (a card is either flat-scaling or debt-scaling, never both).
                if (card.definition.tags.contains("debt_scaling")) {
                    val scaledAmount = state.debt / 10
                    if (scaledAmount > 0) {
                        effects.add(Effect.StrengthGain(player.hashCode().toString(), scaledAmount))
                        logEntries.add(
                            CombatLogEntry.create(l10n.format("log.debt_fuels_resolve", scaledAmount), state.turnNumber)
                        )
                    }
                } else if (card.baseStrengthGain > 0) {
                    effects.add(Effect.StrengthGain(player.hashCode().toString(), card.baseStrengthGain))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.gained_strength", card.baseStrengthGain), state.turnNumber))
                }
                if (card.definition.debtRepay > 0) {
                    effects.add(Effect.RepayDebt(card.definition.debtRepay))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.repaid_debt", card.definition.debtRepay), state.turnNumber))
                }
                if (card.baseThornsGain > 0) {
                    effects.add(Effect.ThornsGain(card.baseThornsGain))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.gained_thorns", card.baseThornsGain), state.turnNumber))
                }
                if (card.baseRegenGain > 0) {
                    effects.add(Effect.RegenGain(card.baseRegenGain))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.gained_regen", card.baseRegenGain), state.turnNumber))
                }
                if (card.baseWeakApply > 0 && targetId != null) {
                    effects.add(Effect.WeakApply(targetId, card.baseWeakApply))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_weak", card.baseWeakApply), state.turnNumber))
                }
                if (card.baseVulnerableApply > 0 && targetId != null) {
                    effects.add(Effect.VulnerableApply(targetId, card.baseVulnerableApply))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_vulnerable", card.baseVulnerableApply), state.turnNumber))
                }
                if (card.basePoisonApply > 0 && targetId != null) {
                    effects.add(Effect.PoisonApply(targetId, card.basePoisonApply))
                    logEntries.add(CombatLogEntry.create(l10n.format("log.applied_poison", card.basePoisonApply), state.turnNumber))
                }
            }
        }

        // Chapter 11's self-damage cost is read from DebtConfig.CHAPTER_11_HP_COST (single source
        // of truth shared with the rest of the debt economy) rather than a duplicated JSON literal.
        // HP cost of a Debt wipe is now card-owned (its own selfDamage field); Chapter 11 sets it
        // to DebtConfig.CHAPTER_11_HP_COST in data, while Debt Forgiveness/Tactical Bankruptcy use
        // their own values. The wipe_debt tag no longer forces the flat 15-HP cost.
        val selfDamageAmount = card.baseSelfDamage
        if (selfDamageAmount > 0) {
            effects.add(Effect.SelfDamage(selfDamageAmount))
            logEntries.add(CombatLogEntry.create(l10n.format("log.lost_hp", selfDamageAmount), state.turnNumber))
        }

        if (card.definition.goldGain > 0 && !card.definition.tags.contains("gold_scaled_debt")) {
            effects.add(Effect.GainGold(card.definition.goldGain))
            logEntries.add(CombatLogEntry.create(l10n.format("log.gained_gold", card.definition.goldGain), state.turnNumber))
        }
        // --- Debt-Economy primitives (debt-economy-cards-and-boss-interest, PR1) ---
        // add-debt: a "penalty/loan" source added directly to the player; never escrow-halved.
        if (card.definition.tags.contains("add_debt") && card.definition.debtAdd > 0) {
            effects.add(Effect.AddDebt(card.definition.debtAdd))
        }
        // gain-credit: grants Credit/energy for the current turn.
        if (card.definition.tags.contains("gain_credit") && card.definition.creditGain > 0) {
            effects.add(Effect.GainCredit(card.definition.creditGain))
        }
        // hand-exhaust: costs one card from the hand in exchange for value (Asset Auction).
        if (card.definition.tags.contains("hand_exhaust")) {
            effects.add(Effect.ExhaustFromHand)
        }
        // gold-scaled-by-debt: Reverse Mortgage grants Gold scaled by current Debt (floor).
        if (card.definition.tags.contains("gold_scaled_debt")) {
            val scaled = floor(state.debt / 10.0).toInt() * card.definition.goldGain
            if (scaled > 0) {
                effects.add(Effect.GainGold(scaled))
                logEntries.add(CombatLogEntry.create(l10n.format("log.gained_gold", scaled), state.turnNumber))
            }
        }

        if (card.definition.tags.contains("wipe_debt")) {
            effects.add(Effect.WipeDebt)
            logEntries.add(CombatLogEntry.create(l10n.get("log.debt_wiped"), state.turnNumber))
        }
        if (card.definition.tags.contains("escrow_shield_activate")) {
            effects.add(Effect.EscrowShieldActivate)
            logEntries.add(CombatLogEntry.create(l10n.get("log.escrow_shield_active"), state.turnNumber))
        }

        if (card.definition.tags.contains("exhaust")) {
            effects.add(Effect.ExhaustSelf)
        }
        if (card.definition.tags.contains("recursive")) {
            effects.add(Effect.AddCopyToDiscard)
        }

        return ResolutionResult(effects, logEntries)
    }
}
