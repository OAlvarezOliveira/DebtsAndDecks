package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.PlayerState
import com.debtsdecks.core.model.TurnPhase
import kotlin.random.Random

/**
 * Owns run-level progression on top of [CombatEngine], which only knows about a single
 * encounter. Reuses the same engine instance across encounters via repeated [CombatEngine.startCombat]
 * calls instead of ever replacing it. The run follows [runSequence] (8 slots), NOT the roster
 * order; each slot's enemyId resolves into [enemyDefinitions] (the catalog) and that slot's
 * rewards are run-authoritative (enemy built-in rewards are inert for run logic).
 *
 * C7 between-fight-node: after winning fights 1-7 the run enters [Phase.NODE] — one decision screen
 * between the free card pick (the old reward) and the Gold-spend actions (repay / buy / remove /
 * loan, all escalating with [NodeConfig]). Entering a node heals a flat amount. There is NO node
 * after the final boss (slot 8 → [Phase.VICTORY] directly).
 */
class RunManager(
    private val combatEngine: CombatEngine,
    private val cardRegistry: CardRegistry,
    private val enemyDefinitions: List<EnemyDefinition>,
    private val runSequence: com.debtsdecks.core.model.RunSequence,
    private val rng: Random
) {
    enum class Phase { COMBAT, NODE, VICTORY, DEFEAT }

    var phase: Phase = Phase.COMBAT
        private set

    /** Free card pick offer shown at the node (the old reward screen, phase-localized to NODE). */
    var rewardChoices: List<CardDefinition> = emptyList()
        private set

    /** Archetype-biased 3-card shop offer shown at the node. */
    var nodeShopChoices: List<CardDefinition> = emptyList()
        private set

    /** Random 3-card ids from the current deck offered for removal at the node. */
    var nodeRemoveChoices: List<String> = emptyList()
        private set

    /** Fixed 3-card ids offered for UPGRADE at the node (set once in [enterNode], cleared on
     *  advance — the upgrade sub-screen order must be stable across renderer + input calls,
     *  fixing the re-shuffle bug where the tapped card was not the offered one). */
    var nodeUpgradeChoices: List<String> = emptyList()
        private set

    /** Which node we're on (1-based); drives cost escalation. */
    var nodeIndex: Int = 0
        private set

    /** Current run deck size (used by the sim's node policy / UI affordance). */
    val deckSize: Int get() = deck.size

    /** Current run deck (ids, acquisition order) — read-only, for sim instrumentation. */
    val deckList: List<String> get() = deck

    /** Resolves the node's removal-offer ids to their full card definitions (for rendering). */
    fun resolveNodeRemoveCards(): List<CardDefinition> =
        nodeRemoveChoices.mapNotNull { cardRegistry.get(it) }

    /** Upgrades bought this run (R3). */
    val upgradesRemaining: Int get() = MAX_UPGRADES_PER_RUN - upgradesUsed

    /** Up to 3 eligible (not-yet-upgraded) deck cards, shuffled - the node upgrade offer (R2). */
    fun resolveNodeUpgradeCards(): List<CardDefinition> =
        nodeUpgradeChoices.mapNotNull { cardRegistry.get(it) }

    /** Copies of [cardId] currently in the deck. */
    private fun copiesInDeck(cardId: String): Int = deck.count { it == cardId }

    /** Whether at least one copy of [cardId] can still be upgraded this run. */
    fun upgradeEligible(cardId: String): Boolean =
        (upgradedCopiesById[cardId] ?: 0) < copiesInDeck(cardId)

    /**
     * Upgrades ONE copy of [cardId] for a flat [NodeConfig.UPGRADE_BASE] gold (R3, decision A):
     * increments the upgraded-copies count and ends the node (one purchase per node).
     * No-op (false) when the card is absent, all its copies are upgraded, the run cap is
     * reached, or gold is insufficient.
     */
    fun upgradeCard(cardId: String): Boolean {
        if (cardId !in deck) return false
        if (!upgradeEligible(cardId)) return false
        if (upgradesUsed >= MAX_UPGRADES_PER_RUN) return false
        if (gold < NodeConfig.UPGRADE_BASE) return false
        gold -= NodeConfig.UPGRADE_BASE
        upgradedCopiesById[cardId] = (upgradedCopiesById[cardId] ?: 0) + 1
        upgradesUsed++
        advanceToNextCombat()
        return true
    }

    /** Run-persistent liability, mirrored from [combatEngine] on every [refresh] while in combat. */
    var debt: Int = 0
        private set

    /** Run-persistent currency, mirrored from [combatEngine] on every [refresh] while in combat. */
    var gold: Int = 0
        private set

    /** Run-persistent player HP, mirrored from [combatEngine] on every [refresh] while in combat. */
    var hp: Int = PlayerState().maxHp
        private set

    /**
     * One-shot flag: set when [debt] first crosses [DebtConfig.BREAK_THRESHOLD] during this run,
     * consumed by the next successful fight's node to force the "collector" encounter. Fires at
     * most once per run (see [breakEncounterUsedThisRun]).
     */
    var pendingBreakEncounter: Boolean = false
        private set

    private var breakEncounterUsedThisRun = false
    private var slotIndex = 0
    private var deck: List<String> = CombatEngine.STARTER_DECK

    /** Upgraded-copies per card id this run (card-upgrades R1, decision A: ONE copy per upgrade).
     *  The deck stores ids; the count selects WHICH copies are upgraded (the first N of each id),
     *  so improving one strike of five upgrades exactly one copy. */
    private val upgradedCopiesById = mutableMapOf<String, Int>()

    /** Upgrades purchased this run; hard-capped by [MAX_UPGRADES_PER_RUN] (R3). */
    private var upgradesUsed = 0

    init {
        beginRun()
    }

    /** Call right after any player action that may have ended the current encounter. */
    fun refresh() {
        if (phase != Phase.COMBAT) return
        val state = combatEngine.getState()
        debt = state.debt
        gold = state.gold
        hp = state.player.hp
        if (!breakEncounterUsedThisRun && debt >= DebtConfig.BREAK_THRESHOLD) {
            pendingBreakEncounter = true
            breakEncounterUsedThisRun = true
        }

        if (state.currentTurn != TurnPhase.COMBAT_END) return

        if (state.player.hp <= 0) {
            phase = Phase.DEFEAT
            return
        }

        // Execution defeat: Debt crossed the threshold mid-combat, so `endCombat` fired while
        // the player still has HP and enemies are still alive. HP-0 above covers life loss; this
        // covers the Debt-driven loss (Debt-as-Leverage pivot), which must also end the run.
        val allEnemiesDead = state.enemies.all { it.hp <= 0 }
        if (!allEnemiesDead) {
            phase = Phase.DEFEAT
            return
        }

        // Enemy defeated: garnish the Gold reward toward Debt repayment instead of granting it
        // in full (see DebtConfig.garnishAmount).
        val currentSlot = runSequence.slots[slotIndex]
        val rawGold = currentSlot.rewards.gold
        val garnished = DebtConfig.garnishAmount(rawGold, debt)
        debt -= garnished
        gold += rawGold - garnished

        if (slotIndex >= runSequence.slots.lastIndex) {
            phase = Phase.VICTORY // final boss: no node after it
        } else {
            enterNode(currentSlot.rewards.cardChoices)
        }
    }

    private fun enterNode(freePickCount: Int) {
        nodeIndex++
        // Flat heal as part of the "rest", capped at max HP.
        hp = minOf(PlayerState().maxHp, hp + NodeConfig.HEAL_AMOUNT)

        rewardChoices = cardRegistry.all()
            .filter { REWARD_EXCLUDED_TAGS.none { tag -> tag in it.tags } }
            .shuffled(rng)
            .take(freePickCount)
        nodeShopChoices = archetypeBiasedOffer()
        nodeRemoveChoices = deck.shuffled(rng).take(NodeConfig.REMOVE_OFFER_SIZE)
        nodeUpgradeChoices = deck.distinct().filter { upgradeEligible(it) }
            .shuffled(rng)
            .take(NodeConfig.REMOVE_OFFER_SIZE)
        phase = Phase.NODE
    }

    /** Free pick: add [card] to the deck, then advance. Alias kept for the old reward flow. */
    fun chooseReward(card: CardDefinition) = takeNodeFreePick(card)

    fun takeNodeFreePick(card: CardDefinition) {
        deck = deck + card.id
        advanceToNextCombat()
    }

    /**
     * Repay Debt at the node: pays `debt + serviceFee(node)` Gold (1:1 + escalating service fee),
     * clearing Debt to 0. No-op when unaffordable (UI disables the button).
     */
    fun repayViaNode(): Boolean {
        if (debt <= 0) return false
        val fee = NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, nodeIndex)
        val cost = debt + fee
        if (gold < cost) return false
        gold -= cost
        debt = 0
        advanceToNextCombat()
        return true
    }

    /**
     * Buy [card] from the node shop: costs escalated BUY_BASE Gold, adds it to the deck.
     * No-op when unaffordable.
     */
    fun buyCard(card: CardDefinition): Boolean {
        val cost = NodeConfig.escalatedCost(NodeConfig.BUY_BASE, nodeIndex)
        if (gold < cost) return false
        gold -= cost
        deck = deck + card.id
        advanceToNextCombat()
        return true
    }

    /**
     * Remove [cardId] from the deck: costs escalated REMOVE_BASE Gold, drops the card (first
     * occurrence) from the run deck only — mid-combat draw piles are engine-owned, untouched.
     * No-op when unaffordable or the id is absent.
     */
    fun removeCardFromDeck(cardId: String): Boolean {
        val cost = NodeConfig.escalatedCost(NodeConfig.REMOVE_BASE, nodeIndex)
        if (gold < cost || cardId !in deck) return false
        gold -= cost
        deck = deck - cardId
        advanceToNextCombat()
        return true
    }

    /**
     * Take the node LOAN: gains escalated LOAN_GOLD_BASE Gold, adds escalated LOAN_DEBT_BASE Debt —
     * the conscious "survive one more step, pay later" trade (and the collector may be armed by the
     * debt rise). Rejected (no-op) when the added Debt crosses Execution — you cannot suicide via loan.
     */
    fun takeLoan(): Boolean {
        val loanGold = NodeConfig.escalatedCost(NodeConfig.LOAN_GOLD_BASE, nodeIndex)
        val loanDebt = NodeConfig.escalatedCost(NodeConfig.LOAN_DEBT_BASE, nodeIndex)
        if (debt + loanDebt > DebtConfig.EXECUTION_THRESHOLD) return false
        gold += loanGold
        debt += loanDebt
        if (!breakEncounterUsedThisRun && debt >= DebtConfig.BREAK_THRESHOLD) {
            pendingBreakEncounter = true
            breakEncounterUsedThisRun = true
        }
        advanceToNextCombat()
        return true
    }

    /** Archetype-biased 3-card offer: weights pool cards by the detected deck archetype. */
    private fun archetypeBiasedOffer(): List<CardDefinition> {
        val pool = cardRegistry.all().filter { REWARD_EXCLUDED_TAGS.none { tag -> tag in it.tags } }
        val archetype = playerArchetype(deck, cardRegistry)
        val weighted = mutableListOf<CardDefinition>()
        for (card in pool) {
            val weight = when (archetype) {
                Archetype.LEVERAGE ->
                    if (card.tags.any { it in LEVERAGE_BIAS }) 3 else if (card.tags.any { it in ECONOMY_BIAS }) 1 else 2
                Archetype.LIQUIDITY ->
                    if (card.tags.any { it in LIQUIDITY_BIAS }) 3 else if (card.tags.any { it in ECONOMY_BIAS }) 1 else 2
                Archetype.PRESSURE ->
                    if (card.tags.none { it in ECONOMY_BIAS }) 3 else 1
            }
            repeat(weight) { weighted.add(card) }
        }
        return weighted.shuffled(rng)
            .distinctBy { it.id }
            .take(NodeConfig.SHOP_OFFER_SIZE)
    }

    private fun advanceToNextCombat() {
        rewardChoices = emptyList()
        nodeShopChoices = emptyList()
        nodeRemoveChoices = emptyList()
        phase = Phase.COMBAT

        if (pendingBreakEncounter) {
            // Forced "collector" encounter: consumes the flag, does NOT advance slotIndex,
            // so the normal run sequence resumes exactly where it left off afterwards.
            pendingBreakEncounter = false
            combatEngine.startCombat(
                listOf(enemyById("collector")),
                deck,
                gold,
                debt,
                hp,
                upgradedCopiesById
            )
        } else {
            slotIndex++
            combatEngine.startCombat(listOf(enemyById(runSequence.slots[slotIndex].enemyId)), deck, gold, debt, hp, upgradedCopiesById)
        }
    }

    fun restartRun() {
        beginRun()
    }

    private fun beginRun() {
        slotIndex = 0
        nodeIndex = 0
        deck = CombatEngine.STARTER_DECK
        upgradedCopiesById.clear()
        upgradesUsed = 0
        rewardChoices = emptyList()
        nodeShopChoices = emptyList()
        nodeRemoveChoices = emptyList()
        nodeUpgradeChoices = emptyList()
        phase = Phase.COMBAT
        debt = 0
        gold = 0
        hp = PlayerState().maxHp
        pendingBreakEncounter = false
        breakEncounterUsedThisRun = false
        combatEngine.startCombat(listOf(enemyById(runSequence.slots[slotIndex].enemyId)), deck, upgradedCopiesById = upgradedCopiesById)
    }

    private fun enemyById(id: String): EnemyDefinition =
        enemyDefinitions.first { it.id == id }

    companion object {
        private const val MAX_UPGRADES_PER_RUN: Int = 2
        // Starter cards are already guaranteed in the deck, so they're excluded from rewards.
        private val REWARD_EXCLUDED_TAGS = setOf("starter")
    }

    private val ECONOMY_BIAS = setOf(
        "debt_scaling", "debt_payoff", "execution_damage",
        "debt_draw", "refinance", "add_debt", "gain_credit", "gold_scaled_debt", "hand_exhaust"
    )
    private val LEVERAGE_BIAS = setOf("debt_scaling", "debt_payoff", "execution_damage")
    private val LIQUIDITY_BIAS = setOf("debt_draw", "refinance", "add_debt", "gain_credit", "gold_scaled_debt", "hand_exhaust")
}