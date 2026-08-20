package com.debtsdecks.core.enemies

import com.debtsdecks.core.model.EnemyState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the tag-driven tier mechanics added in combat-progression-and-i18n Phase 3:
 * `enrage_below_half` (one-shot Strength grant at <=50% HP) and `debuff_resist`
 * (no-ops incoming Weak/Vulnerable). Both are gated purely by [EnemyDefinition.tags],
 * so a NORMAL enemy without the tag must behave exactly as before.
 */
class EnemyInstanceTest {

    private fun enemyWithTags(hp: Int = 10, tags: Set<String> = emptySet()) = EnemyInstance(
        EnemyDefinition(
            id = "test-enemy",
            name = "Test Enemy",
            hp = hp,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 5)),
            rewards = EnemyRewards(gold = 0, cardChoices = 3),
            tags = tags
        )
    )

    @Test
    fun `enrage_below_half grants Strength once when hp drops to 50 percent or below`() {
        val enemy = enemyWithTags(hp = 10, tags = setOf("enrage_below_half"))

        enemy.takeDamage(5) // hp: 10 -> 5, exactly 50%

        assertEquals(3, enemy.strength)
    }

    @Test
    fun `enrage_below_half does not grant Strength again on further damage`() {
        val enemy = enemyWithTags(hp = 10, tags = setOf("enrage_below_half"))

        enemy.takeDamage(5) // hp: 10 -> 5, triggers enrage once
        enemy.takeDamage(2) // hp: 5 -> 3, still below half, must NOT re-trigger

        assertEquals(3, enemy.strength)
    }

    @Test
    fun `enemy without the enrage_below_half tag never gains Strength from taking damage`() {
        val enemy = enemyWithTags(hp = 10, tags = emptySet())

        enemy.takeDamage(9) // hp: 10 -> 1, well below half

        assertEquals(0, enemy.strength)
    }

    @Test
    fun `debuff_resist no-ops applyWeak`() {
        val enemy = enemyWithTags(tags = setOf("debuff_resist"))

        enemy.applyWeak(2)

        assertEquals(0, enemy.weak)
    }

    @Test
    fun `debuff_resist no-ops applyVulnerable`() {
        val enemy = enemyWithTags(tags = setOf("debuff_resist"))

        enemy.applyVulnerable(2)

        assertEquals(0, enemy.vulnerable)
    }

    @Test
    fun `enemy without the debuff_resist tag still gains Weak and Vulnerable normally`() {
        val enemy = enemyWithTags(tags = emptySet())

        enemy.applyWeak(2)
        enemy.applyVulnerable(3)

        assertEquals(2, enemy.weak)
        assertEquals(3, enemy.vulnerable)
    }

    @Test
    fun `intentDisplayName reflects the enemy's current intent`() {
        val enemy = enemyWithTags() // default pattern: ATTACK, damage 5

        assertEquals("Attack 5", enemy.intentDisplayName())
    }

    @Test
    fun `intentIconName reflects the enemy's current intent`() {
        val enemy = enemyWithTags() // default pattern: ATTACK, damage 5

        assertEquals("intent_attack", enemy.intentIconName())
    }

    @Test
    fun `EnemyState fromInstance sources tier from the enemy's definition`() {
        val elite = EnemyInstance(
            EnemyDefinition(
                id = "elite-enemy", name = "Elite Enemy", hp = 10,
                intentPattern = listOf(IntentStep(IntentType.ATTACK, 5)),
                rewards = EnemyRewards(gold = 0, cardChoices = 3),
                tier = EnemyTier.ELITE
            )
        )
        val normal = enemyWithTags()

        assertEquals(EnemyTier.ELITE, EnemyState.fromInstance(elite).tier)
        assertEquals(EnemyTier.NORMAL, EnemyState.fromInstance(normal).tier)
    }
}
