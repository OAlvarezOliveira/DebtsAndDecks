package com.debtsdecks.core.combat.resolution

import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.DebtConfig
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
 * Covers the debt-resource-mechanic Phase 4 card-rework effects: [CardResolver.Effect.RepayDebt],
 * [CardResolver.Effect.GainGold], [CardResolver.Effect.WipeDebt], [CardResolver.Effect.EscrowShieldActivate],
 * and the `"debt_scaling"` tag branch (Compound Interest). `CardResolver.resolve` is a pure function,
 * so these are exercised directly without a full [com.debtsdecks.core.combat.CombatEngine].
 */
class CardResolverTest {

    private val resolver = CardResolver(testLocalizer())

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
        intentType = IntentType.ATTACK,
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
            selfDamage = DebtConfig.CHAPTER_11_HP_COST,
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

    // --- Debt-Economy (debt-economy-cards-and-boss-interest, PR1): new effect primitives ---

    @Test
    fun `add-debt card emits an AddDebt effect`() {
        val def = CardDefinition(
            id = "subprime_loan", name = "Subprime Loan", type = CardType.SKILL, cost = 0,
            debtAdd = 3, creditGain = 3, targetType = TargetType.SELF,
            description = "Gain 3 Credit this turn; add 3 Debt.", rarity = Rarity.UNCOMMON,
            tags = setOf("add_debt", "gain_credit")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState(debt = 10))

        val addDebt = result.effects.filterIsInstance<CardResolver.Effect.AddDebt>()
        assertEquals(1, addDebt.size)
        assertEquals(3, addDebt.single().amount)
    }

    @Test
    fun `gain-credit card emits a GainCredit effect`() {
        val def = CardDefinition(
            id = "golden_credit", name = "Golden Credit", type = CardType.SKILL, cost = 2,
            creditGain = 4, targetType = TargetType.SELF,
            description = "Gain 4 Credit this turn.", rarity = Rarity.UNCOMMON, tags = setOf("gain_credit")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState())

        val gainCredit = result.effects.filterIsInstance<CardResolver.Effect.GainCredit>()
        assertEquals(1, gainCredit.size)
        assertEquals(4, gainCredit.single().amount)
    }

    @Test
    fun `asset-auction card emits an ExhaustFromHand cost and a GainGold reward`() {
        val def = CardDefinition(
            id = "asset_auction", name = "Asset Auction", type = CardType.SKILL, cost = 1,
            goldGain = 9, targetType = TargetType.SELF,
            description = "Exhaust a card from hand; gain 9 Gold.", rarity = Rarity.UNCOMMON,
            tags = setOf("hand_exhaust")
        )
        val card = CardInstance(def)

        val result = resolver.resolve(card, null, testState())

        assertTrue(result.effects.contains(CardResolver.Effect.ExhaustFromHand))
        val gold = result.effects.filterIsInstance<CardResolver.Effect.GainGold>()
        assertEquals(1, gold.size)
        assertEquals(9, gold.single().amount)
    }

    @Test
    fun `reverse-mortgage emits Gold scaled by current Debt with floor rounding`() {
        val def = CardDefinition(
            id = "reverse_mortgage", name = "Reverse Mortgage", type = CardType.SKILL, cost = 1,
            goldGain = 4, targetType = TargetType.SELF,
            description = "Gain 4 Gold per 10 Debt.", rarity = Rarity.UNCOMMON, tags = setOf("gold_scaled_debt")
        )
        val card = CardInstance(def)

        val atTwentyFive = resolver.resolve(card, null, testState(debt = 25))
        val goldAtTwentyFive = atTwentyFive.effects.filterIsInstance<CardResolver.Effect.GainGold>()
        assertEquals(1, goldAtTwentyFive.size)
        assertEquals(8, goldAtTwentyFive.single().amount) // floor(25/10) * 4 = 8

        val atLowDebt = resolver.resolve(card, null, testState(debt = 5))
        assertFalse(atLowDebt.effects.any { it is CardResolver.Effect.GainGold }) // floor(5/10)=0 -> no-op
    }

    // --- Debt-as-Leverage: unconditional ATTACK bonus ---

    @Test
    fun `leverage adds floor(debt over 5) bonus damage to attacks`() {
        val def = CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1, damage = 6,
            targetType = TargetType.ENEMY, description = "Deal 6.", rarity = Rarity.BASIC
        )
        val card = CardInstance(def)

        // debt 0 -> base 6
        val r0 = resolver.resolve(card, "enemy-1", testState(debt = 0))
        assertEquals(6, r0.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 5 -> +0 (5/6 floor) = 6
        val r5 = resolver.resolve(card, "enemy-1", testState(debt = 5))
        assertEquals(6, r5.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 7 -> +1 (floor) = 7
        val r7 = resolver.resolve(card, "enemy-1", testState(debt = 7))
        assertEquals(7, r7.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 24 -> +4 (24/6) = 10
        val r24 = resolver.resolve(card, "enemy-1", testState(debt = 24))
        assertEquals(10, r24.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 30 -> +5 (30/6) = 11
        val r30 = resolver.resolve(card, "enemy-1", testState(debt = 30))
        assertEquals(11, r30.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    // --- Liquidation: Ejecución (execution_damage) ---

    private fun ejecucionDef() = CardDefinition(
        id = "ejecucion", name = "Foreclosure", type = CardType.ATTACK, cost = 2,
        targetType = TargetType.ENEMY, description = "Damage = half Debt, then wipe. Exhaust.",
        rarity = Rarity.RARE, tags = setOf("execution_damage", "exhaust")
    )

    @Test
    fun `ejecucion deals damage equal to half the debt and wipes it`() {
        val result = resolver.resolve(CardInstance(ejecucionDef()), "enemy-1", testState(debt = 22))

        val damages = result.effects.filterIsInstance<CardResolver.Effect.Damage>()
        assertEquals(1, damages.size)
        assertEquals(11, damages.single().amount)
        assertTrue(result.effects.contains(CardResolver.Effect.WipeDebt))
    }

    @Test
    fun `ejecucion exhausts itself so the wipe is once per combat`() {
        val result = resolver.resolve(CardInstance(ejecucionDef()), "enemy-1", testState(debt = 22))

        assertTrue(result.effects.contains(CardResolver.Effect.ExhaustSelf))
    }

    @Test
    fun `ejecucion floors the halved damage on odd debt`() {
        val result = resolver.resolve(CardInstance(ejecucionDef()), "enemy-1", testState(debt = 49))

        assertEquals(24, result.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    @Test
    fun `ejecucion never out-damages its keep-the-band sibling at the same debt`() {
        val payoffDef = CardDefinition(
            id = "asset_bubble", name = "Asset Bubble", type = CardType.ATTACK, cost = 1,
            targetType = TargetType.ENEMY, description = "Half Debt as damage; keep the Debt.",
            rarity = Rarity.RARE, tags = setOf("debt_payoff")
        )

        for (debt in intArrayOf(6, 22, 30, 49)) {
            val exec = resolver.resolve(CardInstance(ejecucionDef()), "enemy-1", testState(debt = debt))
                .effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount
            val payoff = resolver.resolve(CardInstance(payoffDef), "enemy-1", testState(debt = debt))
                .effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount

            assertTrue(
                exec <= payoff,
                "at debt=$debt Ejecucion dealt $exec and also wipes, vs $payoff for the sibling that keeps the Debt"
            )
        }
    }

    // --- Liquidation: Refinanciar (refinance) ---

    @Test
    fun `refinanciar halves debt and grants matching block`() {
        val def = CardDefinition(
            id = "refinanciar", name = "Refinance", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Halve Debt, gain Block.",
            rarity = Rarity.UNCOMMON, tags = setOf("refinance")
        )
        val card = CardInstance(def)

        // debt 22 -> cancel 11, block 11
        val r22 = resolver.resolve(card, null, testState(debt = 22))
        val block22 = r22.effects.filterIsInstance<CardResolver.Effect.Block>().single().amount
        val repay22 = r22.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().single().amount
        assertEquals(11, block22)
        assertEquals(11, repay22)

        // debt 7 -> cancel 3 (floor), block 3
        val r7 = resolver.resolve(card, null, testState(debt = 7))
        val block7 = r7.effects.filterIsInstance<CardResolver.Effect.Block>().single().amount
        val repay7 = r7.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().single().amount
        assertEquals(3, block7)
        assertEquals(3, repay7)
    }

    // --- C4 leverage-payoff-cards: debt_scaling ATTACK branch (T2.1 RED) ---

    @Test
    fun `debt_scaling attack gains extra floor(debt over 10) damage per hit on top of flat leverage`() {
        val def = CardDefinition(
            id = "leverage_strike", name = "Leverage Strike", type = CardType.ATTACK, cost = 1,
            damage = 5, targetType = TargetType.ENEMY, description = "Deal 5. Extra damage per Debt.",
            rarity = Rarity.COMMON, tags = setOf("debt_scaling")
        )
        val card = CardInstance(def)

        // debt 0 -> base 5 + flat 0 + tag 0 = 5
        val r0 = resolver.resolve(card, "enemy-1", testState(debt = 0))
        assertEquals(5, r0.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 5 -> base 5 + flat 0 (5/6) + tag 0 (5/8) = 5
        val r5 = resolver.resolve(card, "enemy-1", testState(debt = 5))
        assertEquals(5, r5.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 10 -> base 5 + flat 1 (10/6) + tag 1 (10/8) = 7
        val r10 = resolver.resolve(card, "enemy-1", testState(debt = 10))
        assertEquals(7, r10.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)

        // debt 20 -> base 5 + flat 3 (20/6) + tag 2 (20/8) = 10
        val r20 = resolver.resolve(card, "enemy-1", testState(debt = 20))
        assertEquals(10, r20.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
    }

    @Test
    fun `untagged attack at same debt gets only flat leverage, not the tag bonus`() {
        val tagged = CardDefinition(
            id = "tagged", name = "Tagged", type = CardType.ATTACK, cost = 1, damage = 5,
            targetType = TargetType.ENEMY, description = "Tagged.", rarity = Rarity.COMMON,
            tags = setOf("debt_scaling")
        )
        val plain = CardDefinition(
            id = "plain", name = "Plain", type = CardType.ATTACK, cost = 1, damage = 5,
            targetType = TargetType.ENEMY, description = "Plain.", rarity = Rarity.COMMON
        )
        val rTagged = resolver.resolve(CardInstance(tagged), "enemy-1", testState(debt = 20))
        val rPlain = resolver.resolve(CardInstance(plain), "enemy-1", testState(debt = 20))
        val taggedDmg = rTagged.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount
        val plainDmg = rPlain.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount
        assertEquals(taggedDmg, plainDmg + 2) // tag adds exactly floor(20/10) = 2 over flat-leverage-only
    }

    // --- C4: debt_payoff ATTACK branch (T2.3 RED) ---

    @Test
    fun `debt_payoff attack deals floor-debt-half damage plus flat leverage and does NOT wipe`() {
        val def = CardDefinition(
            id = "asset_bubble", name = "Asset Bubble", type = CardType.ATTACK, cost = 1,
            targetType = TargetType.ENEMY, description = "Deal damage equal to half Debt; keep the Debt.",
            rarity = Rarity.RARE, tags = setOf("debt_payoff")
        )
        val card = CardInstance(def)

        // debt 20 -> floor(min(20,40)/1) + flat(20/6=3) = 20 + 3 = 23
        val r20 = resolver.resolve(card, "enemy-1", testState(debt = 20))
        val dmg20 = r20.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount
        assertEquals(23, dmg20)
        assertFalse(r20.effects.any { it is CardResolver.Effect.WipeDebt })

        // debt 0 -> 0 damage, still no wipe
        val r0 = resolver.resolve(card, "enemy-1", testState(debt = 0))
        assertEquals(0, r0.effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount)
        assertFalse(r0.effects.any { it is CardResolver.Effect.WipeDebt })
    }

    @Test
    fun `execution_damage regression control still wipes at same debt while debt_payoff does not`() {
        val execDef = CardDefinition(
            id = "ejecucion", name = "Foreclosure", type = CardType.ATTACK, cost = 2,
            targetType = TargetType.ENEMY, description = "Damage = Debt, then wipe.",
            rarity = Rarity.RARE, tags = setOf("execution_damage")
        )
        val payoffDef = CardDefinition(
            id = "asset_bubble", name = "Asset Bubble", type = CardType.ATTACK, cost = 1,
            targetType = TargetType.ENEMY, description = "Half Debt as damage; keep the Debt.",
            rarity = Rarity.RARE, tags = setOf("debt_payoff")
        )

        val execResult = resolver.resolve(CardInstance(execDef), "enemy-1", testState(debt = 22))
        assertTrue(execResult.effects.contains(CardResolver.Effect.WipeDebt))

        val payoffResult = resolver.resolve(CardInstance(payoffDef), "enemy-1", testState(debt = 22))
        assertFalse(payoffResult.effects.any { it is CardResolver.Effect.WipeDebt })
    }

    // --- C4: debt_payoff SKILL branch (T2.5 RED) ---

    @Test
    fun `debt_payoff skill grants block equal to half debt without repaying or wiping`() {
        val def = CardDefinition(
            id = "collateral_hold", name = "Collateral Hold", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Gain Block equal to half your Debt; keep the Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("debt_payoff")
        )
        val card = CardInstance(def)

        // debt 20 -> Block = floor(min(20,40)/1) = 20
        val r20 = resolver.resolve(card, null, testState(debt = 20))
        val block20 = r20.effects.filterIsInstance<CardResolver.Effect.Block>().single().amount
        assertEquals(20, block20)
        assertFalse(r20.effects.any { it is CardResolver.Effect.RepayDebt })
        assertFalse(r20.effects.any { it is CardResolver.Effect.WipeDebt })

        // debt 7 -> Block = floor(min(7,40)/1) = 7
        val r7 = resolver.resolve(card, null, testState(debt = 7))
        assertEquals(7, r7.effects.filterIsInstance<CardResolver.Effect.Block>().single().amount)
    }

    @Test
    fun `refinanciar regression control still repays and blocks while debt_payoff skill only blocks`() {
        val refinanceDef = CardDefinition(
            id = "refinanciar", name = "Refinance", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Halve Debt, gain Block.",
            rarity = Rarity.UNCOMMON, tags = setOf("refinance")
        )
        val payoffDef = CardDefinition(
            id = "collateral_hold", name = "Collateral Hold", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Block = half Debt, keep Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("debt_payoff")
        )

        val refResult = resolver.resolve(CardInstance(refinanceDef), null, testState(debt = 22))
        assertEquals(1, refResult.effects.filterIsInstance<CardResolver.Effect.RepayDebt>().size)
        assertEquals(1, refResult.effects.filterIsInstance<CardResolver.Effect.Block>().size)

        val payoffResult = resolver.resolve(CardInstance(payoffDef), null, testState(debt = 22))
        assertEquals(1, payoffResult.effects.filterIsInstance<CardResolver.Effect.Block>().size)
        assertFalse(payoffResult.effects.any { it is CardResolver.Effect.RepayDebt })
    }

    // --- C4: debt_draw branch (T2.7 RED) ---

    @Test
    fun `debt_draw scales draw count with current debt plus base`() {
        val def = CardDefinition(
            id = "overdraft", name = "Overdraft", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Draw 1, plus 1 per 10 Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("debt_draw")
        )
        val card = CardInstance(def)

        // debt 0 -> base 1 + 0 = 1
        val r0 = resolver.resolve(card, null, testState(debt = 0))
        assertEquals(1, r0.effects.filterIsInstance<CardResolver.Effect.Draw>().single().count)

        // debt 10 -> 1 + 1 = 2
        val r10 = resolver.resolve(card, null, testState(debt = 10))
        assertEquals(2, r10.effects.filterIsInstance<CardResolver.Effect.Draw>().single().count)

        // debt 25 -> 1 + 2 = 3
        val r25 = resolver.resolve(card, null, testState(debt = 25))
        assertEquals(3, r25.effects.filterIsInstance<CardResolver.Effect.Draw>().single().count)
    }
    

    // --- card-upgrades (U3): effective values at resolution ---

    @Test
    fun `upgraded attack resolves 3 bonus damage`() {
        val def = CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1, damage = 6,
            targetType = TargetType.ENEMY, description = "Deal 6", rarity = Rarity.BASIC
        )
        val card = CardInstance(def)
        card.upgraded = true

        val result = resolver.resolve(card, "enemy-1", testState(debt = 0))

        val damage = result.effects.filterIsInstance<CardResolver.Effect.Damage>().first()
        assertEquals(9, damage.amount)
    }

    @Test
    fun `upgraded block skill resolves 2 bonus block`() {
        val def = CardDefinition(
            id = "defend", name = "Defend", type = CardType.SKILL, cost = 1, block = 5,
            targetType = TargetType.SELF, description = "Block 5", rarity = Rarity.BASIC
        )
        val card = CardInstance(def)
        card.upgraded = true

        val result = resolver.resolve(card, null, testState())

        val block = result.effects.filterIsInstance<CardResolver.Effect.Block>().first()
        assertEquals(7, block.amount)
    }

    @Test
    fun `upgraded draw skill resolves 1 bonus draw`() {
        val def = CardDefinition(
            id = "drawer", name = "Drawer", type = CardType.SKILL, cost = 1, draw = 1,
            targetType = TargetType.SELF, description = "Draw 1", rarity = Rarity.BASIC
        )
        val card = CardInstance(def)
        card.upgraded = true

        val result = resolver.resolve(card, null, testState())

        val draw = result.effects.filterIsInstance<CardResolver.Effect.Draw>().first()
        assertEquals(2, draw.count)
    }
}
