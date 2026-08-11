package com.debtsdecks.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    var hp: Int = 50,
    val maxHp: Int = 50,
    var block: Int = 0,
    var strength: Int = 0,
    var weak: Int = 0,
    var vulnerable: Int = 0
) {
    fun takeDamage(amount: Int): Int {
        val effectiveDamage = if (vulnerable > 0) (amount * 1.5).toInt() else amount
        val actualDamage = maxOf(0, effectiveDamage - block)
        block = maxOf(0, block - effectiveDamage)
        hp = maxOf(0, hp - actualDamage)
        return actualDamage
    }

    fun heal(amount: Int) {
        hp = minOf(maxHp, hp + amount)
    }

    fun gainBlock(amount: Int) {
        block += amount
    }

    fun gainStrength(amount: Int) {
        strength += amount
    }

    fun applyWeak(turns: Int) {
        weak += turns
    }

    fun applyVulnerable(turns: Int) {
        vulnerable += turns
    }

    fun tickStatusEffects() {
        if (weak > 0) weak--
        if (vulnerable > 0) vulnerable--
    }

    fun endTurnReset() {
        block = 0
        tickStatusEffects()
    }

    fun isDead(): Boolean = hp <= 0

    val hpPercent: Float
        get() = hp.toFloat() / maxHp
}