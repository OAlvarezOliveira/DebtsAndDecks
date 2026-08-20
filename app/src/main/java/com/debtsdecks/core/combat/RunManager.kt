package com.debtsdecks.core.combat

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.TurnPhase
import kotlin.random.Random

/**
 * Owns run-level progression on top of [CombatEngine], which only knows about a single
 * encounter. Reuses the same engine instance across encounters via repeated [CombatEngine.startCombat]
 * calls instead of ever replacing it.
 */
class RunManager(
    private val combatEngine: CombatEngine,
    private val cardRegistry: CardRegistry,
    private val enemyDefinitions: List<EnemyDefinition>,
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

    /**
     * One-shot flag: set when [debt] first crosses [DebtConfig.BREAK_THRESHOLD] during this run,
     * consumed by the next [chooseReward] call to force the "collector" encounter. Per spec, this
     * fires at most once per run (see [breakEncounterUsedThisRun]).
     */
    var pendingBreakEncounter: Boolean = false
        private set

    private var breakEncounterUsedThisRun = false
    private var encounterIndex = 0
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
        if (!breakEncounterUsedThisRun && debt >= DebtConfig.BREAK_THRESHOLD) {
            pendingBreakEncounter = true
            breakEncounterUsedThisRun = true
        }

        if (state.currentTurn != TurnPhase.COMBAT_END) return

        if (state.player.hp <= 0) {
            phase = Phase.DEFEAT
            return
        }

        // Enemy defeated: garnish the Gold reward toward Debt repayment instead of granting it
        // in full (see DebtConfig.garnishAmount).
        val rawGold = enemyDefinitions[encounterIndex].rewards.gold
        val garnished = DebtConfig.garnishAmount(rawGold, debt)
        debt -= garnished
        gold += rawGold - garnished

        phase = if (encounterIndex >= enemyDefinitions.lastIndex) {
            Phase.VICTORY
        } else {
            rewardChoices = cardRegistry.all()
                .filter { REWARD_EXCLUDED_TAGS.none { tag -> tag in it.tags } }
                .shuffled(rng)
                .take(3)
            Phase.REWARD
        }
    }

    fun chooseReward(card: CardDefinition) {
        deck = deck + card.id
        rewardChoices = emptyList()
        phase = Phase.COMBAT

        if (pendingBreakEncounter) {
            // Forced "collector" encounter: consumes the flag, does NOT advance encounterIndex,
            // so the normal encounter sequence resumes exactly where it left off afterwards.
            pendingBreakEncounter = false
            combatEngine.startCombat(
                listOf(enemyDefinitions.first { it.id == "collector" }),
                deck,
                gold,
                debt
            )
        } else {
            encounterIndex++
            combatEngine.startCombat(listOf(enemyDefinitions[encounterIndex]), deck, gold, debt)
        }
    }

    fun restartRun() {
        beginRun()
    }

    private fun beginRun() {
        encounterIndex = 0
        deck = CombatEngine.STARTER_DECK
        rewardChoices = emptyList()
        phase = Phase.COMBAT
        debt = 0
        gold = 0
        pendingBreakEncounter = false
        breakEncounterUsedThisRun = false
        combatEngine.startCombat(listOf(enemyDefinitions[encounterIndex]), deck)
    }

    companion object {
        // Starter cards are already guaranteed in the deck, so they're excluded from rewards.
        private val REWARD_EXCLUDED_TAGS = setOf("starter")
    }
}
