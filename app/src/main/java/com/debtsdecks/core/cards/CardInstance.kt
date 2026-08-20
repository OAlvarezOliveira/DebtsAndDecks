package com.debtsdecks.core.cards

import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.TargetType

class CardInstance(
    val definition: CardDefinition,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) {
    var cost: Int = definition.cost
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

    val description: String
        get() = definition.description

    /**
     * Whether this card can be played at all. Cost no longer gates playability — any Credit
     * shortfall converts to Debt instead (see [shortfall]) — so the only remaining gate is
     * whether the card was already spent this combat.
     */
    fun isPlayable(): Boolean = !exhausted

    /** How much [credit] falls short of this card's [cost], or 0 if [credit] fully covers it. */
    fun shortfall(credit: Int): Int = maxOf(0, cost - credit)

    fun createModifiedCopy(newCost: Int? = null, newExhausted: Boolean? = null): CardInstance {
        val copy = CardInstance(definition, instanceId).apply {
            cost = newCost ?: this.cost
            exhausted = newExhausted ?: this.exhausted
        }
        return copy
    }
}