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
                        // C7: the sim's node policy makes the between-fight decision. Record the
                        // free-pick offer id (buy cards also come from the same pool) so the
                        // "table is played" evidence (R4.3-style) keeps working.
                        val offerId = run.rewardChoices.firstOrNull()?.id ?: "node_no_free_pick"
                        NodePolicy.act(run)
                        pickedRewardIds.add(offerId)
                        currentCombatTurnStart = null
                    
                }
                RunManager.Phase.VICTORY -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    return SimulationResult(seed, RunOutcome.VICTORY, peakDebt, run.hp, turnsPerCombat, null, pickedRewardIds)
                }
                RunManager.Phase.DEFEAT -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    defeatEncounterId = run.debt?.let { currentEncounterId(state) }
                    return SimulationResult(seed, RunOutcome.DEFEAT, peakDebt, 0, turnsPerCombat, defeatEncounterId, pickedRewardIds)
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