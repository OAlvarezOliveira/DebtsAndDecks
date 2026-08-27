package com.debtsdecks.core.enemies

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression guard the combat-progression-and-i18n sdd-verify recommended: the 3-enemy roster's
 * tier stat ordering ("BOSS > ELITE > NORMAL" in max HP) is hand-authored data, not code-enforced,
 * so a future `assets/enemies/all.json` edit could silently invert it. This test reads the real JSON
 * (repo path, like TestI18n) and asserts the invariant holds, plus that the roster stays exactly 3.
 */
class EnemyTierRegressionTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadEnemies(): List<EnemyDefinition> =
        json.decodeFromString(File("src/main/assets/enemies/all.json").readText())

    @Test
    fun `roster keeps exactly the three enemies with strictly ascending max HP by tier`() {
        val enemies = loadEnemies()

        assertEquals(3, enemies.size, "roster must stay exactly thug/loan_shark/collector")

        val byTier = enemies.groupBy { it.tier }
        val normals = byTier.getValue(EnemyTier.NORMAL)
        val elites = byTier.getValue(EnemyTier.ELITE)
        val bosses = byTier.getValue(EnemyTier.BOSS)

        assertTrue(normals.maxOf { it.hp } < elites.maxOf { it.hp }, "ELITE max HP must exceed NORMAL")
        assertTrue(elites.maxOf { it.hp } < bosses.maxOf { it.hp }, "BOSS max HP must exceed ELITE")
    }

    @Test
    fun `collector remains the sole BOSS and thug the sole NORMAL floor`() {
        val enemies = loadEnemies()
        assertFalse(enemies.any { it.tier == EnemyTier.BOSS && it.id != "collector" })
        assertEquals(52, enemies.first { it.tier == EnemyTier.BOSS }.hp)
    }
}