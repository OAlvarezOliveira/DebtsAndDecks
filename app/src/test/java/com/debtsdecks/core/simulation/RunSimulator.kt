package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.i18n.Localizer

enum class RunOutcome { VICTORY, DEFEAT }

data class SimulationResult(
    val seed: Long,
    val outcome: RunOutcome,
    val peakDebt: Int,
    val endHp: Int,
    val turnsPerCombat: List<Int>,
    val defeatEncounterId: String?,
        val pickedRewardIds: List<String> = emptyList(),
    /** HP right after each combat ends (before the node heal), aligned index-wise with [turnsPerCombat]. */
    val hpAfterCombat: List<Int> = emptyList(),
    /** Enemy defId fought in each combat, aligned index-wise with [turnsPerCombat]. */
    val encounterIds: List<String?> = emptyList(),
)

/**
 * Sole mutator of [CombatEngine]/[RunManager] during a run: creates one [kotlin.random.Random]
 * per seed (shared by both constructors for determinism, matching RunManagerTest/CombatEngineTest),
 * drives the seed to VICTORY/DEFEAT via [ScriptedPolicy] decisions, and returns a [SimulationResult].
 */
class RunSimulator(
    private val cardRegistry: CardRegistry,
    private val enemyDefinitions: List<EnemyDefinition>,
    private val l10n: Localizer = NoOpLocalizer,
    private val policy: RunPolicy = ScriptedPolicy,
) {
    /** Generous upper bound; any real 3-encounter run stays far below it. Guards runaways. */
    private val maxActionsPerRun = 500

    fun simulate(seed: Long): SimulationResult {
        val rng = kotlin.random.Random(seed)
        val engine = CombatEngine(cardRegistry, l10n, rng)
        val run = RunManager(engine, cardRegistry, enemyDefinitions, TestAssetLoader.loadSequence(), rng)
        var actions = 0
        var peakDebt = 0
        val turnsPerCombat = mutableListOf<Int>()
        val hpAfterCombat = mutableListOf<Int>()
        val encounterIds = mutableListOf<String?>()
        var currentCombatTurnStart: Int? = null
        var defeatEncounterId: String? = null
        val pickedRewardIds = mutableListOf<String>()

        while (true) {
            actions++
            if (actions > maxActionsPerRun) {
                    val st = engine.getState()
                    throw IllegalStateException(
                        "run exceeded max actions (seed=$seed phase=${run.phase} turn=${st.turnNumber} " +
                        "debt=${st.debt} hp=${st.player.hp} enemies=${st.enemies.map { "${it.defId}:${it.hp}" }} " +
                        "turnPhase=${st.currentTurn} hand=${st.hand.map { it.cardId }} energy=${st.energy})"
                    )
                }

            val state = engine.getState()
            check(state.debt >= 0) { "Debt observed negative (seed $seed)" }
            peakDebt = maxOf(peakDebt, state.debt)

            if (state.currentTurn == com.debtsdecks.core.model.TurnPhase.PLAYER_DRAW) {
                currentCombatTurnStart = state.turnNumber
            }

            when (run.phase) {
                RunManager.Phase.COMBAT -> driveCombat(engine, run, state)
                RunManager.Phase.NODE -> {
                        // The combat that just ended is complete at this point (engine hasn't
                        // started the next one yet) — record its turn count here, since NODE may
                        // recur up to 7 times per run and only the final combat hits VICTORY/DEFEAT.
                        turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                        hpAfterCombat.add(state.player.hp)
                        encounterIds.add(currentEncounterId(state))
                        // C7: the sim's node policy makes the between-fight decision, using [policy]
                        // to pick WHICH card among the offers (buy/free-pick) — see NodePolicy.act.
                        // Record the card actually added to the deck, if any (loan/repay/thin add none).
                        val deckBefore = run.deckList
                        NodePolicy.act(run, policy)
                        val deckAfter = run.deckList
                        if (deckAfter.size > deckBefore.size) {
                            pickedRewardIds.add(deckAfter.last())
                        }
                        currentCombatTurnStart = null
                }
                RunManager.Phase.VICTORY -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    hpAfterCombat.add(run.hp)
                    encounterIds.add(currentEncounterId(state))
                    return SimulationResult(
                        seed, RunOutcome.VICTORY, peakDebt, run.hp, turnsPerCombat, null, pickedRewardIds,
                        hpAfterCombat, encounterIds,
                    )
                }
                RunManager.Phase.DEFEAT -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    hpAfterCombat.add(state.player.hp)
                    defeatEncounterId = run.debt?.let { currentEncounterId(state) }
                    encounterIds.add(defeatEncounterId)
                    return SimulationResult(
                        seed, RunOutcome.DEFEAT, peakDebt, 0, turnsPerCombat, defeatEncounterId, pickedRewardIds,
                        hpAfterCombat, encounterIds,
                    )
                }
            }
        }
    }

    private fun driveCombat(engine: CombatEngine, run: RunManager, state: com.debtsdecks.core.model.CombatState) {
        val action = policy.chooseAction(state)
        when (action) {
            is ScriptedPolicy.CombatAction.Play -> {
                engine.playCard(action.instanceId, action.targetId)
                run.refresh()
            }
            ScriptedPolicy.CombatAction.EndTurn -> {
                engine.endPlayerTurn()
                run.refresh()
            }
        }
    }

    private fun turnsFor(start: Int?, endTurn: Int): Int =
        if (start != null) maxOf(1, endTurn - start + 1) else maxOf(1, endTurn)

    private fun currentEncounterId(state: com.debtsdecks.core.model.CombatState): String? =
        state.enemies.firstOrNull { it.hp > 0 }?.defId
            ?: state.enemies.firstOrNull()?.defId
}