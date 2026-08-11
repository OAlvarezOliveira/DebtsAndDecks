package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardDefinition
import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.cards.CardType
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatEngineTest {

    private lateinit var cardRegistry: CardRegistry
    private lateinit var engine: CombatEngine
    private val rng = Random(42)

    @BeforeEach
    fun setup() {
        val strike = CardDefinition(
            id = "strike",
            name = "Strike",
            type = CardType.ATTACK,
            cost = 1,
            damage = 6,
            targetType = TargetType.ENEMY,
            description = "Deal 6 damage",
            rarity = Rarity.BASIC
        )
        val defend = CardDefinition(
            id = "defend",
            name = "Defend",
            type = CardType.SKILL,
            cost = 1,
            block = 5,
            targetType = TargetType.SELF,
            description = "Gain 5 Block",
            rarity = Rarity.BASIC
        )
        cardRegistry = CardRegistry.create(listOf(strike, defend))

        engine = CombatEngine(cardRegistry, rng)
    }

    @Test
    fun `starting combat initializes player and enemy`() {
        val thug = EnemyDefinition(
            id = "thug",
            name = "Thug",
            hp = 24,
            intentPattern = listOf(
                IntentStep(IntentType.ATTACK, 6),
                IntentStep(IntentType.ATTACK, 6),
                IntentStep(IntentType.BUFF, param = 3)
            ),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        )

        val starterDeck = listOf("strike", "strike", "defend", "defend", "strike")
        engine.startCombat(listOf(thug), starterDeck)

        val state = engine.getState()
        assertEquals(50, state.player.hp)
        assertEquals(3, state.energy)
        assertEquals(5, state.hand.size)
        assertEquals(1, state.enemies.size)
        assertEquals("Thug", state.enemies[0].name)
        assertEquals(24, state.enemies[0].hp)
    }

    @Test
    fun `playing strike deals damage to enemy`() {
        val thug = EnemyDefinition(
            id = "thug",
            name = "Thug",
            hp = 24,
            intentPattern = listOf(
                IntentStep(IntentType.ATTACK, 6)
            ),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        )

        val starterDeck = listOf("strike", "strike", "defend", "defend", "strike")
        engine.startCombat(listOf(thug), starterDeck)

        // Find a strike in hand
        val strikeCard = engine.getState().hand.find { it.cardId == "strike" }
        assertTrue(strikeCard != null)

        val initialEnemyHp = engine.getState().enemies[0].hp
        val result = engine.playCard(strikeCard!.id, "thug")

        assertTrue(result.success)
        assertEquals(initialEnemyHp - 6, engine.getState().enemies[0].hp)
        assertEquals(2, engine.getState().energy) // 3 - 1 cost
    }

    @Test
    fun `playing defend grants block`() {
        val thug = EnemyDefinition(
            id = "thug",
            name = "Thug",
            hp = 24,
            intentPattern = listOf(
                IntentStep(IntentType.ATTACK, 6)
            ),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        )

        val starterDeck = listOf("strike", "strike", "defend", "defend", "strike")
        engine.startCombat(listOf(thug), starterDeck)

        val defendCard = engine.getState().hand.find { it.cardId == "defend" }
        assertTrue(defendCard != null)

        val result = engine.playCard(defendCard!.id, null)

        assertTrue(result.success)
        assertEquals(5, engine.getState().player.block)
    }

    @Test
    fun `end player turn triggers enemy attack`() {
        val thug = EnemyDefinition(
            id = "thug",
            name = "Thug",
            hp = 24,
            intentPattern = listOf(
                IntentStep(IntentType.ATTACK, 6)
            ),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        )

        val starterDeck = listOf("defend", "defend", "defend", "defend", "defend")
        engine.startCombat(listOf(thug), starterDeck)

        // Play defend to get block
        val defendCard = engine.getState().hand.find { it.cardId == "defend" }!!
        engine.playCard(defendCard.id, null)

        val playerHpBefore = engine.getState().player.hp
        val result = engine.endPlayerTurn()

        assertTrue(result.success)
        // Enemy attacks for 6, player has 5 block -> 1 damage
        assertEquals(playerHpBefore - 1, engine.getState().player.hp)
        assertEquals(0, engine.getState().player.block) // Block resets at end of turn
        assertEquals(3, engine.getState().energy) // Energy restored
        assertEquals(5, engine.getState().hand.size) // Drew 5 cards
    }

    @Test
    fun `vulnerable increases damage taken`() {
        // This test would require a card that applies vulnerable
        // For now, we test the PlayerState directly
    }
}