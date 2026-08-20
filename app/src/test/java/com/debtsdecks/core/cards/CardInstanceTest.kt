package com.debtsdecks.core.cards

import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `isPlayable()` / `shortfall(credit)` replace the old `canPlay(energy)` gate now that cards are
 * always playable and a Credit shortfall converts to Debt instead of blocking the play.
 */
class CardInstanceTest {

    private fun card(cost: Int = 2): CardInstance {
        val def = CardDefinition(
            id = "test_card", name = "Test Card", type = CardType.ATTACK, cost = cost,
            damage = 6, targetType = TargetType.ENEMY, description = "Deal 6 damage",
            rarity = Rarity.BASIC
        )
        return CardInstance(def)
    }

    @Test
    fun `isPlayable is true for a fresh card regardless of cost`() {
        assertTrue(card(cost = 99).isPlayable())
    }

    @Test
    fun `isPlayable is false once the card is exhausted`() {
        val instance = card()
        instance.exhausted = true
        assertFalse(instance.isPlayable())
    }

    @Test
    fun `shortfall is zero when credit exactly covers cost`() {
        assertEquals(0, card(cost = 2).shortfall(2))
    }

    @Test
    fun `shortfall is zero when credit exceeds cost`() {
        assertEquals(0, card(cost = 2).shortfall(5))
    }

    @Test
    fun `shortfall is the exact gap when credit falls short`() {
        assertEquals(3, card(cost = 5).shortfall(2))
    }

    @Test
    fun `shortfall is zero for a free card regardless of credit`() {
        assertEquals(0, card(cost = 0).shortfall(0))
    }
}
