package com.debtsdecks.core.combat.resolution

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.i18n.testI18nBundle
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EnemyState
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the debt-resource-mechanic Phase 4 card-rework effects: [CardResolver.Effect.RepayDebt],
 * [CardResolver.Effect.GainGold], [CardResolver.Effect.WipeDebt], [CardResolver.Effect.EscrowShieldActivate],
 * and the `"debt_scaling"` tag branch (Compound Interest). `CardResolver.resolve` is a pure function,
 * so these are exercised directly without a full [com.debtsdecks.core.combat.CombatEngine].
 */
class CardResolverTest {

    private val resolver = CardResolver(testI18nBundle())

    private fun testEnemy(id: String = "enemy-1", vulnerable: Int = 0) = EnemyState(
        id = id,
        defId = "test-def",
        name = "Test Enemy",
        hp = 50,
        maxHp = 50,
        block = 0,
        strength = 0,
        weak = 0,
        vulnerable = vulnerable,
        poison = 0,
        intentType = "ATTACK",
        intentDamage = 5,
        intentParam = 0,
        intentDisplayName = "Attack",
        intentIconName = "sword"
    )

    private fun testState(debt: Int = 0, enemies: List<EnemyState> = listOf(testEnemy())) = CombatState(
        player = PlayerState(),
        enemies = enemies,
        currentTurn = TurnPhase.PLAYER_ACTION,
        energy = 3,
        maxEnergy = 3,
        hand = emptyList(),
        drawPileCount = 0,
        discardPileCount = 0,
        exhaustPileCount = 0,
        log = emptyList(),
        debt = debt
    )

    // --- Debt Relief: SKILL, debtRepay=10, once per play ---

    @Test
    fun `Debt Relief repays a flat amount of Debt directly`() {
        val def = CardDefinition(
            id = "debt_relief", name = "Debt Relief", type = CardType.SKILL, cost = 1,
            debtRepay = 10, targetType = TargetType.SELF, description = "Exhaust. Repay 10 Debt directly.",
            rarity = Rarity.UNCOMMON, tags = setOf("exhaust")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState(debt = 20))

        val repayEffects = result.effects.filterIsInstance<CardResolver.Effect.RepayDebt>()
        assertEquals(1, repayEffects.size)
        assertEquals(10, repayEffects.single().amount)
        assertTrue(result.effects.contains(CardResolver.Effect.ExhaustSelf))
    }

    // --- Wage Garnishment: ATTACK, single hit, debtRepay=3 ---

    @Test
    fun `Wage Garnishment deals damage and repays Debt once for a single hit`() {
        val def = CardDefinition(
            id = "wage_garnishment", name = "Wage Garnishment", type = CardType.ATTACK, cost = 1,
            damage = 4, debtRepay = 3, targetType = TargetType.ENEMY,
            description = "Deal 4 damage. Repay 3 Debt.", rarity = Rarity.COMMON
        )
        val card = CardInstance(def)
        val enemy = testEnemy(id = "enemy-1")

        val result = resolver.resolve(card, "enemy-1", testState(debt = 15, enemies = listOf(enemy)))

        assertEquals(1, result.effects.filterIsInstance<CardResolver.Effect.Damage>().size)
        val repayEffects = result.effects.filterIsInstance<CardResolver.Effect.RepayDebt>()
        assertEquals(1, repayEffects.size)
        assertEquals(3, repayEffects.single().amount)
    }

    // --- Collections Call: ATTACK, hits=3, debtRepay=2 per landed hit (triangulation vs. Wage Garnishment) ---

    @Test
    fun `Collections Call repays Debt once per landed hit across its 3-hit attack`() {
        val def = CardDefinition(
            id = "collections_call", name = "Collections Call", type = CardType.ATTACK, cost = 1,
            damage = 4, debtRepay = 2, hits = 3, targetType = TargetType.ENEMY,
            description = "Deal 4 damage 3 times. Each hit repays 2 Debt.", rarity = Rarity.UNCOMMON
        )
        val card = CardInstance(def)
        val enemy = testEnemy(id = "enemy-1")

        val result = resolver.resolve(card, "enemy-1", testState(debt = 15, enemies = listOf(enemy)))

        assertEquals(3, result.effects.filterIsInstance<CardResolver.Effect.Damage>().size)
        val repayEffects = result.effects.filterIsInstance<CardResolver.Effect.RepayDebt>()
        assertEquals(3, repayEffects.size)
        assertTrue(repayEffects.all { it.amount == 2 })
    }

    // --- Repo Sweep: ATTACK, ALL_ENEMIES, goldGain=5 exactly once regardless of enemy count ---

    @Test
    fun `Repo Sweep grants Gold exactly once even when it hits multiple enemies`() {
        val def = CardDefinition(
            id = "repo_sweep", name = "Repo Sweep", type = CardType.ATTACK, cost = 2,
            damage = 6, goldGain = 5, targetType = TargetType.ALL_ENEMIES,
            description = "Deal 6 damage to ALL enemies. Gain 5 Gold.", rarity = Rarity.UNCOMMON
        )
        val card = CardInstance(def)
        val enemies = listOf(testEnemy(id = "enemy-1"), testEnemy(id = "enemy-2"))

        val result = resolver.resolve(card, null, testState(enemies = enemies))

        assertEquals(2, result.effects.filterIsInstance<CardResolver.Effect.Damage>().size)
        val goldEffects = result.effects.filterIsInstance<CardResolver.Effect.GainGold>()
        assertEquals(1, goldEffects.size)
        assertEquals(5, goldEffects.single().amount)
    }

    // --- Chapter 11: SKILL, tag "wipe_debt", HP cost from DebtConfig.CHAPTER_11_HP_COST ---

    @Test
    fun `Chapter 11 wipes Debt and costs HP equal to DebtConfig CHAPTER_11_HP_COST`() {
        val def = CardDefinition(
            id = "chapter_11", name = "Chapter 11", type = CardType.SKILL, cost = 2,
            targetType = TargetType.SELF, description = "Exhaust. Lose 15 HP. Wipe all Debt to 0.",
            rarity = Rarity.RARE, tags = setOf("exhaust", "wipe_debt")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState(debt = 175))

        assertTrue(result.effects.contains(CardResolver.Effect.WipeDebt))
        val selfDamage = result.effects.filterIsInstance<CardResolver.Effect.SelfDamage>()
        assertEquals(1, selfDamage.size)
        assertEquals(DebtConfig.CHAPTER_11_HP_COST, selfDamage.single().amount)
        assertTrue(result.effects.contains(CardResolver.Effect.ExhaustSelf))
    }

    // --- Escrow Shield: POWER, tag "escrow_shield_activate" ---

    @Test
    fun `Escrow Shield emits an EscrowShieldActivate effect when played`() {
        val def = CardDefinition(
            id = "escrow_shield", name = "Escrow Shield", type = CardType.POWER, cost = 1,
            targetType = TargetType.SELF,
            description = "Activate Escrow Shield: Debt gained from borrowing is halved for the rest of combat.",
            rarity = Rarity.UNCOMMON, tags = setOf("escrow_shield_activate")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState())

        assertTrue(result.effects.contains(CardResolver.Effect.EscrowShieldActivate))
    }

    // --- Compound Interest: SKILL, tag "debt_scaling", floor(debt/10) Strength (triangulated) ---

    @Test
    fun `Compound Interest scales Strength gain with current Debt, floor rounding`() {
        val def = CardDefinition(
            id = "compound_interest", name = "Compound Interest", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Exhaust. Gain 1 Strength per 10 Debt.",
            rarity = Rarity.COMMON, tags = setOf("exhaust", "debt_scaling")
        )
        val card = CardInstance(def)

        val resultAtTwentyFive = resolver.resolve(card, null, testState(debt = 25))
        val strengthAtTwentyFive = resultAtTwentyFive.effects.filterIsInstance<CardResolver.Effect.StrengthGain>()
        assertEquals(1, strengthAtTwentyFive.size)
        assertEquals(2, strengthAtTwentyFive.single().amount) // floor(25/10) = 2

        val resultAtHundred = resolver.resolve(card, null, testState(debt = 100))
        val strengthAtHundred = resultAtHundred.effects.filterIsInstance<CardResolver.Effect.StrengthGain>()
        assertEquals(1, strengthAtHundred.size)
        assertEquals(10, strengthAtHundred.single().amount) // floor(100/10) = 10

        val resultAtLowDebt = resolver.resolve(card, null, testState(debt = 5))
        assertFalse(resultAtLowDebt.effects.any { it is CardResolver.Effect.StrengthGain }) // floor(5/10) = 0, no-op
    }
}
