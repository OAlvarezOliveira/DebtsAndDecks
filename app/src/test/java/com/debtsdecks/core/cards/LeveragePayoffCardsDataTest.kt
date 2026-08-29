package com.debtsdecks.core.cards

import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.simulation.TestAssetLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C4 leverage-payoff-cards data table invariants (T3.1 RED / T3.2 GREEN): every expectation below
 * is concrete (no tautologies) and maps 1:1 to the design table in sdd/leverage-payoff-cards/design
 * (obs 1452) — 6 new cards, 3 reworks, pool 17 -> 23 non-starter, 3 legible archetypes.
 */
class LeveragePayoffCardsDataTest {

    private val cards: List<com.debtsdecks.core.model.CardDefinition> = TestAssetLoader.loadCards()

    private fun byId(id: String) = cards.firstOrNull { it.id == id }

    // --- R1.1 / R1.2: 6 new + 3 reworked ids present exactly once ---

    @Test
    fun `six new C4 card ids exist exactly once`() {
        val newIds = listOf(
            "leverage_strike", "asset_bubble",
            "overdraft", "collateral_hold",
            "repo_expert", "emergency_fund"
        )
        for (id in newIds) {
            assertEquals(1, cards.count { it.id == id }, "id $id should exist exactly once")
        }
    }

    @Test
    fun `three reworked dead-card ids still exist`() {
        for (id in listOf("bounced_check", "zombie_debt", "eternal_debt")) {
            assertEquals(1, cards.count { it.id == id }, "reworked id $id must remain")
        }
    }

    // --- R1.6: reward pool 23 non-starter ---

    @Test
    fun `reward pool grows to exactly 25 non-starter cards`() {
        // Was 23 pre-FV.E1 card-pool-expansion (obs sdd/fv-e1-card-pool-expansion); +2 for
        // debt_settlement / emergency_payment, neither carrying the starter tag.
        val nonStarter = cards.filter { !it.tags.contains("starter") }
        assertEquals(25, nonStarter.size)
        // Starter count stays 4.
        assertEquals(4, cards.count { it.tags.contains("starter") })
    }

    @Test
    fun `no C4 card carries the starter tag`() {
        val c4Ids = setOf(
            "leverage_strike", "asset_bubble", "overdraft", "collateral_hold",
            "repo_expert", "emergency_fund", "bounced_check", "zombie_debt", "eternal_debt"
        )
        for (id in c4Ids) {
            assertFalse(byId(id)!!.tags.contains("starter"), "C4 card $id must not be starter")
        }
    }

    // --- R1.3: archetype coverage (concrete per-card contract) ---

    @Test
    fun `leverage archetype cards have their concrete contract`() {
        val strike = byId("leverage_strike")!!
        assertEquals(CardType.ATTACK, strike.type)
        assertEquals(Rarity.COMMON, strike.rarity)
        assertEquals(1, strike.cost)
        assertEquals(5, strike.damage)
        assertEquals(TargetType.ENEMY, strike.targetType)
        assertTrue("debt_scaling" in strike.tags)

        val bubble = byId("asset_bubble")!!
        assertEquals(CardType.ATTACK, bubble.type)
        assertEquals(Rarity.RARE, bubble.rarity)
        assertEquals(1, bubble.cost)
        assertEquals(0, bubble.damage) // payoff-driven, no base damage
        assertTrue("debt_payoff" in bubble.tags)

        val check = byId("bounced_check")!!
        assertEquals(CardType.ATTACK, check.type)
        assertEquals(1, check.cost)
        assertEquals(5, check.damage)
        assertEquals(4, check.debtAdd)
        assertTrue("add_debt" in check.tags)

        val eternal = byId("eternal_debt")!!
        assertEquals(CardType.SKILL, eternal.type)
        assertEquals(Rarity.RARE, eternal.rarity)
        assertEquals(1, eternal.cost)
        assertEquals(3, eternal.debtAdd)
        assertTrue(eternal.tags.containsAll(setOf("add_debt", "recursive", "debt_scaling")))
    }

    @Test
    fun `liquidity archetype cards have their concrete contract`() {
        val draft = byId("overdraft")!!
        assertEquals(CardType.SKILL, draft.type)
        assertEquals(Rarity.UNCOMMON, draft.rarity)
        assertEquals(1, draft.cost)
        assertTrue("debt_draw" in draft.tags)

        val hold = byId("collateral_hold")!!
        assertEquals(CardType.SKILL, hold.type)
        assertEquals(Rarity.UNCOMMON, hold.rarity)
        assertEquals(1, hold.cost)
        assertTrue("debt_payoff" in hold.tags)

        val zombie = byId("zombie_debt")!!
        assertEquals(CardType.SKILL, zombie.type)
        assertEquals(Rarity.UNCOMMON, zombie.rarity)
        assertEquals(0, zombie.cost)
        assertEquals(2, zombie.debtAdd)
        assertEquals(1, zombie.creditGain)
        assertTrue(zombie.tags.containsAll(setOf("add_debt", "recursive", "gain_credit")))
    }

    @Test
    fun `pressure archetype cards have their concrete contract`() {
        val repo = byId("repo_expert")!!
        assertEquals(CardType.ATTACK, repo.type)
        assertEquals(Rarity.COMMON, repo.rarity)
        assertEquals(1, repo.cost)
        assertEquals(7, repo.damage)
        assertEquals(1, repo.weakApply)

        val fund = byId("emergency_fund")!!
        assertEquals(CardType.SKILL, fund.type)
        assertEquals(Rarity.COMMON, fund.rarity)
        assertEquals(1, fund.cost)
        assertEquals(6, fund.block)
        assertEquals(1, fund.draw)
        assertTrue(fund.tags.isEmpty())
    }

    // --- R1.5: rarity ladder visible ---

    @Test
    fun `rarity ladder has rares in both leverage and liquidity identity cards`() {
        assertTrue(cards.any { it.id == "asset_bubble" && it.rarity == Rarity.RARE })
        assertTrue(cards.any { it.id == "eternal_debt" && it.rarity == Rarity.RARE })
    }

}