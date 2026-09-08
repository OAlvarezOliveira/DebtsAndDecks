package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.i18n.testLocalizer
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CardType
import com.debtsdecks.core.model.EncounterSlot
import com.debtsdecks.core.model.Rarity
import com.debtsdecks.core.model.RunSequence
import com.debtsdecks.core.model.SlotRole
import com.debtsdecks.core.model.TargetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * WU5 focused tests: biased 3-choose-1 free pick (T5.3/T5.5), upgrade-every-4-wins cadence + cap
 * (T5.1/T5.2), the T5.2 no-upgrade-after-final-boss caveat, and the convergence guarantee.
 */
class RewardEconomyTest {

    private val rng = Random(7)
    private lateinit var cardRegistry: CardRegistry
    private lateinit var combatEngine: CombatEngine
    private lateinit var runManager: RunManager

    // Low-HP enemies so a single starter attack always kills (deterministic, shuffle-order independent).
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

    private fun makeStarterCards(): List<CardDefinition> = listOf(
        CardDefinition(
            id = "strike", name = "Strike", type = CardType.ATTACK, cost = 1,
            damage = 6, targetType = TargetType.ENEMY, description = "", rarity = Rarity.BASIC,
            tags = setOf("starter")
        ),
        CardDefinition(
            id = "defend", name = "Defend", type = CardType.SKILL, cost = 1,
            block = 5, targetType = TargetType.SELF, description = "", rarity = Rarity.BASIC,
            tags = setOf("starter")
        ),
        CardDefinition(
            id = "bash", name = "Bash", type = CardType.ATTACK, cost = 2,
            damage = 8, vulnerableApply = 1, targetType = TargetType.ENEMY, description = "",
            rarity = Rarity.BASIC, tags = setOf("starter")
        ),
        CardDefinition(
            id = "survive", name = "Survive", type = CardType.SKILL, cost = 1,
            block = 8, targetType = TargetType.SELF, description = "", rarity = Rarity.BASIC,
            tags = setOf("starter")
        )
    )

    // LEVERAGE-tagged reward cards (debt_scaling -> LEVERAGE_BIAS, weight 3 under a LEVERAGE deck).
    private fun leveragePool(): List<CardDefinition> = (1..8).map {
        CardDefinition(
            id = "lev_$it", name = "Lev $it", type = CardType.ATTACK, cost = 1,
            damage = 5, targetType = TargetType.ENEMY, description = "", rarity = Rarity.COMMON,
            tags = setOf("debt_scaling")
        )
    }

    // Neutral PRESSURE cards (no economy tag -> weight 2 under a LEVERAGE deck).
    private fun pressurePool(): List<CardDefinition> = (1..2).map {
        CardDefinition(
            id = "pres_$it", name = "Pres $it", type = CardType.SKILL, cost = 1,
            block = 3, targetType = TargetType.SELF, description = "", rarity = Rarity.COMMON,
            tags = setOf("pressure")
        )
    }

    // Cross-archetype LIQUIDITY cards (economy but not leverage -> weight 1 under a LEVERAGE deck).
    private fun liquidityPool(): List<CardDefinition> = (1..2).map {
        CardDefinition(
            id = "liq_$it", name = "Liq $it", type = CardType.SKILL, cost = 1,
            draw = 1, targetType = TargetType.SELF, description = "", rarity = Rarity.COMMON,
            tags = setOf("debt_draw")
        )
    }

    private fun slot(id: String, gold: Int, picks: Int, role: SlotRole = SlotRole.STREET) =
        EncounterSlot(enemyId = id, districtId = "fixture", rewards = EnemyRewards(gold = gold, cardChoices = picks), role = role)

    /** 8-slot sequence matching the real reward economy: non-boss = 3 picks, boss = 1, final = 0. */
    private fun testSequence(): RunSequence = RunSequence(
        slots = listOf(
            slot("thug", 10, 3), slot("thug", 10, 3), slot("loan_shark", 15, 1, SlotRole.BOSS),
            slot("thug", 12, 3), slot("loan_shark", 18, 3), slot("loan_shark", 20, 1, SlotRole.BOSS),
            slot("collector", 25, 3), slot("collector", 30, 0, SlotRole.BOSS)
        )
    )

    /** Long sequence of [n] killable slots (all STREET, 3 picks, generous gold) for cadence walks. */
    private fun longSequence(n: Int): RunSequence = RunSequence(
        slots = (0 until n).map { slot("thug", 30, 3) }
    )

    private fun newRun(sequence: RunSequence = testSequence(), withRewardCards: Boolean = true) {
        val cards = makeStarterCards() + if (withRewardCards) leveragePool() + pressurePool() + liquidityPool() else emptyList()
        cardRegistry = CardRegistry.create(cards)
        combatEngine = CombatEngine(cardRegistry, testLocalizer(), rng)
        runManager = RunManager(combatEngine, cardRegistry, enemies, sequence, rng)
    }

    private fun killCurrentEnemy() {
        var guard = 0
        while (true) {
            guard++
            check(guard < 50) { "killCurrentEnemy exceeded safety guard" }
            val state = combatEngine.getState()
            if (state.currentTurn == com.debtsdecks.core.model.TurnPhase.COMBAT_END) return
            val enemy = state.enemies.firstOrNull { it.hp > 0 } ?: return
            val attackCard = state.hand.firstOrNull {
                it.type == CardType.ATTACK && it.targetType == TargetType.ENEMY && it.isPlayable()
            }
            if (attackCard != null) combatEngine.playCard(attackCard.id, enemy.id)
            else combatEngine.endPlayerTurn()
            runManager.refresh()
        }
    }

    // --- T5.3 / spec "Biased offer": >=0.6 probability of >=2 dominant-archetype cards in the 3-choose-1 ---

    @Test
    fun `biased 3-choose-1 offer skews to the dominant archetype`() {
        newRun(withRewardCards = true)
        killCurrentEnemy() // win 1 -> NODE (deck all-starter -> PRESSURE archetype)
        runManager.takeNodeFreePick(leveragePool().first()) // inject one LEVERAGE card -> archetype LEVERAGE
        // The deck is now LEVERAGE; sample 200 biased 3-card offers and count >=2 LEVERAGE cards.
        var hits = 0
        repeat(200) {
            val offer = runManager.archetypeBiasedOffer(3)
            val levCount = offer.count { "debt_scaling" in it.tags }
            if (levCount >= 2) hits++
        }
        val fraction = hits / 200.0
        assertTrue(fraction >= 0.6, "biased offer should contain >=2 LEVERAGE cards with P>=0.6; observed $fraction")
    }

    // --- spec "No starters in offer" ---

    @Test
    fun `free pick never contains a starter-tagged card`() {
        newRun()
        // Walk the entire 8-slot run; every node's reward offer must exclude starter-tagged cards.
        for (win in 1..7) {
            killCurrentEnemy() // win -> NODE
            assertTrue(runManager.rewardChoices.isNotEmpty(), "node $win should offer a free pick")
            assertTrue(
                runManager.rewardChoices.none { "starter" in it.tags },
                "node $win free pick must exclude starter cards"
            )
            if (win < 8) runManager.takeNodeFreePick(runManager.rewardChoices.first())
        }
        killCurrentEnemy() // final boss -> VICTORY
        assertEquals(RunManager.Phase.VICTORY, runManager.phase)
    }

    // --- T5.2 caveat: upgrades only at win-4 node, never after the final boss ---

    @Test
    fun `upgrades offered only at the win-4 cadence node, never after the final boss`() {
        newRun()
        for (win in 1..7) {
            killCurrentEnemy() // win -> NODE (win 1..7)
            if (win == 4) {
                assertTrue(
                    runManager.nodeUpgradeChoices.isNotEmpty(),
                    "the node after win 4 must offer upgrades (if eligible cards exist)"
                )
            } else {
                assertTrue(
                    runManager.nodeUpgradeChoices.isEmpty(),
                    "non-cadence node (win $win) must NOT offer upgrades (false-positive trap)"
                )
            }
            if (win < 8) runManager.takeNodeFreePick(runManager.rewardChoices.first())
        }
        // Win 8 = final boss -> VICTORY directly, no node, so no upgrade can appear after the boss.
        killCurrentEnemy()
        assertEquals(RunManager.Phase.VICTORY, runManager.phase)
        assertTrue(runManager.nodeUpgradeChoices.isEmpty(), "no upgrade offer must exist after the final boss")
    }

    // --- T5.1 + T5.2: cap is 4, reached only on cadence nodes ---

    @Test
    fun `upgrade cadence is every 4 wins and the cap is four per run`() {
        newRun(longSequence(21)) // 21 slots so win-20 is a non-final cadence node
        // Walk 20 wins; upgrade at each cadence node (win 4, 8, 12, 16); the 5th attempt (win 20) is capped.
        for (win in 1..20) {
            killCurrentEnemy()
            if (win % 4 == 0) {
                if (win < 20) {
                    assertTrue(runManager.upgradeCard("strike"), "upgrade should succeed at win $win")
                } else {
                    assertFalse(runManager.upgradeCard("strike"), "the 5th upgrade must be rejected by the cap")
                }
            } else {
                runManager.takeNodeFreePick(runManager.rewardChoices.first())
            }
        }
        assertEquals(0, runManager.upgradesRemaining, "exactly 4 upgrades consumed out of the cap of 4")
    }

    // --- Convergence guarantee: biased picks skew the deck toward the dominant archetype ---

    @Test
    fun `biased free picks converge the deck toward the dominant archetype`() {
        newRun(withRewardCards = true)
        // Seed one LEVERAGE card so the dominant archetype is LEVERAGE.
        killCurrentEnemy()
        runManager.takeNodeFreePick(leveragePool().first())

        // Greedy policy: at each node, pick the offer card that best matches the dominant archetype.
        repeat(8) {
            if (runManager.phase != RunManager.Phase.NODE) killCurrentEnemy()
            if (runManager.phase != RunManager.Phase.NODE) return@repeat // VICTORY reached
            val chosen = runManager.rewardChoices.maxByOrNull { card ->
                when {
                    "debt_scaling" in card.tags -> 2
                    "debt_draw" in card.tags -> 0
                    else -> 1
                }
            } ?: runManager.rewardChoices.first()
            runManager.takeNodeFreePick(chosen)
        }

        val dominant = playerArchetype(runManager.deckList, cardRegistry)
        assertEquals(Archetype.LEVERAGE, dominant, "dominant archetype should be LEVERAGE after greedy leverage picks")

        val addedCards = runManager.deckList.size - 10 // minus the 10 starter cards
        val leverageAdded = runManager.deckList.count { id ->
            cardRegistry.get(id)?.tags?.any { it == "debt_scaling" } ?: false
        } - 1 // the single seeded leverage card is also counted; we want majority of the ADDED pool
        // Majority of the added (non-starter) cards must be LEVERAGE-tagged -> deck skews to dominant archetype.
        assertTrue(
            leverageAdded * 2 > addedCards,
            "biased picks should skew the added pool to LEVERAGE (majority); levAdded=$leverageAdded of $addedCards"
        )
    }
}
