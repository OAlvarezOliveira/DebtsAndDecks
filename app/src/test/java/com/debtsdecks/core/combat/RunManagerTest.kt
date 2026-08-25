package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
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
        runManager = RunManager(combatEngine, cardRegistry, enemies, rng)
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
    fun `defeating the first enemy opens a reward screen with three choices`() {
        killCurrentEnemy()

        assertEquals(RunManager.Phase.REWARD, runManager.phase)
        assertEquals(3, runManager.rewardChoices.size)
        assertTrue(runManager.rewardChoices.none { "starter" in it.tags })
    }

    @Test
    fun `choosing a reward advances to the next encounter with a bigger deck`() {
        killCurrentEnemy()
        val chosen = runManager.rewardChoices.first()

        runManager.chooseReward(chosen)

        assertEquals(RunManager.Phase.COMBAT, runManager.phase)
        assertEquals("Loan Shark", combatEngine.getState().enemies[0].name)
        assertTrue(combatEngine.getState().hand.isNotEmpty())
    }

    @Test
    fun `defeating the last enemy triggers victory`() {
        killCurrentEnemy() // Thug
        runManager.chooseReward(runManager.rewardChoices.first())
        killCurrentEnemy() // Loan Shark
        runManager.chooseReward(runManager.rewardChoices.first())
        killCurrentEnemy() // Collector

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
        val rm = RunManager(engine, registry, enemies, rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)

        assertEquals(RunManager.Phase.REWARD, rm.phase)
        val debtCarried = rm.debt
        val goldCarried = rm.gold
        assertTrue(debtCarried > 0)

        rm.chooseReward(rm.rewardChoices.first())

        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals("Loan Shark", engine.getState().enemies[0].name)
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
        val rm = RunManager(engine, registry, enemies, rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)

        assertEquals(RunManager.Phase.REWARD, rm.phase)
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
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 60) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        assertTrue(rm.debt >= DebtConfig.BREAK_THRESHOLD)
        assertTrue(rm.pendingBreakEncounter)

        finishSoleEnemy(engine, rm) // defeat the Thug (encounterIndex still 0)
        assertEquals(RunManager.Phase.REWARD, rm.phase)

        rm.chooseReward(rm.rewardChoices.first())

        assertEquals(RunManager.Phase.COMBAT, rm.phase)
        assertEquals("Collector", engine.getState().enemies[0].name)
        assertFalse(rm.pendingBreakEncounter)

        // encounterIndex must NOT have advanced: defeating this forced Collector and choosing a
        // reward again must resume the *normal* sequence at enemies[1] ("Loan Shark"), proving
        // the forced detour never desynced encounterIndex.
        finishSoleEnemy(engine, rm)
        assertEquals(RunManager.Phase.REWARD, rm.phase)

        rm.chooseReward(rm.rewardChoices.first())
        assertEquals("Loan Shark", engine.getState().enemies[0].name)
    }

    @Test
    fun `the break encounter does not re-trigger after it has already fired once this run`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 60) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        finishSoleEnemy(engine, rm)
        assertTrue(rm.pendingBreakEncounter)

        rm.chooseReward(rm.rewardChoices.first()) // consumes the flag, forces the Collector
        assertFalse(rm.pendingBreakEncounter)
        assertEquals("Collector", engine.getState().enemies[0].name)

        // Carried + interest-ticked Debt is still >= BREAK_THRESHOLD here; refresh() must NOT
        // re-arm pendingBreakEncounter since the break already fired once this run.
        rm.refresh()
        assertTrue(rm.debt >= DebtConfig.BREAK_THRESHOLD)
        assertFalse(rm.pendingBreakEncounter)

        finishSoleEnemy(engine, rm)
        assertEquals(RunManager.Phase.REWARD, rm.phase)
        assertFalse(rm.pendingBreakEncounter)

        rm.chooseReward(rm.rewardChoices.first())
        assertEquals("Loan Shark", engine.getState().enemies[0].name) // normal progression, not a 2nd forced Collector
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

        assertEquals(RunManager.Phase.REWARD, runManager.phase)
        val hpAtRewardScreen = runManager.hp
        assertTrue(hpAtRewardScreen < 50)

        runManager.chooseReward(runManager.rewardChoices.first())
        runManager.refresh()

        assertEquals(hpAtRewardScreen, combatEngine.getState().player.hp)
        assertEquals(hpAtRewardScreen, runManager.hp)
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
    fun `reward choice count matches the defeated enemy's cardChoices, not a hardcoded three`() {
        killCurrentEnemy() // Thug: cardChoices = 3
        assertEquals(3, runManager.rewardChoices.size)

        runManager.chooseReward(runManager.rewardChoices.first())
        killCurrentEnemy() // Loan Shark: cardChoices = 4

        assertEquals(4, runManager.rewardChoices.size)
    }

    @Test
    fun `restarting the run resets debt, gold, and the pending break flag`() {
        val registry = CardRegistry.create(makeStarterCards(surviveCost = 60) + rewardCards)
        val engine = CombatEngine(registry, testLocalizer(), rng)
        val rm = RunManager(engine, registry, enemies, rng)

        playSelfCardWhenDrawn(engine, rm, "survive")
        assertTrue(rm.debt >= DebtConfig.BREAK_THRESHOLD)
        assertTrue(rm.pendingBreakEncounter)

        rm.restartRun()

        assertEquals(0, rm.debt)
        assertEquals(0, rm.gold)
        assertFalse(rm.pendingBreakEncounter)
        assertEquals(0, engine.getState().debt)
        assertEquals(0, engine.getState().gold)
        assertEquals("Thug", engine.getState().enemies[0].name)
    }
}
