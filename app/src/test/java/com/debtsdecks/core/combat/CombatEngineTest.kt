package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.EnemyTier
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

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

        engine = CombatEngine(cardRegistry, testLocalizer(), rng)
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
        assertEquals(0, state.debt)
        assertEquals(0, state.gold)
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

        val enemyId = engine.getState().enemies[0].id
        val initialEnemyHp = engine.getState().enemies[0].hp
        val result = engine.playCard(strikeCard!!.id, enemyId)

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

        val result = engine.playCard(defendCard!!.id, null)

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
        val bash = CardDefinition(
            id = "bash",
            name = "Bash",
            type = CardType.ATTACK,
            cost = 2,
            damage = 8,
            vulnerableApply = 2,
            targetType = TargetType.ENEMY,
            description = "Deal 8 damage. Apply 2 Vulnerable.",
            rarity = Rarity.BASIC
        )
        cardRegistry = CardRegistry.create(cardRegistry.all() + bash)
        engine = CombatEngine(cardRegistry, testLocalizer(), rng)

        val thug = EnemyDefinition(
            id = "thug",
            name = "Thug",
            hp = 100,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 6)),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        )
        val starterDeck = listOf("bash", "strike", "strike", "strike", "strike")
        engine.startCombat(listOf(thug), starterDeck)

        val enemyId = engine.getState().enemies[0].id
        val bashCard = engine.getState().hand.find { it.cardId == "bash" }!!
        engine.playCard(bashCard.id, enemyId)
        assertEquals(2, engine.getState().enemies[0].vulnerable)

        val hpBeforeStrike = engine.getState().enemies[0].hp
        val strikeCard = engine.getState().hand.find { it.cardId == "strike" }!!
        engine.playCard(strikeCard.id, enemyId)

        // Strike deals 6 base damage; with Vulnerable active that's 6 * 1.5 = 9
        assertEquals(hpBeforeStrike - 9, engine.getState().enemies[0].hp)
    }

    // --- Debt / Gold / Credit economy (debt-resource-mechanic, Phase 1) ---

    private fun standardThug() = EnemyDefinition(
        id = "thug",
        name = "Thug",
        hp = 100,
        intentPattern = listOf(IntentStep(IntentType.ATTACK, 6)),
        rewards = EnemyRewards(gold = 10, cardChoices = 3)
    )

    private fun registerPriceyCard(cost: Int = 5): CardDefinition {
        val pricey = CardDefinition(
            id = "pricey", name = "Pricey", type = CardType.ATTACK, cost = cost,
            damage = 10, targetType = TargetType.ENEMY, description = "Deal 10 damage.",
            rarity = Rarity.BASIC
        )
        cardRegistry = CardRegistry.create(cardRegistry.all() + pricey)
        engine = CombatEngine(cardRegistry, testLocalizer(), rng)
        return pricey
    }

    private fun registerEscrowShieldCard(): CardDefinition {
        val escrowShield = CardDefinition(
            id = "escrow_shield_test", name = "Escrow Shield", type = CardType.SKILL, cost = 0,
            targetType = TargetType.SELF, description = "Activate Escrow Shield.",
            rarity = Rarity.UNCOMMON, tags = setOf("escrow_shield_activate")
        )
        cardRegistry = CardRegistry.create(cardRegistry.all() + escrowShield)
        engine = CombatEngine(cardRegistry, testLocalizer(), rng)
        return escrowShield
    }

    @Test
    fun `playing a card beyond available Credit converts the shortfall into Debt and still fully resolves`() {
        registerPriceyCard(cost = 5)
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("pricey", "strike", "strike", "strike", "strike"))

        val enemyId = engine.getState().enemies[0].id
        val hpBefore = engine.getState().enemies[0].hp
        val pricey = engine.getState().hand.find { it.cardId == "pricey" }!!

        val result = engine.playCard(pricey.id, enemyId)

        assertTrue(result.success)
        assertEquals(0, engine.getState().energy) // all 3 Credit spent, none left over
        assertEquals(2, engine.getState().debt) // cost 5 - credit 3 = 2 shortfall
        assertEquals(hpBefore - 10, engine.getState().enemies[0].hp) // full effect, not partial
    }

    @Test
    fun `escrow shield halves the Debt added from a shortfall, rounding up`() {
        registerPriceyCard(cost = 5)
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("pricey", "strike", "strike", "strike", "strike"))
        engine.activateEscrowShield()

        val enemyId = engine.getState().enemies[0].id
        val pricey = engine.getState().hand.find { it.cardId == "pricey" }!!
        engine.playCard(pricey.id, enemyId)

        // Raw shortfall is 5 - 3 = 2; shielded is ceil(2 * 0.5) = 1.
        assertEquals(1, engine.getState().debt)
    }

    @Test
    fun `interest ticks at the first turn start when carrying Debt forward`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 0, startingDebt = 100)

        // 100 + ceil(100 * 15%) = 115, applied at the first turn start.
        assertEquals(115, engine.getState().debt)
    }

    @Test
    fun `interest growth is capped at the debt ceiling`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 0, startingDebt = 195)

        assertEquals(200, engine.getState().debt)
    }

    @Test
    fun `zero starting debt produces no interest growth`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"))

        assertEquals(0, engine.getState().debt)
    }

    @Test
    fun `repayDebt with GOLD spends the minimum of available gold and debt`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 5, startingDebt = 8)
        assertEquals(10, engine.getState().debt) // 8 + ceil(8 * 15%) = 10, turn-start interest

        val result = engine.repayDebt(CombatEngine.RepayMode.GOLD)

        assertTrue(result.success)
        assertEquals(5, engine.getState().debt) // 10 - min(5, 10) = 5
        assertEquals(0, engine.getState().gold) // 5 - 5 = 0
    }

    @Test
    fun `repayDebt with GOLD is capped by current debt, not by gold on hand`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 20, startingDebt = 3)
        assertEquals(4, engine.getState().debt) // 3 + ceil(3 * 10%) = 4

        val result = engine.repayDebt(CombatEngine.RepayMode.GOLD)

        assertTrue(result.success)
        assertEquals(0, engine.getState().debt)
        assertEquals(16, engine.getState().gold) // 20 - 4
    }

    @Test
    fun `repayDebt with GOLD fails when there is no debt to repay`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 10, startingDebt = 0)

        val result = engine.repayDebt(CombatEngine.RepayMode.GOLD)

        assertEquals(false, result.success)
        assertEquals(10, engine.getState().gold) // untouched
    }

    @Test
    fun `repayDebt with DISCARD removes the chosen card and cuts a flat amount of debt`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 0, startingDebt = 3)
        assertEquals(4, engine.getState().debt) // 3 + ceil(3 * 10%) = 4

        val handSizeBefore = engine.getState().hand.size
        val card = engine.getState().hand.first()

        val result = engine.repayDebt(CombatEngine.RepayMode.DISCARD, card.id)

        assertTrue(result.success)
        assertEquals(handSizeBefore - 1, engine.getState().hand.size)
        // Flat REPAY_DISCARD_VALUE=5 clamped by current debt of 4 -> debt goes to 0, not negative.
        assertEquals(0, engine.getState().debt)
    }

    @Test
    fun `repayDebt with DISCARD fails when the given card is not in hand`() {
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("strike", "strike", "defend", "defend", "strike"), startingGold = 0, startingDebt = 3)

        val result = engine.repayDebt(CombatEngine.RepayMode.DISCARD, "not-a-real-instance-id")

        assertEquals(false, result.success)
        assertEquals(4, engine.getState().debt) // untouched
    }

    // --- Debt / Gold card-effect wiring (debt-resource-mechanic, Phase 4 follow-up) ---

    // --- Player HP persistence across combats (combat-progression-and-i18n, Phase 1) ---

    @Test
    fun `starting combat with a carried-over HP value uses it as the player's starting HP`() {
        val thug = standardThug()
        engine.startCombat(
            listOf(thug),
            listOf("strike", "strike", "defend", "defend", "strike"),
            startingHp = 33
        )

        assertEquals(33, engine.getState().player.hp)
    }

    @Test
    fun `playing a card tagged escrow_shield_activate wires CardResolver's effect through to activateEscrowShield`() {
        registerEscrowShieldCard()
        registerPriceyCard(cost = 5)
        val thug = standardThug()
        engine.startCombat(listOf(thug), listOf("escrow_shield_test", "pricey", "strike", "strike", "strike"))

        // escrowShieldActive isn't exposed on CombatState, so the call-through is proven the same
        // way the direct activateEscrowShield() test above proves the shield's effect: by observing
        // that a subsequent shortfall is halved (rounded up), instead of applied in full.
        val shieldCard = engine.getState().hand.find { it.cardId == "escrow_shield_test" }!!
        engine.playCard(shieldCard.id, null)

        val enemyId = engine.getState().enemies[0].id
        val pricey = engine.getState().hand.find { it.cardId == "pricey" }!!
        engine.playCard(pricey.id, enemyId)

        // Raw shortfall is 5 - 3 = 2; shielded is ceil(2 * 0.5) = 1, proving
        // CardResolver.Effect.EscrowShieldActivate reached CombatEngine.applyEffects()
        // and called activateEscrowShield(), not just that the direct API call works.
        assertEquals(1, engine.getState().debt)
    }

    // --- Debt-Economy (debt-economy-cards-and-boss-interest, PR1): new effect primitives ---

    private fun registerCard(def: CardDefinition): CardDefinition {
        cardRegistry = CardRegistry.create(cardRegistry.all() + def)
        engine = CombatEngine(cardRegistry, testLocalizer(), rng)
        return def
    }

    @Test
    fun `playing an add-debt card raises player Debt`() {
        registerCard(CardDefinition(
            id = "add_debt_card", name = "Add Debt", type = CardType.SKILL, cost = 0,
            debtAdd = 5, targetType = TargetType.SELF, description = "Add 5 Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("add_debt")
        ))
        engine.startCombat(listOf(standardThug()), listOf("add_debt_card", "strike", "strike", "strike", "strike"))

        val card = engine.getState().hand.find { it.cardId == "add_debt_card" }!!
        engine.playCard(card.id, null)

        assertEquals(5, engine.getState().debt)
    }

    @Test
    fun `add-debt is capped at the debt ceiling`() {
        registerCard(CardDefinition(
            id = "add_debt_card", name = "Add Debt", type = CardType.SKILL, cost = 0,
            debtAdd = 5, targetType = TargetType.SELF, description = "Add 5 Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("add_debt")
        ))
        engine.startCombat(listOf(standardThug()), listOf("add_debt_card", "strike", "strike", "strike", "strike"), startingDebt = 180)

        // 180 + ceil(180 * 15%) = 207, capped to 200 at first turn start.
        assertEquals(200, engine.getState().debt)

        val card = engine.getState().hand.find { it.cardId == "add_debt_card" }!!
        engine.playCard(card.id, null)

        // Already at cap, so the +5 add stays capped at INTEREST_CAP = 200.
        assertEquals(200, engine.getState().debt)
    }

    @Test
    fun `add-debt is NOT halved when the escrow shield is active`() {
        registerCard(CardDefinition(
            id = "add_debt_card", name = "Add Debt", type = CardType.SKILL, cost = 0,
            debtAdd = 5, targetType = TargetType.SELF, description = "Add 5 Debt.",
            rarity = Rarity.UNCOMMON, tags = setOf("add_debt")
        ))
        registerEscrowShieldCard()
        engine.startCombat(
            listOf(standardThug()),
            listOf("escrow_shield_test", "add_debt_card", "strike", "strike", "strike")
        )

        val shield = engine.getState().hand.find { it.cardId == "escrow_shield_test" }!!
        engine.playCard(shield.id, null)
        val card = engine.getState().hand.find { it.cardId == "add_debt_card" }!!
        engine.playCard(card.id, null)

        // Full 5, NOT halved: the escrow shields only Credit-shortfall borrowing.
        assertEquals(5, engine.getState().debt)
    }

    @Test
    fun `playing a gain-credit card grants extra energy`() {
        registerCard(CardDefinition(
            id = "golden_credit", name = "Golden Credit", type = CardType.SKILL, cost = 1,
            creditGain = 4, targetType = TargetType.SELF, description = "Gain 4 Credit.",
            rarity = Rarity.UNCOMMON, tags = setOf("gain_credit")
        ))
        engine.startCombat(listOf(standardThug()), listOf("golden_credit", "strike", "strike", "strike", "strike"))

        val card = engine.getState().hand.find { it.cardId == "golden_credit" }!!
        engine.playCard(card.id, null)

        // 3 - 1 cost = 2, + 4 gain = 6.
        assertEquals(6, engine.getState().energy)
    }

    @Test
    fun `gain-credit is capped at MAX_ENERGY_CAP`() {
        registerCard(CardDefinition(
            id = "golden_credit", name = "Golden Credit", type = CardType.SKILL, cost = 1,
            creditGain = 4, targetType = TargetType.SELF, description = "Gain 4 Credit.",
            rarity = Rarity.UNCOMMON, tags = setOf("gain_credit")
        ))
        engine.startCombat(listOf(standardThug()), listOf("golden_credit", "golden_credit", "strike", "strike", "strike"))

        val first = engine.getState().hand.find { it.cardId == "golden_credit" }!!
        engine.playCard(first.id, null) // 3 - 1 = 2, + 4 = 6
        val second = engine.getState().hand.find { it.cardId == "golden_credit" }!!
        engine.playCard(second.id, null) // 6 - 1 = 5, + 4 = 9 -> capped to 6

        assertEquals(6, engine.getState().energy)
    }

    @Test
    fun `playing an asset-auction card exhausts another hand card and gains gold`() {
        registerCard(CardDefinition(
            id = "asset_auction", name = "Asset Auction", type = CardType.SKILL, cost = 1,
            goldGain = 9, targetType = TargetType.SELF, description = "Exhaust a card; gain 9 Gold.",
            rarity = Rarity.UNCOMMON, tags = setOf("hand_exhaust")
        ))
        engine.startCombat(listOf(standardThug()), listOf("asset_auction", "strike", "strike", "strike", "strike"))

        val card = engine.getState().hand.find { it.cardId == "asset_auction" }!!
        engine.playCard(card.id, null)

        val state = engine.getState()
        assertEquals(9, state.gold)
        // One strike was exhausted as the cost; the played asset_auction went to the discard pile.
        assertEquals(1, state.exhaustPileCount)
    }

    // --- Debt-Economy boss interest (debt-economy-cards-and-boss-interest, PR2): IntentType.LEVY ---

    private fun levyCollector() = EnemyDefinition(
        id = "collector", name = "Collector", hp = 80,
        intentPattern = listOf(IntentStep(IntentType.LEVY, param = 6)),
        rewards = EnemyRewards(gold = 20, cardChoices = 3),
        tier = EnemyTier.BOSS
    )

    @Test
    fun `a LEVY enemy intent raises player Debt and logs it when the turn ends`() {
        engine.startCombat(listOf(levyCollector()), listOf("strike", "strike", "strike", "strike", "strike"))
        val debtBefore = engine.getState().debt

        val result = engine.endPlayerTurn()

        assertTrue(result.success)
        // LEVY adds 6, then the next turn-start interest tick compounds it.
        val after = DebtConfig.applyInterest(debtBefore + 6)
        assertEquals(after, engine.getState().debt)
        assertTrue(
            engine.getState().log.any { it.message == testLocalizer().format("log.intent_levy", 6) },
            "expected a localized LEVY log entry"
        )
    }

    @Test
    fun `LEVY debt is capped at the debt ceiling`() {
        engine.startCombat(
            listOf(levyCollector()),
            listOf("strike", "strike", "strike", "strike", "strike"),
            startingDebt = 180
        )
        // 180 + ceil(180 * 15%) = 207, capped to 200 at first turn start.
        assertEquals(200, engine.getState().debt)

        engine.endPlayerTurn() // LEVY 6 -> 200 + 6 = 206, capped to 200

        assertEquals(200, engine.getState().debt)
    }

    @Test
    fun `a normal enemy without a LEVY intent never adds Debt`() {
        engine.startCombat(listOf(standardThug()), listOf("strike", "strike", "strike", "strike", "strike"))
        val debtBefore = engine.getState().debt

        engine.endPlayerTurn()

        assertEquals(debtBefore, engine.getState().debt)
    }
}