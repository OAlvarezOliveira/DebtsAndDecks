package com.debtsdecks.core.enemies

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.combat.actForSlotIndex
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * WU4 (Enemy Scaling + Intents) focused tests. Covers the two acceptance invariants from the
 * enemy-scaling spec: (1) HP AND damage scale together per act (the "HP without damage scaling"
 * false-positive trap), and (2) slot -> act mapping, plus the new FORECLOSE/HEDGE intents.
 */
class EnemyScalingTest {

    private lateinit var engine: CombatEngine

    @BeforeEach
    fun setup() {
        val strike = CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1, damage = 6,
            targetType = TargetType.ENEMY, description = "Deal 6 damage", rarity = Rarity.BASIC
        )
        val defend = CardDefinition(
            id = "defend", name = "Defend", type = CardType.SKILL, cost = 1, block = 5,
            targetType = TargetType.SELF, description = "Gain 5 Block", rarity = Rarity.BASIC
        )
        val registry = CardRegistry.create(listOf(strike, defend))
        engine = CombatEngine(registry, testLocalizer(), Random(42))
    }

    private fun thugWithModifiers() = EnemyDefinition(
        id = "thug", name = "Thug", hp = 22,
        intentPattern = listOf(
            IntentStep(IntentType.ATTACK, 8),
            IntentStep(IntentType.ATTACK, 8),
            IntentStep(IntentType.BUFF, 3)
        ),
        rewards = EnemyRewards(gold = 0, cardChoices = 0),
        actModifiers = listOf(
            ActModifier(act = 1, hpMultiplier = 1.36, damageMultiplier = 1.0),
            ActModifier(act = 2, hpMultiplier = 2.5, damageMultiplier = 1.2)
        )
    )

    @Test
    fun `act I keeps thug at 30 HP and baseline damage`() {
        val enemy = EnemyInstance(thugWithModifiers(), testLocalizer(), act = 1)
        assertEquals(30, enemy.hp) // spec acceptance: "thug 30 HP vs baseline 22 HP"
        assertEquals(8, enemy.currentIntent().damage) // act I damage mult 1.0
    }

    @Test
    fun `act II scales HP and damage together`() {
        val enemy = EnemyInstance(thugWithModifiers(), testLocalizer(), act = 2)
        assertEquals(55, enemy.hp) // round(22 * 2.5)
        // First pattern step is ATTACK damage 8, scaled by 1.2 -> round(9.6) = 10.
        assertEquals(10, enemy.currentIntent().damage)
        // The trap: damage MUST have grown with HP. A 22->55 HP bump with flat 8 damage would be
        // easier, not harder. Assert the damage actually increased.
        assertTrue(enemy.currentIntent().damage > 8, "damage must scale with HP")
    }

    @Test
    fun `no actModifiers means the enemy is unscaled (backward compatible)`() {
        val plain = EnemyDefinition(
            id = "plain", name = "Plain", hp = 22,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 8)),
            rewards = EnemyRewards(gold = 0, cardChoices = 0)
        )
        val enemy = EnemyInstance(plain, testLocalizer(), act = 1)
        assertEquals(22, enemy.hp)
        assertEquals(8, enemy.currentIntent().damage)
    }

    @Test
    fun `slot index maps to the correct act (3+3+2 district partition)`() {
        assertEquals(1, actForSlotIndex(0))
        assertEquals(1, actForSlotIndex(2))
        assertEquals(2, actForSlotIndex(3))
        assertEquals(2, actForSlotIndex(5))
        assertEquals(3, actForSlotIndex(6))
        assertEquals(3, actForSlotIndex(7))
    }

    @Test
    fun `HEDGE intent grants Block via EnemyAI`() {
        val enemy = EnemyInstance(
            EnemyDefinition(
                id = "hedger", name = "Hedger", hp = 10,
                intentPattern = listOf(IntentStep(IntentType.HEDGE, param = 8)),
                rewards = EnemyRewards(gold = 0, cardChoices = 0)
            ),
            testLocalizer()
        )
        val ai = EnemyAI(enemy, testLocalizer())
        val log = ai.executeIntent(PlayerState(), listOf(enemy), 1)
        assertEquals(8, enemy.block)
        assertTrue(log.isNotEmpty())
        assertEquals(IntentType.HEDGE, enemy.currentIntent().type) // advanced past HEDGE
    }

    @Test
    fun `FORECLOSE with existing Debt adds Debt through the engine`() {
        engine.startCombat(
            listOf(EnemyDefinition(
                id = "forecloser", name = "Forecloser", hp = 50,
                intentPattern = listOf(IntentStep(IntentType.FORECLOSE, param = 10)),
                rewards = EnemyRewards(gold = 0, cardChoices = 0)
            )),
            listOf("strike", "strike", "strike", "strike", "strike"),
            startingDebt = 20
        )
        val before = engine.getState().debt
        engine.endPlayerTurn()
        // FORECLOSE adds 10; the next turn-start interest tick compounds it.
        assertEquals(DebtConfig.applyInterest(before + 10), engine.getState().debt)
    }

    @Test
    fun `FORECLOSE without Debt deals direct HP damage`() {
        engine.startCombat(
            listOf(EnemyDefinition(
                id = "forecloser", name = "Forecloser", hp = 50,
                intentPattern = listOf(IntentStep(IntentType.FORECLOSE, param = 10)),
                rewards = EnemyRewards(gold = 0, cardChoices = 0)
            )),
            listOf("strike", "strike", "strike", "strike", "strike"),
            startingDebt = 0
        )
        val before = engine.getState().player.hp
        engine.endPlayerTurn()
        assertEquals(before - 5, engine.getState().player.hp)
    }
}
