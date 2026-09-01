package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.resolution.CardResolver
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.i18n.testLocalizer
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
 * WU3 acceptance tests: PRESSURE cards + synergy tiers (T3.1–T3.6; T3.7 AUDIT-punish is deferred
 * to a follow-up PR and intentionally NOT covered here). Pure-function checks over [CardResolver]
 * plus an end-to-end [CombatEngine] check for the low-debt escalator end-of-turn trigger.
 */
class PressureTest {

    private val resolver = CardResolver(testLocalizer())

    private fun testEnemy(id: String = "enemy-1", hp: Int = 50, maxHp: Int = 50) = EnemyState(
        id = id,
        defId = "test-def",
        name = "Test Enemy",
        hp = hp,
        maxHp = maxHp,
        block = 0,
        strength = 0,
        weak = 0,
        vulnerable = 0,
        poison = 0,
        intentType = IntentType.ATTACK,
        intentDamage = 5,
        intentParam = 0,
        intentDisplayName = "Attack",
        intentIconName = "sword"
    )

    private fun state(debt: Int, tiers: Map<Archetype, Int> = emptyMap(), enemyHp: Int = 50, enemyMaxHp: Int = 50) = CombatState(
        player = PlayerState(),
        enemies = listOf(testEnemy(hp = enemyHp, maxHp = enemyMaxHp)),
        currentTurn = TurnPhase.PLAYER_ACTION,
        energy = 3,
        maxEnergy = 3,
        hand = emptyList(),
        drawPileCount = 0,
        discardPileCount = 0,
        exhaustPileCount = 0,
        log = emptyList(),
        debt = debt,
        archetypeTiers = tiers
    )

    private fun resolve(def: CardDefinition, debt: Int, tiers: Map<Archetype, Int> = emptyMap(), enemyHp: Int = 50, enemyMaxHp: Int = 50) =
        resolver.resolve(CardInstance(def), "enemy-1", state(debt, tiers, enemyHp, enemyMaxHp))

    // --- T3.1: PRESSURE status tier escalation ---

    private fun weakPressureDef() = CardDefinition(
        id = "weak_pressure", name = "Pressure Weakener", type = CardType.SKILL, cost = 1,
        weakApply = 2, vulnerableApply = 1, targetType = TargetType.ENEMY,
        description = "Apply 2 Weak. Apply 1 Vulnerable.",
        rarity = Rarity.COMMON, tags = setOf("pressure")
    )

    @Test
    fun `pressure-tagged weak-stacker applies base weak and vulnerable at tier 0`() {
        val r = resolve(weakPressureDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 0))
        assertEquals(2, r.effects.filterIsInstance<CardResolver.Effect.WeakApply>().single().turns)
        assertEquals(1, r.effects.filterIsInstance<CardResolver.Effect.VulnerableApply>().single().turns)
    }

    @Test
    fun `pressure tier 1 escalates weak and vulnerable applications by the tier`() {
        // Synergy "Status escalation": weakApply 2 -> 3, vulnerableApply 1 -> 2 at tier 1.
        val r = resolve(weakPressureDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 1))
        assertEquals(3, r.effects.filterIsInstance<CardResolver.Effect.WeakApply>().single().turns)
        assertEquals(2, r.effects.filterIsInstance<CardResolver.Effect.VulnerableApply>().single().turns)
    }

    @Test
    fun `pressure tier 3 escalates weak and vulnerable by three`() {
        val r = resolve(weakPressureDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 3))
        assertEquals(5, r.effects.filterIsInstance<CardResolver.Effect.WeakApply>().single().turns) // 2 + 3
        assertEquals(4, r.effects.filterIsInstance<CardResolver.Effect.VulnerableApply>().single().turns) // 1 + 3
    }

    // --- False-positive trap: plain (non-economy) card != PRESSURE tier ---

    @Test
    fun `plain non-pressure weak card gets no pressure tier bonus even at high pressure tier`() {
        val plain = CardDefinition(
            id = "plain_weak", name = "Plain Weak", type = CardType.SKILL, cost = 1,
            weakApply = 2, targetType = TargetType.ENEMY, description = "Apply 2 Weak.",
            rarity = Rarity.COMMON // no "pressure" tag
        )
        val r = resolve(plain, debt = 0, tiers = mapOf(Archetype.PRESSURE to 3))
        // Tier does NOT leak to non-pressure cards: weak stays at the base 2.
        assertEquals(2, r.effects.filterIsInstance<CardResolver.Effect.WeakApply>().single().turns)
    }

    // --- T3.2: PRESSURE low-HP damage bonus (tier 2+) ---

    private fun pressureAttackDef(damage: Int = 5) = CardDefinition(
        id = "pressure_strike", name = "Pressure Strike", type = CardType.ATTACK, cost = 1,
        damage = damage, targetType = TargetType.ENEMY, description = "Deal damage.",
        rarity = Rarity.COMMON, tags = setOf("pressure")
    )

    @Test
    fun `pressure attack below tier 2 gets no low-HP bonus even at low enemy HP`() {
        // debt 0 -> base 5; tier 1 so no +20%; enemy at 10/50 (<50%) should NOT get the bonus.
        val r = resolve(pressureAttackDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 1), enemyHp = 10, enemyMaxHp = 50)
        assertEquals(5, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    @Test
    fun `pressure attack at tier 2 deals plus 20 percent when enemy is below half HP`() {
        // debt 0 -> base 5; tier 2 -> +20% (5 -> 6) while enemy at 10/50 (<50%).
        val r = resolve(pressureAttackDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 2), enemyHp = 10, enemyMaxHp = 50)
        assertEquals(6, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    @Test
    fun `pressure attack at tier 2 deals base damage when enemy is at or above half HP`() {
        // enemy at 30/50 (>=50%) -> no low-HP bonus, even at tier 2.
        val r = resolve(pressureAttackDef(), debt = 0, tiers = mapOf(Archetype.PRESSURE to 2), enemyHp = 30, enemyMaxHp = 50)
        assertEquals(5, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    @Test
    fun `non-pressure attack gets no low-HP bonus at high pressure tier`() {
        val plain = CardDefinition(
            id = "plain_strike", name = "Plain Strike", type = CardType.ATTACK, cost = 1,
            damage = 5, targetType = TargetType.ENEMY, description = "Deal damage.",
            rarity = Rarity.COMMON
        )
        val r = resolve(plain, debt = 0, tiers = mapOf(Archetype.PRESSURE to 3), enemyHp = 10, enemyMaxHp = 50)
        // No "pressure" tag -> no escalation bonus.
        assertEquals(5, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    // --- T3.3: Paydown strike (baseDamage + debtRepaid) ---

    private fun paydownDef() = CardDefinition(
        id = "paydown_strike", name = "Paydown Strike", type = CardType.ATTACK, cost = 1,
        damage = 4, debtRepay = 3, targetType = TargetType.ENEMY,
        description = "Deal 4 damage. Repay 3 Debt.",
        rarity = Rarity.COMMON, tags = setOf("pressure", "paydown")
    )

    @Test
    fun `paydown strike deals base damage plus the repaid debt and repays it`() {
        // debt 15 -> unconditional leverage floor(15/6)=2, paydown bonus min(3,15)=3.
        // Total damage = 4 + 2 + 3 = 9 (the spec's simplified "4+3=7" omits the leverage term
        // that every attack already carries; see WU3 deviations). Repay amount = card debtRepay = 3.
        val r = resolve(paydownDef(), debt = 15)
        assertEquals(9, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
        assertEquals(1, r.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().size)
        assertEquals(3, r.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().single().amount)
    }

    @Test
    fun `paydown strike at zero debt deals only base damage with no bonus and no negative`() {
        // debt 0 -> leverage 0, paydown bonus min(3,0)=0, damage = 4 (no bonus, no negative).
        // The RepayDebt effect is still emitted (amount = card debtRepay = 3) but clamps to 0 at
        // apply time, so the "zero debt fallback" is satisfied: damage equals baseDamage only.
        val r = resolve(paydownDef(), debt = 0)
        assertEquals(4, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
        assertEquals(3, r.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().single().amount)
    }

    @Test
    fun `paydown bonus is clamped to available debt`() {
        // debt 2 -> leverage floor(2/6)=0, paydown bonus min(3,2)=2 -> damage = 4 + 2 = 6.
        val r = resolve(paydownDef(), debt = 2)
        assertEquals(6, r.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
        assertEquals(3, r.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().single().amount)
    }

    // --- T3.5/T3.6: Low-debt escalator POWER end-of-turn trigger (via CombatEngine) ---

    private val escalatorDef = CardDefinition(
        id = "low_debt_escalator", name = "Low-Debt Escalator", type = CardType.POWER, cost = 1,
        targetType = TargetType.SELF, description = "End of turn: +1 Strength if Debt < 15.",
        rarity = Rarity.UNCOMMON, tags = setOf("pressure", "low_debt_bonus")
    )

    private fun engineWithEscalator(startingDebt: Int): com.debtsdecks.core.combat.CombatEngine {
        val strike = CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1, damage = 6,
            targetType = TargetType.ENEMY, description = "Deal 6 damage", rarity = Rarity.BASIC
        )
        val registry = com.debtsdecks.core.cards.CardRegistry.create(listOf(strike, escalatorDef))
        val engine = com.debtsdecks.core.combat.CombatEngine(registry, testLocalizer(), kotlin.random.Random(42))
        val thug = com.debtsdecks.core.enemies.EnemyDefinition(
            id = "thug", name = "Thug", hp = 100,
            intentPattern = listOf(com.debtsdecks.core.enemies.IntentStep(IntentType.ATTACK, 6)),
            rewards = com.debtsdecks.core.enemies.EnemyRewards(gold = 10, cardChoices = 3)
        )
        // Use a deck of all-strike plus one escalator so the escalator is guaranteed in the opening hand.
        engine.startCombat(listOf(thug), listOf("low_debt_escalator", "strike", "strike", "strike", "strike"), startingDebt = startingDebt)
        return engine
    }

    @Test
    fun `low-debt escalator grants strength at end of turn when debt is below the threshold`() {
        val engine = engineWithEscalator(startingDebt = 0) // 0 < 15
        val card = engine.getState().hand.first { it.cardId == "low_debt_escalator" }
        assertTrue(engine.playCard(card.id, null).success)
        assertEquals(0, engine.getState().player.strength) // not yet — only at end of turn

        engine.endPlayerTurn()
        assertEquals(1, engine.getState().player.strength) // +1 Strength for the active stack
    }

    @Test
    fun `low-debt escalator does not grant strength when debt is at or above the threshold`() {
        val engine = engineWithEscalator(startingDebt = 30) // 30 >= 15
        val card = engine.getState().hand.first { it.cardId == "low_debt_escalator" }
        assertTrue(engine.playCard(card.id, null).success)

        engine.endPlayerTurn()
        assertEquals(0, engine.getState().player.strength) // no bonus: Debt above threshold
    }
}
