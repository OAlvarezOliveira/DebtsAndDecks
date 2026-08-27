package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.cards.CardInstance
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.TargetType
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RunManagerTest {

    private val rng = Random(7)
    private lateinit var cardRegistry: CardRegistry
    private lateinit var combatEngine: CombatEngine
    private lateinit var runManager: RunManager

    // Low HP so a single starter attack card always kills, keeping the tests deterministic
    // regardless of hand-shuffle order.
    private val enemies = listOf(
        EnemyDefinition(
            id = "thug", name = "Thug", hp = 5,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 6)),
            rewards = EnemyRewards(gold = 10, cardChoices = 3)
        ),
        EnemyDefinition(
            id = "loan_shark", name = "Loan Shark", hp = 5,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 8)),
            rewards = EnemyRewards(gold = 15, cardChoices = 4)
        ),
        EnemyDefinition(
            id = "collector", name = "Collector", hp = 5,
            intentPattern = listOf(IntentStep(IntentType.ATTACK, 10)),
            rewards = EnemyRewards(gold = 25, cardChoices = 5)
        )
    )

    /** C5: 8-slot sequence over the fixture roster. Slot rewards are deliberately distinct from the
     *  enemies' built-in cardChoices (3/4/5) to prove slot authority (R2.4/R4.2). */
    private val sequence = runSequence()
    private fun runSequence(): com.debtsdecks.core.model.RunSequence {
        fun slot(id: String, gold: Int, picks: Int) = com.debtsdecks.core.model.EncounterSlot(
            enemyId = id, rewards = com.debtsdecks.core.enemies.EnemyRewards(gold = gold, cardChoices = picks)
        )
        return com.debtsdecks.core.model.RunSequence(
            slots = listOf(
                slot("thug", 10, 1),
                slot("thug", 10, 1),
                slot("loan_shark", 15, 1),
                slot("thug", 12, 1),
                slot("loan_shark", 18, 2),
                slot("loan_shark", 20, 1),
                slot("collector", 25, 1),
                slot("collector", 30, 0)
            )
        )
    }

/**
     * Starter cards, with "survive"'s cost parametrized. Debt-focused tests inflate it far past
     * `maxEnergy` so a single, deterministic play (no shuffle-order dependence) converts a known
     * shortfall into Debt via a SELF-targeting card that never touches enemy HP.
     */
    private fun makeStarterCards(surviveCost: Int = 1): List<CardDefinition> = listOf(
        CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1,
            damage = 6, targetType = TargetType.ENEMY, description = "Deal 6 damage",
            rarity = Rarity.BASIC, tags = setOf("starter")
        ),
        CardDefinition(
            id = "defend", name = "Defend", type = CardType.SKILL, cost = 1,
            block = 5, targetType = TargetType.SELF, description = "Gain 5 Block",
            rarity = Rarity.BASIC, tags = setOf("starter")
        ),
        CardDefinition(
            id = "bash", name = "Bash", type = CardType.ATTACK, cost = 2,
            damage = 8, vulnerableApply = 1, targetType = TargetType.ENEMY,
            description = "Deal 8 damage. Apply 1 Vulnerable.", rarity = Rarity.BASIC,
            tags = setOf("starter")
        ),
        CardDefinition(
            id = "survive", name = "Survive", type = CardType.SKILL, cost = surviveCost,
            block = 8, targetType = TargetType.SELF, description = "Gain 8 Block",
            rarity = Rarity.BASIC, tags = setOf("starter")
        )
    )

    private val rewardCards = listOf(
        CardDefinition(
            id = "reward_a", name = "Reward A", type = CardType.ATTACK, cost = 1,
            damage = 10, targetType = TargetType.ENEMY, description = "", rarity = Rarity.COMMON
        ),
        CardDefinition(
            id = "reward_b", name = "Reward B", type = CardType.SKILL, cost = 1,
            block = 10, targetType = TargetType.SELF, description = "", rarity = Rarity.COMMON
        ),
        CardDefinition(
            id = "reward_c", name = "Reward C", type = CardType.ATTACK, cost = 1,
            damage = 12, targetType = TargetType.ENEMY, description = "", rarity = Rarity.COMMON
        ),
        CardDefinition(
            id = "reward_d", name = "Reward D", type = CardType.ATTACK, cost = 2,
            damage = 14, targetType = TargetType.ENEMY, description = "", rarity = Rarity.UNCOMMON
        )
    )

    @BeforeEach
    fun setup() {
        cardRegistry = CardRegistry.create(makeStarterCards() + rewardCards)
        combatEngine = CombatEngine(cardRegistry, testLocalizer(), rng)
        runManager = RunManager(combatEngine, cardRegistry, enemies, sequence, rng)
    }

    private fun upgradeTurnToRefresh() {
        combatEngine.endPlayerTurn()
        runManager.refresh()
    }

    private fun killCurrentEnemy() {
        var guard = 0
        while (true) {
            guard++
            check(guard < 50) { "killCurrentEnemy exceeded safety guard" }

            val state = combatEngine.getState()
            if (state.currentTurn == TurnPhase.COMBAT_END) return

            val enemy = state.enemies.firstOrNull { it.hp > 0 } ?: return
            val attackCard = state.hand.firstOrNull {
                it.type == CardType.ATTACK && it.targetType == TargetType.ENEMY && it.isPlayable()
            }

            if (attackCard != null) {
                combatEngine.playCard(attackCard.id, enemy.id)
            } else {
                combatEngine.endPlayerTurn()
            }
            runManager.refresh()
        }
    }

    @Test
    fun `defeating the first enemy opens a reward screen with the slot's choice count`() {
        killCurrentEnemy()

        assertEquals(RunManager.Phase.NODE, runManager.phase)
        // Slot 0 rewards say 1 pick; the defeated thug's built-in cardChoices is 3. Slot wins.
        assertEquals(1, runManager.rewardChoices.size)
        assertTrue(runManager.rewardChoices.none { "starter" in it.tags })
    }

    @Test
    fun `defeating the first enemy proves slot rewards beat enemy built-in cardChoices`() {
        killCurrentEnemy()
        assertEquals(1, runManager.rewardChoices.size)
    }

    @Test
    fun `choosing a reward advances to the next encounter with a bigger deck`() {
        killCurrentEnemy()
        val chosen = runManager.rewardChoices.first()

        runManager.chooseReward(chosen)

        assertEquals(RunManager.Phase.COMBAT, runManager.phase)
        assertEquals("Thug", combatEngine.getState().enemies[0].name) // slot 1 is thug
        assertTrue(combatEngine.getState().hand.isNotEmpty())
    }

    @Test
    fun `victory fires only after the final slot of the sequence`() {
        // Walk all 8 slots; victory must NOT fire before slot 8.
        repeat(7) { slotNumber ->
            killCurrentEnemy() // defeats the slot's enemy (fixture are all killable)
            assertEquals(RunManager.Phase.NODE, runManager.phase, "slot $slotNumber should reward, not win")
            runManager.chooseReward(runManager.rewardChoices.first())
        }
        killCurrentEnemy() // slot 8: final collector
        assertEquals(RunManager.Phase.VICTORY, runManager.phase)
    }

    @Test
    fun `player death triggers defeat`() {
        // Never attack back - end turn every time until the player dies to the Thug's repeated hits.
        var guard = 0
        while (runManager.phase == RunManager.Phase.COMBAT) {
            guard++
            check(guard < 50) { "combat did not end in time" }
            combatEngine.endPlayerTurn()
            runManager.refresh()
        }

        assertEquals(RunManager.Phase.DEFEAT, runManager.phase)
    }

    @Test
    fun `restarting the run resets to the first enemy with the starter deck`() {
        killCurrentEnemy()
        runManager.chooseReward(runManager.rewardChoices.first())

        runManager.restartRun()

        assertEquals(RunManager.Phase.COMBAT, runManager.phase)
        assertEquals("Thug", combatEngine.getState().enemies[0].name)
    }

    // --- Phase 2: RunManager Debt/Gold persistence, garnishment, and the break encounter ---

    /**
     * Plays [cardId] (a SELF-targeting card assumed to be somewhere in the 10-card starter deck)
     * as soon as it is drawn into hand, ending turns until then. The starter deck's exact 5/5
     * split across the first two turns guarantees this resolves within 2 turns.
     */
    private fun playSelfCardWhenDrawn(engine: CombatEngine, runManager: RunManager, cardId: String) {
        var guard = 0
        while (true) {
            guard++
            check(guard < 20) { "playSelfCardWhenDrawn exceeded safety guard waiting for $cardId" }
            val instance = engine.getState().hand.firstOrNull { it.cardId == cardId }
            if (instance != null) {
                engine.playCard(instance.id, null)
                runManager.refresh()
                return
            }
            engine.endPlayerTurn()
            runManager.refresh()
        }
    }

    /** Repeatedly plays the first available enemy-targeting attack card until the sole enemy dies. */
    private fun finishSoleEnemy(engine: CombatEngine, runManager: RunManager) {
        var guard = 0
        while (true) {
            guard++
            check(guard < 50) { "finishSoleEnemy exceeded safety guard" }

            val state = engine.getState()
            if (state.currentTurn == TurnPhase.COMBAT_END) {
                runManager.refresh()
                return
            }

            val enemy = state.enemies.firstOrNull { it.hp > 0 }
            val attackCard = state.hand.firstOrNull {
                it.type == CardType.ATTACK && it.targetType == TargetType.ENEMY
            }

            if (enemy != null && attackCard != null) {
                engine.playCard(attackCard.id, enemy.id)
            } else {
                engine.endPlayerTurn()
            }
            runManager.refresh()
        }
    }

    @Test
    fun `debt and gold carry across encounters, with an interest tick applied at the next encounter start`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 6) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, runSequence(), rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)

        assertEquals(RunManager.Phase.NODE, rm.phase)
        val debtCarried = rm.debt
        val goldCarried = rm.gold
        assertTrue(debtCarried > 0)

        rm.chooseReward(rm.rewardChoices.first())

        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals("Thug", engine.getState().enemies[0].name) // slot 1 is thug
        val expectedDebtAfterTick = DebtConfig.applyInterest(debtCarried)
        assertEquals(expectedDebtAfterTick, engine.getState().debt)
        assertEquals(goldCarried, engine.getState().gold)

        // RunManager's own fields lag until the next refresh() call syncs them.
        rm.refresh()
        assertEquals(expectedDebtAfterTick, rm.debt)
        assertEquals(goldCarried, rm.gold)
    }

    @Test
    fun `garnishment splits a gold reward at encounter end, reducing debt and crediting net gold`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 20) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, runSequence(), rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)

        assertEquals(RunManager.Phase.NODE, rm.phase)
        val debtBeforeGarnish = engine.getState().debt
        val rawGold = enemies[0].rewards.gold
        val expectedGarnish = DebtConfig.garnishAmount(rawGold, debtBeforeGarnish)

        assertTrue(debtBeforeGarnish > 0)
        assertTrue(expectedGarnish > 0)
        assertEquals(debtBeforeGarnish - expectedGarnish, rm.debt)
        assertEquals(rawGold - expectedGarnish, rm.gold)
    }

    @Test
    fun `crossing the break threshold forces the collector encounter next, without desyncing encounterIndex`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 33) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, runSequence(), rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        assertTrue(rm.debt >= DebtConfig.BREAK_THRESHOLD)
        assertTrue(rm.pendingBreakEncounter)

        finishSoleEnemy(engine, rm) // defeat the Thug (encounterIndex still 0)
        assertEquals(RunManager.Phase.NODE, rm.phase)

        rm.chooseReward(rm.rewardChoices.first())

        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals("Collector", engine.getState().enemies[0].name)
        assertFalse(rm.pendingBreakEncounter)

        // encounterIndex must NOT have advanced: defeating this forced Collector and choosing a
        // reward again must resume the *normal* sequence at enemies[1] ("Loan Shark"), proving
        // the forced detour never desynced encounterIndex.
        finishSoleEnemy(engine, rm)
        assertEquals(RunManager.Phase.NODE, rm.phase)

        rm.chooseReward(rm.rewardChoices.first())
        assertEquals("Thug", engine.getState().enemies[0].name) // slot 1 is thug
    }

    @Test
    fun `the break encounter does not re-trigger after it has already fired once this run`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 33) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, runSequence(), rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)
        assertTrue(rm.pendingBreakEncounter)

        rm.chooseReward(rm.rewardChoices.first()) // consumes the flag, forces the Collector
        assertFalse(rm.pendingBreakEncounter)
        assertEquals("Collector", engine.getState().enemies[0].name)

        // Winning the Thug garnished Debt (garnishAmount on the gold reward), so carried Debt may
        // now sit below BREAK_THRESHOLD — intended. What must hold: refresh() does NOT re-arm the
        // pendingBreakEncounter flag once the break already fired this run.
        assertFalse(rm.pendingBreakEncounter)

        finishSoleEnemy(engine, rm)
        assertEquals(RunManager.Phase.NODE, rm.phase)
        assertFalse(rm.pendingBreakEncounter)

        rm.chooseReward(rm.rewardChoices.first())
        assertEquals("Thug", engine.getState().enemies[0].name) // normal progression resumes at slot 1, not a 2nd forced Collector
    }

    // --- Player HP persistence across combats (combat-progression-and-i18n, Phase 1) ---

    @Test
    fun `hp mirrors the combat engine's player state after refresh, reflecting damage taken`() {
        combatEngine.endPlayerTurn() // no card played: the Thug's attack lands in full
        runManager.refresh()

        val expectedHp = combatEngine.getState().player.hp
        assertTrue(expectedHp < 50)
        assertEquals(expectedHp, runManager.hp)
    }

    @Test
    fun `hp carries over exactly into the next encounter when a reward is chosen`() {
        combatEngine.endPlayerTurn() // take one hit before finishing off the Thug
        runManager.refresh()
        killCurrentEnemy()

        assertEquals(RunManager.Phase.NODE, runManager.phase)
        val hpAtNode = runManager.hp
        assertTrue(hpAtNode > 0) // node heals +8 flat, may cap at 50

        runManager.chooseReward(runManager.rewardChoices.first())
        runManager.refresh()

        assertEquals(hpAtNode, combatEngine.getState().player.hp)
        assertEquals(hpAtNode, runManager.hp)
    }

    @Test
    fun `restarting the run resets hp back to full`() {
        combatEngine.endPlayerTurn()
        runManager.refresh()
        assertTrue(runManager.hp < 50)

        runManager.restartRun()

        assertEquals(50, runManager.hp)
        assertEquals(50, combatEngine.getState().player.hp)
    }

    // --- Enemy tiers: reward count wired to the defeated enemy's cardChoices (combat-progression-and-i18n, Phase 3) ---

    @Test
    fun `reward choice count matches the slot's cardChoices, walking the sequence`() {
        // Slots 0-3 all give 1 pick (thug/thug/loan_shark/thug).
        repeat(4) { i ->
            killCurrentEnemy()
            assertEquals(RunManager.Phase.NODE, runManager.phase)
            assertEquals(1, runManager.rewardChoices.size, "slot $i should give 1 pick")
            runManager.chooseReward(runManager.rewardChoices.first())
        }
        // Slot 4 = loan_shark with 2 picks (the only double-pick slot).
        killCurrentEnemy()
        assertEquals(2, runManager.rewardChoices.size)
    }

    @Test
    fun `restarting the run resets debt, gold, and the pending break flag`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 33) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, runSequence(), rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        assertTrue(rm.debt >= DebtConfig.BREAK_THRESHOLD)
        assertTrue(rm.pendingBreakEncounter)

        rm.restartRun()

        assertEquals(DebtConfig.STARTING_DEBT, rm.debt) // design D: every run starts in debt
        assertEquals(0, rm.gold)
        assertFalse(rm.pendingBreakEncounter)
        assertTrue(engine.getState().debt >= DebtConfig.STARTING_DEBT, "the rerun starts in debt (interest may add one tick)")
        assertEquals(0, engine.getState().gold)
        assertEquals("Thug", engine.getState().enemies[0].name)
    }

    // --- C7 between-fight-node (T2.1 RED) ---

    @Test
    fun `winning a fight enters NODE phase and heals flat amount capped at max HP`() {
        killCurrentEnemy() // defeats slot 0 (thug); hp stays at max since thug hp=5 dies first
        assertEquals(RunManager.Phase.NODE, runManager.phase)
        assertEquals(50, runManager.hp)
    }

    @Test
    fun `applying node free pick progresses to the next slot`() {
        killCurrentEnemy()
        assertEquals(RunManager.Phase.NODE, runManager.phase)
        val freePick = runManager.rewardChoices.first()
        runManager.takeNodeFreePick(freePick)
        assertEquals(RunManager.Phase.COMBAT, runManager.phase)
        assertEquals("Thug", combatEngine.getState().enemies[0].name) // slot 1 = thug again
    }

    @Test
    fun `buying at the node charges escalated gold and adds a card`() {
        val rm = RunManager(combatEngine, cardRegistry, enemies, sequence, rng)
        playSelfCardWhenDrawn(combatEngine, rm, "survive")
        finishSoleEnemy(combatEngine, rm)
        assertEquals(RunManager.Phase.NODE, rm.phase)

        val goldBefore = rm.gold
        val offer = rm.nodeShopChoices.first()
        val buyCost = NodeConfig.escalatedCost(NodeConfig.BUY_BASE, 1)
        assertTrue(rm.buyCard(offer))
        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals(goldBefore - buyCost, rm.gold)
    }

    @Test
    fun `removing a card at the node charges escalated gold and removes it from the deck`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold (net of garnish) funds a node-2 remove
        assertEquals(RunManager.Phase.NODE, runManager.phase)

        val goldBefore = runManager.gold
        val removal = runManager.nodeRemoveChoices.first()
        val removeCost = NodeConfig.escalatedCost(NodeConfig.REMOVE_BASE, runManager.nodeIndex)
        assertTrue(runManager.removeCardFromDeck(removal))
        assertEquals(RunManager.Phase.COMBAT, runManager.phase)
        assertEquals(goldBefore - removeCost, runManager.gold)
    }fun `taking a loan at the node gains gold and adds debt`() {
        val rm = RunManager(combatEngine, cardRegistry, enemies, sequence, rng)
        playSelfCardWhenDrawn(combatEngine, rm, "survive")
        finishSoleEnemy(combatEngine, rm)
        assertEquals(RunManager.Phase.NODE, rm.phase)

        val goldBefore = rm.gold
        val debtBefore = rm.debt
        assertTrue(rm.takeLoan())
        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals(goldBefore + NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, 1), rm.gold)
        assertEquals(debtBefore + NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, 1), rm.debt)
    }

    @Test
    fun `repay via node clears debt for gold plus escalating fee when affordable`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // two fights: gold (net of garnish) covers debt + fee
        assertTrue(runManager.debt > 0) // design D starts in debt and interest ticks per turn
        assertEquals(RunManager.Phase.NODE, runManager.phase)

        val goldBefore = runManager.gold
        val debtBefore = runManager.debt
        val fee = NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, runManager.nodeIndex)
        assertTrue(goldBefore >= debtBefore + fee, "two-fight gold must afford the repay")
        assertTrue(runManager.repayViaNode())
        assertEquals(0, runManager.debt)
        assertEquals(goldBefore - (debtBefore + fee), runManager.gold)
    }fun `upgrade card costs flat gold and marks the id`() {
        killCurrentEnemy() // slot 0 win -> NODE, gold 10
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // slot 1 win -> NODE, gold 20

        val goldBefore = runManager.gold // slot rewards net of garnishment (design D starts in debt)

        assertTrue(runManager.upgradeCard("strike"))

        assertEquals(goldBefore - NodeConfig.UPGRADE_BASE, runManager.gold)
        assertEquals(1, runManager.upgradesRemaining)
        assertEquals(RunManager.Phase.COMBAT, runManager.phase) // one purchase ends the node
    }

    @Test
    fun `upgraded card is never re-offered in a later node`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        runManager.upgradeCard("survive") // single copy: upgrading it exhausts its copies
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // next NODE

        assertFalse(runManager.resolveNodeUpgradeCards().any { it.id == "survive" },
            "a card whose only copy is upgraded must not be re-offered (R2, decision A)")
        assertTrue(runManager.upgradeEligible("strike"), "strike still has 4 un-upgraded copies")
    }

    @Test
    fun `upgrade card fails when gold is insufficient and does not advance`() {
        killCurrentEnemy() // gold 10 < 15

        assertFalse(runManager.upgradeCard("strike"))

        assertEquals(RunManager.Phase.NODE, runManager.phase)
        assertTrue(runManager.gold < NodeConfig.UPGRADE_BASE, "single fight gold (net of garnish) stays under the flat 15")
    }

    @Test
    fun `upgrade card fails for an unknown card`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        assertFalse(runManager.upgradeCard("does_not_exist"))
    }

    @Test
    fun `upgrading the same card twice is blocked`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        assertTrue(runManager.upgradeCard("strike"))
        assertFalse(runManager.upgradeCard("strike")) // already upgraded (R2/S4)
    }

    @Test
    fun `upgrade cap is two per run`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 35 (10+10+15)

        assertTrue(runManager.upgradeCard("strike"))
        assertTrue(runManager.upgradeCard("defend"))
        assertEquals(0, runManager.upgradesRemaining)
        assertFalse(runManager.upgradeCard("bash")) // cap reached (R3/S5)
    }

    @Test
    fun `restart resets upgrade state`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        runManager.upgradeCard("strike")
        runManager.restartRun()

        assertEquals(2, runManager.upgradesRemaining)

        // R11: a fresh run can upgrade the same id again (proved functionally: earn gold, then upgrade).
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20 again

        assertTrue(runManager.upgradeCard("strike"))
    }


    // --- card-upgrades regression (playtest P0-A/B/C): the RunManager->CombatEngine handoff ---

    @Test
    fun `upgraded card carries into the next combat with effective values`() {
        // P0-B regression: upgrades must reach NORMAL combats (not only the forced collector).
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        assertTrue(runManager.upgradeCard("strike"))
        assertEquals(RunManager.Phase.COMBAT, runManager.phase) // slot 2 already running

        // decision A: ONE of the five strikes is upgraded; the others stay vanilla.
        var guard = 0
        var upgradedStrike: CardInstance? = null
        while (upgradedStrike == null && guard < 40) {
            guard++
            upgradeTurnToRefresh()
            upgradedStrike = combatEngine.getState().hand.firstOrNull { it.cardId == "strike" && it.upgraded }
        }
        assertTrue(upgradedStrike != null, "an upgraded strike must reach the hand")
        assertEquals(9, upgradedStrike!!.effectiveDamage)
        val strikesInHand = combatEngine.getState().hand.filter { it.cardId == "strike" }
        if (strikesInHand.size >= 2) {
            assertTrue(strikesInHand.any { !it.upgraded }, "the other copies stay vanilla")
        }
    }

    @Test
    fun `cost2 card upgrade reduces its cost in the next combat`() {
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // gold 20

        assertTrue(runManager.upgradeCard("bash"))

        // bash may start in the draw pile; play until it reaches the hand (guard bounded).
        var guard = 0
        var bash = combatEngine.getState().hand.firstOrNull { it.cardId == "bash" }
        while (bash == null && guard < 40) {
            guard++
            combatEngine.endPlayerTurn()
            runManager.refresh()
            bash = combatEngine.getState().hand.firstOrNull { it.cardId == "bash" }
        }
        assertTrue(bash != null, "bash must reach the hand within the bounded draw")
        assertEquals(1, bash!!.cost, "cost-2 upgraded card must cost 1 in the next combat")
        assertTrue(bash!!.upgraded)
    }

    @Test
    fun `upgrade offer order is stable across calls`() {
        // P0-C regression: resolveNodeUpgradeCards must NOT re-shuffle per call.
        killCurrentEnemy()
        runManager.takeNodeFreePick(runManager.rewardChoices.first())
        killCurrentEnemy() // NODE gold 20

        val first = runManager.resolveNodeUpgradeCards().map { it.id }
        val second = runManager.resolveNodeUpgradeCards().map { it.id }
        assertEquals(first, second, "the offered upgrade order must be stable (tapped card == offered card)")
        assertEquals(3, first.size)
    }
}
