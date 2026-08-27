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

    private fun skillCard(cost: Int, block: Int = 0, draw: Int = 0): CardInstance {
        val def = CardDefinition(
            id = "test_skill", name = "Test Skill", type = CardType.SKILL, cost = cost,
            block = block, draw = draw, targetType = TargetType.SELF, description = "Skill",
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

    @Test
    fun `strike card has correct base properties`() {
        val strike = CardRegistry.create(loadCards()).getOrThrow("strike")
        assertEquals(1, strike.cost)
        assertEquals(6, strike.damage)
        assertEquals(CardType.ATTACK, strike.type)
        assertEquals(TargetType.ENEMY, strike.targetType)
        assertEquals(Rarity.BASIC, strike.rarity)
        assertTrue(strike.tags.contains("starter"))
    }

    private fun loadCards(): List<CardDefinition> {
        // In real implementation this would parse assets/cards/all.json
        return listOf(
            CardDefinition(
                id = "strike",
                name = "Strike",
                type = CardType.ATTACK,
                cost = 1,
                damage = 6,
                targetType = TargetType.ENEMY,
                description = "Deal 6 damage",
                rarity = Rarity.BASIC,
                tags = setOf("starter")
            )
        )
    }


    // --- card-upgrades: effective values (R4/R5/R6) ---

    @Test
    fun `upgraded cost1 attack gains 3 damage`() {
        val instance = card(cost = 1)
        assertEquals(6, instance.effectiveDamage)
        instance.upgraded = true
        assertEquals(9, instance.effectiveDamage)
    }

    @Test
    fun `non-upgraded attack keeps base damage`() {
        assertEquals(6, card(cost = 1).effectiveDamage)
        assertEquals(6, card(cost = 2).effectiveDamage)
    }

    @Test
    fun `cost2 attack upgraded gets no stat bonus - cost reduction priority`() {
        val instance = card(cost = 2)
        instance.upgraded = true
        assertEquals(6, instance.effectiveDamage)
    }

    @Test
    fun `upgraded block skill gains 2 block`() {
        val skill = skillCard(cost = 1, block = 5)
        skill.upgraded = true
        assertEquals(7, skill.effectiveBlock)
    }

    @Test
    fun `upgraded draw skill gains 1 draw`() {
        val skill = skillCard(cost = 1, draw = 1)
        skill.upgraded = true
        assertEquals(2, skill.effectiveDraw)
    }

    @Test
    fun `upgraded block skill does not gain draw`() {
        val skill = skillCard(cost = 1, block = 5, draw = 1)
        skill.upgraded = true
        assertEquals(1, skill.effectiveDraw)  // no draw bonus when block > 0
    }

    @Test
    fun `non-upgraded skills keep base values`() {
        val blockSkill = skillCard(cost = 1, block = 5)
        assertEquals(5, blockSkill.effectiveBlock)
        val drawSkill = skillCard(cost = 1, draw = 1)
        assertEquals(1, drawSkill.effectiveDraw)
    }

    @Test
    fun `baseCost captures the definition cost even after mutation`() {
        val instance = card(cost = 2)
        instance.cost = 1
        assertEquals(2, instance.baseCost)
    }
}
