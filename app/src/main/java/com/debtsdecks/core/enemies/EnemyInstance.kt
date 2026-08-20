package com.debtsdecks.core.enemies

import com.badlogic.gdx.utils.I18NBundle
import com.debtsdecks.core.enemies.IntentType.ATTACK
import com.debtsdecks.core.enemies.IntentType.BUFF
import com.debtsdecks.core.enemies.IntentType.DEBUFF
import com.debtsdecks.core.enemies.IntentType.MULTI_ATTACK
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.PlayerState

class EnemyInstance(
    val definition: EnemyDefinition,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) {
    var hp: Int = definition.hp
    var maxHp: Int = definition.hp
    var block: Int = 0
    var strength: Int = 0
    var weak: Int = 0
    var vulnerable: Int = 0
    var poison: Int = 0
    private var patternIndex = 0
    private var hasEnraged = false

    val id: String
        get() = instanceId

    val name: String
        get() = definition.name

    fun currentIntent(): Intent {
        val step = definition.intentPattern[patternIndex % definition.intentPattern.size]
        return Intent(step.type, step.damage, step.param)
    }

    /** Human-readable label for [currentIntent], e.g. for [com.debtsdecks.core.model.EnemyState]. */
    fun intentDisplayName(): String {
        val intent = currentIntent()
        return when (intent.type) {
            ATTACK -> "Attack ${intent.damage}"
            BUFF -> "Buff Strength +${intent.param}"
            DEBUFF -> "Debuff Weak ${intent.param}"
            MULTI_ATTACK -> "Multi Attack ${intent.damage} x${intent.param}"
        }
    }

    /** Icon asset key for [currentIntent], e.g. for [com.debtsdecks.core.model.EnemyState]. */
    fun intentIconName(): String =
        when (currentIntent().type) {
            ATTACK -> "intent_attack"
            BUFF -> "intent_buff"
            DEBUFF -> "intent_debuff"
            MULTI_ATTACK -> "intent_multi"
        }

    fun advanceIntent() {
        patternIndex++
    }

    fun takeDamage(amount: Int): Int {
        val actualDamage = maxOf(0, amount - block)
        block = maxOf(0, block - amount)
        hp = maxOf(0, hp - actualDamage)
        maybeEnrage()
        return actualDamage
    }

    /** Tag-driven, one-shot: grants Strength the first time HP drops to <=50%. */
    private fun maybeEnrage() {
        if (hasEnraged) return
        if (TAG_ENRAGE_BELOW_HALF !in definition.tags) return
        if (maxHp <= 0 || hp.toFloat() / maxHp > ENRAGE_HP_THRESHOLD) return
        gainStrength(ENRAGE_STRENGTH_BONUS)
        hasEnraged = true
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
        if (TAG_DEBUFF_RESIST in definition.tags) return
        weak += turns
    }

    fun applyVulnerable(turns: Int) {
        if (TAG_DEBUFF_RESIST in definition.tags) return
        vulnerable += turns
    }

    fun applyPoison(amount: Int) {
        poison += amount
    }

    /** Applies at the start of this enemy's turn, before it acts. Returns the damage dealt. */
    fun tickPoison(): Int {
        if (poison <= 0) return 0
        val dmg = poison
        hp = maxOf(0, hp - dmg)
        poison--
        return dmg
    }

    fun endTurnReset() {
        block = 0
        if (weak > 0) weak--
        if (vulnerable > 0) vulnerable--
    }

    fun isDead(): Boolean = hp <= 0

    companion object {
        /** [EnemyDefinition.tags] value that triggers [maybeEnrage]. */
        const val TAG_ENRAGE_BELOW_HALF = "enrage_below_half"
        private const val ENRAGE_HP_THRESHOLD = 0.5f
        private const val ENRAGE_STRENGTH_BONUS = 3

        /** [EnemyDefinition.tags] value that no-ops [applyWeak] and [applyVulnerable]. */
        const val TAG_DEBUFF_RESIST = "debuff_resist"
    }

    data class Intent(
        val type: IntentType,
        val damage: Int,
        val param: Int
    )
}

/**
 * [bundle] is wired in this constructor for the combat-progression-and-i18n Phase 4a DI slice but
 * not yet consumed: the combat-log strings below remain literal English pending the Phase 4b-iii
 * string migration, which replaces them with `bundle.get()`/`bundle.format()` lookups.
 */
class EnemyAI(private val enemy: EnemyInstance, private val bundle: I18NBundle) {
    fun executeIntent(player: PlayerState, allEnemies: List<EnemyInstance>, turn: Int): List<CombatLogEntry> {
        val intent = enemy.currentIntent()
        val log = mutableListOf<CombatLogEntry>()

        when (intent.type) {
            ATTACK -> {
                val dmg = ((intent.damage + enemy.strength) * if (enemy.weak > 0) 0.75 else 1.0).toInt()
                val actual = player.takeDamage(dmg)
                log.add(CombatLogEntry.create("${enemy.name} attacks for $actual damage!", turn))
                reflectThorns(player, turn, log)
            }
            BUFF -> {
                enemy.gainStrength(intent.param)
                log.add(CombatLogEntry.create("${enemy.name} gains ${intent.param} Strength!", turn))
            }
            DEBUFF -> {
                player.applyWeak(intent.param)
                log.add(CombatLogEntry.create("${enemy.name} applies Weak (${intent.param})!", turn))
            }
            MULTI_ATTACK -> {
                repeat(intent.param) {
                    val dmg = ((intent.damage + enemy.strength) * if (enemy.weak > 0) 0.75 else 1.0).toInt()
                    val actual = player.takeDamage(dmg)
                    log.add(CombatLogEntry.create("${enemy.name} attacks for $actual damage!", turn))
                    reflectThorns(player, turn, log)
                }
            }
        }

        enemy.advanceIntent()
        return log
    }

    private fun reflectThorns(player: PlayerState, turn: Int, log: MutableList<CombatLogEntry>) {
        if (player.thorns <= 0) return
        val reflected = enemy.takeDamage(player.thorns)
        log.add(CombatLogEntry.create("${enemy.name} takes $reflected Thorns damage!", turn))
    }
}