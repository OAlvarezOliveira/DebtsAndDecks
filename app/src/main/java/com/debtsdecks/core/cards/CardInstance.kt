package com.debtsdecks.core.cards

import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.combat.NodeConfig

class CardInstance(
    val definition: CardDefinition,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) {
    var cost: Int = definition.cost

    /** Cost as defined in [definition], captured before any upgrade reduction is applied. */
    val baseCost: Int = definition.cost

    var exhausted: Boolean = false
    var upgraded: Boolean = false

    val id: String
        get() = instanceId

    val cardId: String
        get() = definition.id

    val name: String
        get() = definition.name

    val type: CardType
        get() = definition.type

    val targetType: TargetType
        get() = definition.targetType

    val baseDamage: Int
        get() = definition.damage

    val baseBlock: Int
        get() = definition.block

    val baseDraw: Int
        get() = definition.draw

    val baseStrengthGain: Int
        get() = definition.strengthGain

    val baseWeakApply: Int
        get() = definition.weakApply

    val baseVulnerableApply: Int
        get() = definition.vulnerableApply

    val baseSelfDamage: Int
        get() = definition.selfDamage

    val basePoisonApply: Int
        get() = definition.poisonApply

    val baseThornsGain: Int
        get() = definition.thornsGain

    val baseRegenGain: Int
        get() = definition.regenGain

    val baseHits: Int
        get() = definition.hits

    // --- Upgrade bonuses (card-upgrades, R4/R5/R6): effective values read by CardResolver.
    // The baseCost < 2 guard enforces the -1-cost priority (R6): cost-2+ cards get ONLY the
    // cost reduction at instance creation, never a stat bonus.

    val effectiveDamage: Int
        get() = baseDamage + if (upgraded && baseCost < 2 && type == CardType.ATTACK) NodeConfig.UPGRADE_ATTACK_DAMAGE else 0

    val effectiveBlock: Int
        get() = baseBlock + if (upgraded && baseCost < 2 && type == CardType.SKILL && baseBlock > 0) NodeConfig.UPGRADE_SKILL_BLOCK else 0

    val effectiveDraw: Int
        get() = baseDraw + if (upgraded && baseCost < 2 && type == CardType.SKILL && baseBlock == 0) NodeConfig.UPGRADE_SKILL_DRAW else 0

    val description: String
        get() = definition.description

    /**
     * Whether this card can be played at all. Cost no longer gates playability — any Credit
     * shortfall converts to Debt instead (see [shortfall]) — so the only remaining gate is
     * whether the card was already spent this combat.
     */
    fun isPlayable(): Boolean = !exhausted

        /**
         * Whether this card can be played given current [debt]. Liquidation cards
         * ("execution_damage"/"refinance" tags) are additionally gated: explicitly unplayable
         * when [debt] <= 0, so the player sees a disabled card rather than a silent no-op.
         */
        fun isPlayable(debt: Int): Boolean =
            !exhausted && !((definition.tags.contains("execution_damage") ||
                             definition.tags.contains("refinance")) && debt <= 0)

    /** How much [credit] falls short of this card's [cost], or 0 if [credit] fully covers it. */
    fun shortfall(credit: Int): Int = maxOf(0, cost - credit)

    fun createModifiedCopy(newCost: Int? = null, newExhausted: Boolean? = null): CardInstance {
        val copy = CardInstance(definition, instanceId).apply {
            cost = newCost ?: this.cost
            exhausted = newExhausted ?: this.exhausted
            upgraded = this@CardInstance.upgraded
        }
        return copy
    }
}