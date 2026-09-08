package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.resolution.CardResolver
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WU1 acceptance tests for `archetypeTiers()` (synergy tier compute) and the DebtConfig
 * constants it + the band-cap / divisor-unification share. Pure-function tests over a fake
 * [CardRegistry]; one glue test drives [CombatEngine.startCombat] to confirm the tiers are
 * threaded into [com.debtsdecks.core.model.CombatState].
 */
class ArchetypeTiersTest {

    private fun levCard(id: String) = CardDefinition(
        id, id, CardType.ATTACK, 1, targetType = TargetType.ENEMY, description = "",
        rarity = Rarity.BASIC, tags = setOf("debt_scaling")
    )
    private fun liqCard(id: String) = CardDefinition(
        id, id, CardType.SKILL, 1, targetType = TargetType.SELF, description = "",
        rarity = Rarity.BASIC, tags = setOf("debt_draw")
    )
    private fun presCard(id: String) = CardDefinition(
        id, id, CardType.SKILL, 1, targetType = TargetType.SELF, description = "",
        rarity = Rarity.BASIC, tags = setOf("pressure")
    )
    private fun plainCard(id: String) = CardDefinition(
        id, id, CardType.ATTACK, 1, targetType = TargetType.ENEMY, description = "",
        rarity = Rarity.BASIC, tags = setOf("basic")
    )

    private val registry = CardRegistry.create(
        listOf(levCard("lev"), liqCard("liq"), presCard("pres"), plainCard("plain"))
    )

    // --- Requirement: Tag-Count Tier Computation (thresholds 2/4/6) ---

    @Test
    fun `3 leverage-tagged cards yields tier 1 (floor 3 over 2)`() {
        val tiers = archetypeTiers(listOf("lev", "lev", "lev"), registry)
        assertEquals(1, tiers[Archetype.LEVERAGE])
        assertEquals(0, tiers[Archetype.LIQUIDITY])
        assertEquals(0, tiers[Archetype.PRESSURE])
    }

    @Test
    fun `5 liquidity-tagged cards yields tier 2 (floor 5 over 2)`() {
        val tiers = archetypeTiers(listOf("liq", "liq", "liq", "liq", "liq"), registry)
        assertEquals(2, tiers[Archetype.LIQUIDITY])
    }

    @Test
    fun `1 leverage-tagged card yields tier 0 (sparse, no bonus)`() {
        val tiers = archetypeTiers(listOf("lev"), registry)
        assertEquals(0, tiers[Archetype.LEVERAGE])
    }

    @Test
    fun `6 or more tagged cards cap at tier 3`() {
        assertEquals(3, archetypeTiers(List(6) { "lev" }, registry)[Archetype.LEVERAGE])
        assertEquals(3, archetypeTiers(List(8) { "lev" }, registry)[Archetype.LEVERAGE])
    }

    @Test
    fun `mixed deck yields independent per-archetype tiers`() {
        val deck = List(4) { "lev" } + List(2) { "liq" } + List(2) { "pres" }
        val tiers = archetypeTiers(deck, registry)
        assertEquals(2, tiers[Archetype.LEVERAGE])   // floor(4/2)
        assertEquals(1, tiers[Archetype.LIQUIDITY])  // floor(2/2)
        assertEquals(1, tiers[Archetype.PRESSURE])   // floor(2/2), only pressure-tagged count
    }

    // --- Requirement trap: plain non-economy cards signal PRESSURE for playerArchetype() but NOT
    //     for tier thresholds ---

    @Test
    fun `plain non-economy cards do NOT count toward PRESSURE tier`() {
        val deck = listOf("plain", "plain", "plain")
        val tiers = archetypeTiers(deck, registry)
        // Trap guard: tier must be 0 even though the deck is all plain non-economy.
        assertEquals(0, tiers[Archetype.PRESSURE])
        assertEquals(0, tiers[Archetype.LEVERAGE])
        assertEquals(0, tiers[Archetype.LIQUIDITY])
        // And the same deck DOES resolve to PRESSURE via playerArchetype tie-break (the distinction).
        assertEquals(Archetype.PRESSURE, playerArchetype(deck, registry))
    }

    @Test
    fun `pressure tier only advances on pressure-tagged cards`() {
        val tiers = archetypeTiers(listOf("pres", "pres", "pres"), registry)
        assertEquals(1, tiers[Archetype.PRESSURE])
    }

    // --- Requirement: Named constants only (locked values) ---

    @Test
    fun `tier and band-cap constants hold the design's locked values`() {
        assertEquals(2, DebtConfig.ARCHETYPE_TIER_TAGS_PER_TIER)
        assertEquals(3, DebtConfig.ARCHETYPE_TIER_MAX)
        assertEquals(40, DebtConfig.LEVERAGE_PAYOFF_BAND_CAP)
        assertEquals(5, DebtConfig.LEVERAGE_PAYOFF_DIMINISHING_DIVISOR)
        assertEquals(10, DebtConfig.DEBT_STRENGTH_DIVISOR)
    }

    // --- WU1 item 3: debt_scaling SKILL strength uses the named constant (value preserved at 10) ---

    @Test
    fun `debt_scaling SKILL strength scales off DEBT_STRENGTH_DIVISOR, not a magic 10`() {
        val resolver = CardResolver(testLocalizer())
        val def = CardDefinition(
            "compound_interest", "Compound Interest", CardType.SKILL, 1,
            targetType = TargetType.SELF, description = "Gain 1 Strength per 10 Debt.",
            rarity = Rarity.COMMON, tags = setOf("debt_scaling")
        )
        val at25 = resolver.resolve(CardInstance(def), null, debtState(25))
        assertEquals(2, at25.effects.filterIsInstance<CardResolver.Effect.StrengthGain>().single().amount) // floor(25/10)
        val at100 = resolver.resolve(CardInstance(def), null, debtState(100))
        assertEquals(10, at100.effects.filterIsInstance<CardResolver.Effect.StrengthGain>().single().amount) // floor(100/10)
        val at5 = resolver.resolve(CardInstance(def), null, debtState(5))
        assertEquals(
            0,
            at5.effects.filterIsInstance<CardResolver.Effect.StrengthGain>().size // floor(5/10)=0, no-op
        )
    }

    // --- WU1 glue: CombatEngine threads tiers into CombatState ---

    @Test
    fun `combat state carries archetype tiers computed from the starting deck`() {
        val engine = CombatEngine(registry, testLocalizer())
        val deck = listOf("lev", "lev", "lev", "lev") // 4 -> tier 2
        val enemy = EnemyDefinition(
            "thug", "Thug", 30,
            listOf(IntentStep(IntentType.ATTACK, 5)), EnemyRewards(10, 1)
        )
        engine.startCombat(listOf(enemy), deck)
        val state = engine.getState()
        assertEquals(archetypeTiers(deck, registry), state.archetypeTiers)
        assertEquals(2, state.archetypeTiers[Archetype.LEVERAGE])
    }

    private fun debtState(debt: Int) = com.debtsdecks.core.model.CombatState(
        player = com.debtsdecks.core.model.PlayerState(),
        enemies = emptyList(),
        currentTurn = com.debtsdecks.core.model.TurnPhase.PLAYER_ACTION,
        energy = 3,
        maxEnergy = 3,
        hand = emptyList(),
        drawPileCount = 0,
        discardPileCount = 0,
        exhaustPileCount = 0,
        log = emptyList(),
        debt = debt
    )
}
