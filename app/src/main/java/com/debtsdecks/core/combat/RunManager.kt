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
 */
class RunManager(
    private val combatEngine: CombatEngine,
    private val cardRegistry: CardRegistry,
    private val enemyDefinitions: List<EnemyDefinition>,
    private val runSequence: com.debtsdecks.core.model.RunSequence,
    private val rng: Random
) {
    enum class Phase { COMBAT, REWARD, VICTORY, DEFEAT }

    var phase: Phase = Phase.COMBAT
        private set
    var rewardChoices: List<CardDefinition> = emptyList()
        private set

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
     * consumed by the next [chooseReward] call to force the "collector" encounter. Per spec, this
     * fires at most once per run (see [breakEncounterUsedThisRun]).
     */
    var pendingBreakEncounter: Boolean = false
        private set

    private var breakEncounterUsedThisRun = false
    private var slotIndex = 0
    private var deck: List<String> = CombatEngine.STARTER_DECK

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

        phase = if (slotIndex >= runSequence.slots.lastIndex) {
            Phase.VICTORY
        } else {
            rewardChoices = cardRegistry.all()
                .filter { REWARD_EXCLUDED_TAGS.none { tag -> tag in it.tags } }
                .shuffled(rng)
                .take(currentSlot.rewards.cardChoices)
            Phase.REWARD
        }
    }

    fun chooseReward(card: CardDefinition) {
        deck = deck + card.id
        rewardChoices = emptyList()
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
                hp
            )
        } else {
            slotIndex++
            combatEngine.startCombat(listOf(enemyById(runSequence.slots[slotIndex].enemyId)), deck, gold, debt, hp)
        }
    }

    fun restartRun() {
        beginRun()
    }

    private fun beginRun() {
        slotIndex = 0
        deck = CombatEngine.STARTER_DECK
        rewardChoices = emptyList()
        phase = Phase.COMBAT
        debt = 0
        gold = 0
        hp = PlayerState().maxHp
        pendingBreakEncounter = false
        breakEncounterUsedThisRun = false
        combatEngine.startCombat(listOf(enemyById(runSequence.slots[slotIndex].enemyId)), deck)
    }

    private fun enemyById(id: String): EnemyDefinition =
        enemyDefinitions.first { it.id == id }

    companion object {
        // Starter cards are already guaranteed in the deck, so they're excluded from rewards.
        private val REWARD_EXCLUDED_TAGS = setOf("starter")
    }
}
