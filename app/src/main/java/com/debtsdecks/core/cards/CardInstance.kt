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

    val description: String
        get() = definition.description

    fun canPlay(energy: Int): Boolean = !exhausted && cost <= energy

    fun createModifiedCopy(newCost: Int? = null, newExhausted: Boolean? = null): CardInstance {
        val copy = CardInstance(definition, instanceId).apply {
            cost = newCost ?: this.cost
            exhausted = newExhausted ?: this.exhausted
        }
        return copy
    }
}