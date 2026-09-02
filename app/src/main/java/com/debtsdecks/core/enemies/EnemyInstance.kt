package com.debtsdecks.core.enemies

import com.debtsdecks.core.i18n.Localizer
import com.debtsdecks.core.enemies.IntentType.ATTACK
import com.debtsdecks.core.enemies.IntentType.BUFF
import com.debtsdecks.core.enemies.IntentType.DEBUFF
import com.debtsdecks.core.enemies.IntentType.FORECLOSE
import com.debtsdecks.core.enemies.IntentType.HEDGE
import com.debtsdecks.core.enemies.IntentType.LEVY
import com.debtsdecks.core.enemies.IntentType.MULTI_ATTACK
import com.debtsdecks.core.model.CombatLogEntry
import com.debtsdecks.core.model.PlayerState

class EnemyInstance(
    val definition: EnemyDefinition,
    private val l10n: Localizer,
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

    /**
     * Human-readable label for [currentIntent], e.g. for [com.debtsdecks.core.model.EnemyState].
     *
     * The key comes from [IntentType.l10nKey]; only the *arguments* vary per type, which is what
     * this `when` is for. Keeping the key on the enum is what lets `IntentTypeCoverageTest` walk
     * `IntentType.entries` and check every key really exists in both bundles.
     */
    fun intentDisplayName(): String {
        val intent = currentIntent()
        val key = intent.type.l10nKey
        return when (intent.type) {
            ATTACK -> l10n.format(key, intent.damage)
            BUFF -> l10n.format(key, intent.param)
            DEBUFF -> l10n.format(key, intent.param)
            MULTI_ATTACK -> l10n.format(key, intent.damage, intent.param)
            LEVY -> l10n.format(key, intent.param)
            FORECLOSE -> l10n.format(key, intent.param, intent.damage)
            HEDGE -> l10n.format(key)
        }
    }

    /** Icon asset key for [currentIntent], e.g. for [com.debtsdecks.core.model.EnemyState]. */
    fun intentIconName(): String = currentIntent().type.iconName

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
        // FV deliverable 1: enemy Block survives to the player's next turn — HEDGE arms the enemy
        // mid-ENEMY_ACTION and the player must be able to attack THROUGH it. Spent by takeDamage
        // like the player's own block.
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
 * [l10n] was wired in this constructor in the combat-progression-and-i18n Phase 4a DI slice and
 * is consumed as of Phase 4b-iii: all combat-log strings below resolve via `l10n.format()`.
 */
class EnemyAI(private val enemy: EnemyInstance, private val l10n: Localizer) {
    fun executeIntent(player: PlayerState, allEnemies: List<EnemyInstance>, turn: Int): List<CombatLogEntry> {
        val intent = enemy.currentIntent()
        val log = mutableListOf<CombatLogEntry>()

        when (intent.type) {
            ATTACK -> {
                val dmg = ((intent.damage + enemy.strength) * if (enemy.weak > 0) 0.75 else 1.0).toInt()
                val actual = player.takeDamage(dmg)
                log.add(CombatLogEntry.create(l10n.format("log.enemy_attacks", enemy.name, actual), turn))
                reflectThorns(player, turn, log)
            }
            BUFF -> {
                enemy.gainStrength(intent.param)
                log.add(CombatLogEntry.create(l10n.format("log.enemy_gains_strength", enemy.name, intent.param), turn))
            }
            DEBUFF -> {
                player.applyWeak(intent.param)
                log.add(CombatLogEntry.create(l10n.format("log.enemy_applies_weak", enemy.name, intent.param), turn))
            }
            LEVY -> {
                // Engine owns the debt levy (applied in CombatEngine.endPlayerTurn);
                // EnemyAI only advances the pattern, no combat effect.
            }
            FORECLOSE -> {
                // Engine-owned (reads the player's Debt, only the engine holds it):
                // CombatEngine.endPlayerTurn applies the seizure.
            }
            HEDGE -> {
                // Engine-owned: the block is debt-scaled at resolution, in
                // CombatEngine.endPlayerTurn.
            }
            MULTI_ATTACK -> {
                repeat(intent.param) {
                    val dmg = ((intent.damage + enemy.strength) * if (enemy.weak > 0) 0.75 else 1.0).toInt()
                    val actual = player.takeDamage(dmg)
                    log.add(CombatLogEntry.create(l10n.format("log.enemy_attacks", enemy.name, actual), turn))
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
        log.add(CombatLogEntry.create(l10n.format("log.enemy_takes_thorns", enemy.name, reflected), turn))
    }
}