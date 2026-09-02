package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.combat.resolution.CardResolver
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
import org.junit.jupiter.api.Test

/**
 * WU2 acceptance tests: T2.1 (band-capped LEVERAGE payoff) and T2.3 (Leverage archetype tier
 * damage). Pure-function checks over [DebtConfig.leveragePayoffBandCapped] plus resolver-level
 * checks driving [CardResolver] directly with a hand-built [CombatState] carrying tiers.
 */
class LeverageBandCapTest {

    private val resolver = CardResolver(testLocalizer())

    private fun testEnemy(id: String = "enemy-1") = EnemyState(
        id = id,
        defId = "test-def",
        name = "Test Enemy",
        hp = 50,
        maxHp = 50,
        block = 0,
        strength = 0,
        weak = 0,
        vulnerable = 0,
        poison = 0,
        intentType = com.debtsdecks.core.enemies.IntentType.ATTACK,
        intentDamage = 5,
        intentParam = 0,
        intentDisplayName = "Attack",
        intentIconName = "sword"
    )

    private fun state(debt: Int, tiers: Map<Archetype, Int> = emptyMap()) = CombatState(
        player = PlayerState(),
        enemies = listOf(testEnemy()),
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

    private fun dmgOf(def: CardDefinition, debt: Int, tiers: Map<Archetype, Int> = emptyMap()): Int =
        resolver.resolve(CardInstance(def), "enemy-1", state(debt, tiers))
            .effects.filterIsInstance<CardResolver.Effect.Damage>().single().amount

    private fun blockOf(def: CardDefinition, debt: Int): Int =
        resolver.resolve(CardInstance(def), null, state(debt))
            .effects.filterIsInstance<CardResolver.Effect.Block>().single().amount

    // --- T2.1: band-cap payoff component (pure helper) ---

    @Test
    fun `band-cap payoff is linear below the cap (debt 30 to 15)`() {
        assertEquals(15, DebtConfig.leveragePayoffBandCapped(30))
        assertEquals(7, DebtConfig.leveragePayoffBandCapped(15))
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(40))
    }

    @Test
    fun `band-cap payoff is frozen above the cap so EXECUTION-1 parks equal to the cap`() {
        // Orchestrator-specified guard: debt=49 (EXECUTION-1) MUST yield the SAME band-capped
        // payoff component as debt=40 (the cap), i.e. no extra reward for over-leveraging.
        assertEquals(
            DebtConfig.leveragePayoffBandCapped(40),
            DebtConfig.leveragePayoffBandCapped(49)
        )
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(40))
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(49))
    }

    @Test
    fun `band-cap payoff stays frozen far above the cap (50 and 100 to 20)`() {
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(50))
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(100))
        assertEquals(20, DebtConfig.leveragePayoffBandCapped(199))
    }

    // --- T2.1: resolver wires the band cap into the debt_payoff role ---

    @Test
    fun `debt_payoff ATTACK uses the band-capped payoff component`() {
        val def = CardDefinition(
            id = "asset_bubble", name = "Asset Bubble", type = CardType.ATTACK, cost = 1,
            targetType = TargetType.ENEMY, description = "Deal damage equal to half Debt; keep the Debt.",
            rarity = Rarity.RARE, tags = setOf("debt_payoff")
        )
        // debt 20 -> floor(20/2)=10 + flat(20/6=3) + tier0 = 13
        assertEquals(13, dmgOf(def, 20))
        // debt 30 -> 15 + 5 + 0 = 20
        assertEquals(20, dmgOf(def, 30))
        // debt 50 -> band-cap floor(40/2)=20 + flat(50/6=8) + 0 = 28 (NOT floor(50/2)=25 + 8 = 33)
        assertEquals(28, dmgOf(def, 50))
    }

    @Test
    fun `debt_payoff SKILL block uses the band-capped payoff component`() {
        val def = CardDefinition(
            id = "collateral_hold", name = "Collateral Hold", type = CardType.SKILL, cost = 1,
            targetType = TargetType.SELF, description = "Block = half Debt, keep Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("debt_payoff")
        )
        assertEquals(10, blockOf(def, 20))
        assertEquals(20, blockOf(def, 50)) // frozen at floor(40/2)=20, not floor(50/2)=25
    }

    // --- T2.3: Leverage archetype tier damage ---

    @Test
    fun `leverage-tagged attack stacks tier bonus on flat leverage (debt24, tier2 to leverage portion 6)`() {
        // debt_scaling is a Leverage tag. damage 0 isolates the leverage contribution:
        //   flat floor(24/6)=4  +  taggedScale floor(24/8)=3  +  tier 2  = 9
        // leverage-driven portion (flat + tier) = 6, matching the synergy acceptance scenario.
        val def = CardDefinition(
            id = "leverage_strike", name = "Leverage Strike", type = CardType.ATTACK, cost = 1,
            damage = 0, targetType = TargetType.ENEMY, description = "Deal damage per Debt.",
            rarity = Rarity.COMMON, tags = setOf("debt_scaling")
        )
        val tier0 = dmgOf(def, 24, mapOf(Archetype.LEVERAGE to 0)) // 0 + 0 + 4 + 3 + 0 = 7
        val tier2 = dmgOf(def, 24, mapOf(Archetype.LEVERAGE to 2)) // 0 + 0 + 4 + 3 + 2 = 9
        assertEquals(7, tier0)
        assertEquals(9, tier2)
        assertEquals(2, tier2 - tier0) // tier adds exactly the tier value
    }

    @Test
    fun `non-leverage attack receives no tier bonus even at high leverage tier`() {
        val def = CardDefinition(
            id = "plain_strike", name = "Plain Strike", type = CardType.ATTACK, cost = 1,
            damage = 0, targetType = TargetType.ENEMY, description = "Plain.",
            rarity = Rarity.COMMON
        )
        // Only the unconditional flat leverage floor(24/6)=4 applies; tier does NOT leak to non-leverage cards.
        assertEquals(4, dmgOf(def, 24, mapOf(Archetype.LEVERAGE to 3)))
    }

    @Test
    fun `debt_payoff attack also gains the leverage tier bonus`() {
        val def = CardDefinition(
            id = "asset_bubble", name = "Asset Bubble", type = CardType.ATTACK, cost = 1,
            targetType = TargetType.ENEMY, description = "Deal damage equal to half Debt; keep the Debt.",
            rarity = Rarity.RARE, tags = setOf("debt_payoff")
        )
        // debt 24, tier 2: payoff floor(24/2)=12 + flat floor(24/6)=4 + tier 2 = 18
        assertEquals(18, dmgOf(def, 24, mapOf(Archetype.LEVERAGE to 2)))
        // tier 0: 12 + 4 = 16
        assertEquals(16, dmgOf(def, 24, mapOf(Archetype.LEVERAGE to 0)))
    }
}
