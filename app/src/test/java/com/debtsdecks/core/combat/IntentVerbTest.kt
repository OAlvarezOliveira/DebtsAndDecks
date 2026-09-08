package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.EnemyTier
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * FV deliverable 1 (E1 verb mechanics, engine side): FORECLOSE and HEDGE resolve as engine-owned
 * intents in [CombatEngine.endPlayerTurn] because both read the player's Debt, which only the
 * engine owns. FORECLOSE seizes if Debt is at/above its announced threshold; HEDGE arms the enemy
 * with block scaled by the player's Debt (mirror of the player's leaky floor(debt/6) leverage).
 */
class IntentVerbTest {

    private lateinit var cardRegistry: CardRegistry
    private lateinit var engine: CombatEngine
    private val rng = Random(7)

    @BeforeEach
    fun setup() {
        val strike = CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1, damage = 6,
            targetType = TargetType.ENEMY, description = "Deal 6 damage", rarity = Rarity.BASIC
        )
        cardRegistry = CardRegistry.create(listOf(strike))
        engine = CombatEngine(cardRegistry, testLocalizer(), rng)
    }

    private fun start(
        intent: IntentType, damage: Int = 0, param: Int = 0,
        startingDebt: Int = 0
    ) {
        val enemy = EnemyDefinition(
            id = "debtor", name = "Debtor",
            hp = 60,
            intentPattern = listOf(IntentStep(intent, damage, param)),
            rewards = EnemyRewards(gold = 10, cardChoices = 3),
            tier = EnemyTier.BOSS
        )
        engine.startCombat(listOf(enemy), listOf("strike", "strike", "strike"), startingDebt = startingDebt)
    }

    private fun resolveIntent(): Unit {
        engine.endPlayerTurn()
    }

    @Test
    fun `FORECLOSE ends the run when debt is at or above its threshold`() {
        start(IntentType.FORECLOSE, damage = 14, param = 30, startingDebt = 30)
        resolveIntent()
        assertEquals(0, engine.getState().player.hp, "the seizure is run-ending at Debt == threshold")
    }

    @Test
    fun `FORECLOSE calls a fee below its threshold and seizes at or above it`() {
        start(IntentType.FORECLOSE, damage = 9, param = 30, startingDebt = 30)
        resolveIntent()
        assertEquals(0, engine.getState().player.hp, "at/above the threshold the seizure is run-ending")

        start(IntentType.FORECLOSE, damage = 9, param = 30, startingDebt = 10)
        val hpBefore = engine.getState().player.hp
        resolveIntent()
        assertEquals(hpBefore - 9, engine.getState().player.hp, "below the threshold the creditor still calls in the fee")
    }

    @Test
    fun `HEDGE arms the enemy with block equal to the player debt divided by six`() {
        start(IntentType.HEDGE, startingDebt = 30)
        resolveIntent()
        assertEquals(5, engine.getState().enemies[0].block, "floor(30 / 6) block")
    }

    @Test
    fun `HEDGE grants no block at zero debt`() {
        start(IntentType.HEDGE, startingDebt = 0)
        resolveIntent()
        assertEquals(0, engine.getState().enemies[0].block, "no debt, no hedge")
    }

    @Test
    fun `FORECLOSE seizure increments the instrumentation counter`() {
        start(IntentType.FORECLOSE, damage = 9, param = 30, startingDebt = 30)
        resolveIntent()
        assertEquals(1, engine.forecloseSeizureCount, "at/above the threshold the seizure counts")
    }

    @Test
    fun `FORECLOSE fee branch does not increment the seizure counter`() {
        start(IntentType.FORECLOSE, damage = 9, param = 30, startingDebt = 10)
        resolveIntent()
        assertEquals(0, engine.forecloseSeizureCount, "below the threshold only the fee fires")
    }

    @Test
    fun `HEDGE block persists through the player turn`() {
        start(IntentType.HEDGE, startingDebt = 30)
        resolveIntent()
        assertEquals(5, engine.getState().enemies[0].block)
        // The player now attacks THROUGH it. The fixture baseline is hp = 60 (see [start]);
        // the removed "55 - 1" implied a baseline of 55 that the fixture never had. The strike
        // is not 6 damage either: the flat leverage bonus adds floor(debt / 6) and interest
        // compounds twice before the strike resolves (30 -> 35 on startCombat, 35 -> 41 on the
        // next beginTurn), so the strike deals 6 + floor(41 / 6) = 12; 5 block absorbs 5 of it,
        // net 7 against baseline 60 -> 53.
        val strike = engine.getState().hand.first { it.cardId == "strike" }
        engine.playCard(strike.id, engine.getState().enemies[0].id)
        assertEquals(53, engine.getState().enemies[0].hp, "12 damage - 5 block")
    }
}