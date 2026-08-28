package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.combat.playerArchetype
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * FV core-validation door (E1/E2): why a run died. [endDebt] at or above the execution line is
 * Death by Execution; anything below is ordinary HP loss. The cause must be countable per run,
 * not inferable from a worst-defeats top-5.
 */
enum class DefeatCause { EXECUTION, HP_ZERO }

/** [endDebt] >= the execution line is [DefeatCause.EXECUTION]; otherwise HP reached zero first. */
fun classifyDefeat(endDebt: Int): DefeatCause =
    if (endDebt >= DebtConfig.EXECUTION_THRESHOLD) DefeatCause.EXECUTION else DefeatCause.HP_ZERO

/**
 * Playtest-via-sim observation harness (test-source, deterministic, no asserts beyond health).
 * Runs a batch of seeds and records, per run: outcome, defeat enemy + cause + slot, peak debt,
 * end gold/debt/hp, and the full node-by-node decision trail (gold/debt/deck before-after +
 * inferred action). Stdout lands in the JUnit XML for review — the raw material for weak-point
 * analysis.
 */
class RunObservationTest {

    private val registry = CardRegistry.create(TestAssetLoader.loadCards())
    private val enemies = TestAssetLoader.loadEnemies()
    private val sequence = TestAssetLoader.loadSequence()

    private class NodeStep(val idx: Int, val act: String, val gold: Int, val debt: Int, val deck: Int, val endGold: Int, val endDebt: Int, val endDeck: Int)

    private class Trace(
        val seed: Long,
        val outcome: String,
        val encountersWon: Int,
        val peakDebt: Int,
        val endDebt: Int,
        val endGold: Int,
        val endHp: Int,
        val defeatEnemy: String?,
        val defeatCause: DefeatCause?,
        val defeatSlot: Int?,
        val endDeckSize: Int,
        val nodes: List<NodeStep>,
    )

    private fun inferAction(run: RunManager, gold0: Int, debt0: Int, deck0: Int): String = when {
        run.debt < debt0 -> "REPAY"
        run.gold > gold0 && run.debt > debt0 -> "LOAN"
        run.deckSize > deck0 && run.gold < gold0 -> "BUY"
        run.deckSize < deck0 -> "REMOVE"
        run.deckSize > deck0 -> "FREE_PICK"
        else -> "FREE_PICK"
    }

    private fun runTrace(seed: Long, policy: RunPolicy, maxActions: Int = 600): Trace {
        val rng = Random(seed)
        val engine = CombatEngine(registry, NoOpLocalizer, rng)
        val run = RunManager(engine, registry, enemies, sequence, rng)
        val nodes = mutableListOf<NodeStep>()
        var peakDebt = 0
        var actions = 0
        // 0-based sequence slot of the combat in progress. The forced BREAK "collector" rematch does
        // NOT advance it (RunManager keeps its slot index), so the slot is tracked here rather than
        // derived from node count — a won rematch adds a node without adding a run slot.
        var currentSlot = 0

        while (actions < maxActions) {
            actions++
            val state = engine.getState()
            peakDebt = maxOf(peakDebt, state.debt)

            if (run.phase == RunManager.Phase.COMBAT && state.currentTurn == TurnPhase.PLAYER_ACTION) {
                when (val action = policy.chooseAction(state)) {
                    is ScriptedPolicy.CombatAction.Play -> engine.playCard(action.instanceId, action.targetId)
                    ScriptedPolicy.CombatAction.EndTurn -> engine.endPlayerTurn()
                }
                run.refresh()
            }

            if (run.phase == RunManager.Phase.NODE) {
                // The BREAK rematch flag is consumed INSIDE NodePolicy.act (via advanceToNextCombat), so
                // sample it before acting: a pending rematch keeps the next combat on the same run slot,
                // anything else advances the sequence.
                val hadBreak = run.pendingBreakEncounter
                val g0 = run.gold; val d0 = run.debt; val k0 = run.deckSize
                NodePolicy.act(run, policy)
                run.refresh() // sync run fields (deckSize is live; gold/debt checked directly)
                nodes.add(NodeStep(run.nodeIndex, inferAction(run, g0, d0, k0), g0, d0, k0, run.gold, run.debt, run.deckSize))
                if (run.phase == RunManager.Phase.COMBAT && !hadBreak) currentSlot++
            }

            if (run.phase == RunManager.Phase.VICTORY || run.phase == RunManager.Phase.DEFEAT) {
                val st = engine.getState()
                val isDefeat = run.phase == RunManager.Phase.DEFEAT
                return Trace(
                    seed, run.phase.name, nodes.size /* = fights won ✓ (1 per non-boss, 8 total)*/,
                    peakDebt, run.debt, run.gold, run.hp,
                    if (isDefeat) st.enemies.firstOrNull { it.hp > 0 }?.defId else null,
                    if (isDefeat) classifyDefeat(run.debt) else null,
                    if (isDefeat) currentSlot + 1 else null,
                    run.deckSize, nodes
                )
            }
        }
        error("seed $seed max-actions")
    }

    @Test
    fun `batch observation across seeds`() {
        println("================ BATCH OBSERVATION: 80 seeds, greedy ScriptedPolicy ================")
        val traces = (0L until 80L).map { runTrace(it, ScriptedPolicy) }

        // ---------- A. outcome & death table ----------
        val wins = traces.filter { it.outcome == "VICTORY" }
        val deaths = traces.filter { it.outcome == "DEFEAT" }
        println("wins=${wins.size}/80 (${(wins.size / 80.0 * 100).toInt()}%)  defeats=${deaths.size}")
        println("avg peakDebt(all)=${(traces.map { it.peakDebt }.average()).toInt()}  avg endGold(win)=${wins.map { it.endGold }.average().toInt()}  avg endHp(win)=${wins.map { it.endHp }.average().toInt()}")
        deaths.filter { it.defeatEnemy != null }.groupBy { it.defeatEnemy }.entries.sortedBy { it.key }.forEach { (enemy, ts) ->
            println("  die vs $enemy: ${ts.size} | avg peakDebt=${ts.map { it.peakDebt }.average().toInt()} avg endGold=${ts.map { it.endGold }.average().toInt()} avg endDebt=${ts.map { it.endDebt }.average().toInt()} avg endHp=${ts.map { it.endHp }.average().toInt()}")
        }

        // ---------- A.5 defeat cause & slot (FV E1/E2 door: countable, not top-5 inference) ----------
        println()
        println("---- defeat causes & slots (${deaths.size} defeats) ----")
        val causeCounts = DefeatCause.entries.map { c -> c to deaths.count { it.defeatCause == c } }.filter { it.second > 0 }
        println("  cause: " + causeCounts.joinToString(" ") { "${it.first}=${it.second}" })
        deaths.groupingBy { it.defeatSlot }.eachCount().toSortedMap(compareBy { it ?: -1 }).forEach { (slot, count) ->
            val byCause = DefeatCause.entries
                .map { c -> c to deaths.count { it.defeatSlot == slot && it.defeatCause == c } }
                .filter { it.second > 0 }
            println("  slot=$slot: defeats=$count causes=${byCause.joinToString(" ") { "${it.first}=${it.second}" }}")
        }

        // ---------- B. node decision distribution ----------
        println()
        println("---- node decision counts (all 80 runs × 7 nodes) ----")
        val acts = traces.flatMap { it.nodes }.groupingBy { it.idx to it.act }.eachCount()
        (1..7).forEach { n ->
            val row = acts.filterKeys { it.first == n }.entries.sortedBy { it.key.second }
            println("  node$n: " + row.joinToString(" | ") { "${it.key.second}=${it.value}" })
        }

        // ---------- C. what kills the runs that reach the collector ----------
        val reachedBoss = traces.filter { it.encountersWon >= 7 }
        println()
        println("---- reached final boss (won ≥7 fights): ${reachedBoss.size}/80; deaths among them: ${reachedBoss.count { it.outcome == "DEFEAT" }} ----")
        println("  avg state at end (all who reached): debt=${reachedBoss.map { it.endDebt }.average().toInt()} gold=${reachedBoss.map { it.endGold }.average().toInt()} hp=${reachedBoss.map { it.endHp }.average().toInt()} deck=${reachedBoss.map { it.endDeckSize }.average().toInt()}")

        // ---------- D. per-node state drift (how gold/debt/deck evolve) ----------
        println()
        println("---- avg per-node state (before decision) ----")
        (1..7).forEach { n ->
            val steps = traces.flatMap { it.nodes }.filter { it.idx == n }
            println("  node$n: gold=${steps.map { it.gold }.average().toInt()} debt=${steps.map { it.debt }.average().toInt()} deck=${steps.map { it.deck }.average().toInt()}")
        }

        // ---------- E. worst runs (5 by lowest endHp at victory / most debt at death) ----------
        println()
        println("---- 5 closest wins (lowest endHp) ----")
        wins.sortedBy { it.endHp }.take(5).forEach {
            println("  seed=${it.seed} endHp=${it.endHp} peakDebt=${it.peakDebt} endGold=${it.endGold} deck=${it.endDeckSize} nodes=${it.nodes.map { n -> n.act }.joinToString(",")}")
        }
        println("---- 5 worst defeats (highest endDebt) ----")
        deaths.sortedByDescending { it.endDebt }.take(5).forEach {
            println("  seed=${it.seed} vs=${it.defeatEnemy} endDebt=${it.endDebt} peakDebt=${it.peakDebt} endGold=${it.endGold} hp=${it.endHp}")
        }

        // health gate (observation runs must all terminate)
        assertTrue(traces.all { it.outcome == "VICTORY" || it.outcome == "DEFEAT" })
        assertTrue(traces.all { it.encountersWon in 0..8 })
        // no defeat may report a slot outside the run sequence (a won BREAK rematch must not inflate it)
        assertTrue(deaths.all { it.defeatSlot in 1..8 })
    }

    @Test
    fun `defeat cause follows the execution threshold`() {
        assertEquals(DefeatCause.HP_ZERO, classifyDefeat(DebtConfig.EXECUTION_THRESHOLD - 1))
        assertEquals(DefeatCause.EXECUTION, classifyDefeat(DebtConfig.EXECUTION_THRESHOLD))
        assertEquals(DefeatCause.EXECUTION, classifyDefeat(DebtConfig.EXECUTION_THRESHOLD + 1))
    }

}