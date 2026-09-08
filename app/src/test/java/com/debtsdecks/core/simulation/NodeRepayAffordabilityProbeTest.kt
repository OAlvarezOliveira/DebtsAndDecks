package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.NodeConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.random.Random

/**
 * FV.E1 phase one — the fail-fast probe for the node-level response channel
 * (`openspec/changes/fv-e1-node-response-channel/`).
 *
 * It answers ONE question before any hook is wired: across the standard 200 seeds under
 * [RespondingPolicy] as currently shipped, how often would [RunManager.repayViaNode] actually be
 * AFFORDABLE at the three pre-`loan_shark` node opportunities (`sequence.json` slots 2 / 4 / 5)?
 *
 * Three properties make the number trustworthy rather than plausible:
 *
 *  - **Read-only** (design D4). Affordability is computed from `run.gold` / `run.debt` /
 *    `run.nodeIndex`, which is exactly `repayViaNode`'s own guard (`RunManager.kt:228-231`).
 *    The probe NEVER calls `repayViaNode()`; calling it would change the run it is measuring.
 *  - **Mirror-verified** (design D2). The upcoming enemy comes from [RunSlotCursor], a test-source
 *    mirror of the private `RunManager.slotIndex`, and that mirror is asserted against the engine at
 *    every single combat start (~1400 assertions). A silent off-by-one would target the wrong node
 *    and still print a plausible table.
 *  - **Non-perturbing** (design D3). The private drive loop below is a transcription of
 *    [RunSimulator.simulate]; the second test proves per seed that it produces the identical
 *    trajectory, so the probe measures the shipped run and not a lookalike. House precedent for a
 *    private loop: `RunObservationTest.runTrace`.
 *
 * Decision §6.7: this ships as a permanent regression test, not a throwaway — it is the canary if
 * future garnish/fee changes make `repayViaNode()` dormant again.
 */
class NodeRepayAffordabilityProbeTest {

    private val registry = CardRegistry.create(TestAssetLoader.loadCards())
    private val enemies = TestAssetLoader.loadEnemies()
    private val sequence = TestAssetLoader.loadSequence()
    private val policy: RunPolicy = RespondingPolicy

    /** Generous upper bound, transcribed from [RunSimulator]. Guards runaways. */
    private val maxActionsPerRun = 500

    private class SlotTally {
        /** Nodes whose mirrored next enemy is `loan_shark` — proposal §6.4's denominator. */
        var reached = 0

        /** Of those, how many could have paid `debt + fee` right there. */
        var affordable = 0

        /** Of those, how many the existing `NodePolicy.kt:45` rung already repaid (design D5). */
        var alreadyRepaidByLadder = 0

        /** Affordable AND not already repaid by the ladder — decision §6.3's room to act. */
        var headroom = 0
    }

    private class ProbeRun(
        val outcome: RunOutcome,
        val peakDebt: Int,
        val endHp: Int,
        val defeatEncounterId: String?,
        val nodeCount: Int,
        val turnsPerCombat: List<Int>,
        val loanArmedBreaks: Int,
        val mirrorChecks: Int,
    )

    /**
     * Phase one's measurement, and the shipped guard that outlives it.
     *
     * The §6.4 go/no-go bar (>= 30% aggregate, one slot > 20%) is still computed and printed as an
     * explicit PASS/FAIL verdict — it is the deliverable, and re-running this test must reproduce it.
     * It measured **FAIL** on 2026-08-29 (5.8% aggregate, best slot 12.9%; see
     * `docs/BALANCE-BASELINE.md`), so per decision §6.7 / design D7 the *asserted* guard is the floor
     * canary [CANARY_FLOOR], not the bar: a go/no-go decision bar is not a shipped quality gate, and a
     * permanently red or `@Disabled` test is not a canary. The canary fires if a future garnish or fee
     * change pushes `repayViaNode()` even further into dormancy.
     */
    @Test
    fun `repay affordability at every loan_shark opportunity holds its measured floor`() {
        val tallies = LOAN_SHARK_SLOTS.associateWith { SlotTally() }
        val runs = (0L until SEEDS).map { drive(it, tallies) }

        val reachedTotal = LOAN_SHARK_SLOTS.sumOf { tallies.getValue(it).reached }
        val affordableTotal = LOAN_SHARK_SLOTS.sumOf { tallies.getValue(it).affordable }
        val repaidTotal = LOAN_SHARK_SLOTS.sumOf { tallies.getValue(it).alreadyRepaidByLadder }
        val headroomTotal = LOAN_SHARK_SLOTS.sumOf { tallies.getValue(it).headroom }
        val aggregateRate = if (reachedTotal == 0) 0.0 else affordableTotal.toDouble() / reachedTotal
        val perSlotRate = LOAN_SHARK_SLOTS.associateWith { slot ->
            val t = tallies.getValue(slot)
            if (t.reached == 0) 0.0 else t.affordable.toDouble() / t.reached
        }
        val bestSlotRate = perSlotRate.values.max()
        val verdict = if (aggregateRate >= AGGREGATE_BAR && bestSlotRate > PER_SLOT_BAR) "PASS" else "FAIL"

        // Printed BEFORE the asserts so the numbers survive a failing run (task 1.7).
        println()
        println("=== FV.E1 phase one — repayViaNode() affordability at the pre-loan_shark nodes ===")
        println("$SEEDS seeds, policy=RespondingPolicy (as shipped); read-only, repayViaNode() never called")
        LOAN_SHARK_SLOTS.forEach { slot ->
            val t = tallies.getValue(slot)
            println(
                String.format(
                    Locale.US,
                    "slot=%d reached=%d affordable=%d (%.1f%%) alreadyRepaidByLadder=%d headroom=%d",
                    slot, t.reached, t.affordable, perSlotRate.getValue(slot) * 100,
                    t.alreadyRepaidByLadder, t.headroom,
                )
            )
        }
        println(
            String.format(
                Locale.US,
                "AGGREGATE reached=%d affordable=%d (%.1f%%) alreadyRepaidByLadder=%d headroom=%d" +
                    "  bar: >=%.0f%% and one slot >%.0f%% -> %s",
                reachedTotal, affordableTotal, aggregateRate * 100, repaidTotal, headroomTotal,
                AGGREGATE_BAR * 100, PER_SLOT_BAR * 100, verdict,
            )
        )
        println(
            "mirror assertions=${runs.sumOf { it.mirrorChecks }} " +
                "(engine enemy defId == RunSlotCursor.expected at every combat start); " +
                "loan-armed BREAK rematches observed=${runs.sumOf { it.loanArmedBreaks }}"
        )
        println()

        // Health (task 1.9): the denominator must be real, and every rate must be a rate.
        assertTrue(reachedTotal > 0, "no pre-loan_shark opportunity was ever reached — the mirror is wrong")
        LOAN_SHARK_SLOTS.forEach { slot ->
            val t = tallies.getValue(slot)
            assertTrue(
                perSlotRate.getValue(slot) in 0.0..1.0,
                "slot $slot rate outside 0..1 (affordable=${t.affordable} reached=${t.reached})"
            )
            assertTrue(
                t.alreadyRepaidByLadder <= t.affordable,
                "slot $slot: the ladder repaid ${t.alreadyRepaidByLadder} times but only ${t.affordable} " +
                    "were affordable — the read-only affordability guard has drifted from NodePolicy.kt:45"
            )
        }

        // The §6.4 bar measured FAIL on 2026-08-29 and the direction stopped there, so the shipped
        // assertion is the floor canary (design D7), NOT the bar. The bar itself stays computed and
        // printed above as `verdict` — softening it was never on the table; it was answered.
        assertTrue(
            aggregateRate >= CANARY_FLOOR,
            String.format(
                Locale.US,
                "repayViaNode() affordability fell to %.1f%% of %d reached opportunities, below the " +
                    "%.1f%% floor recorded on 2026-08-29 (%.1f%%, 28/486) — a garnish or fee change has " +
                    "pushed the node repay rule further into dormancy; see docs/BALANCE-BASELINE.md",
                aggregateRate * 100, reachedTotal, CANARY_FLOOR * 100, MEASURED_AGGREGATE * 100,
            )
        )
    }

    @Test
    fun `the probe observes without perturbing the run it measures`() {
        val tallies = LOAN_SHARK_SLOTS.associateWith { SlotTally() }
        (0L until SEEDS).forEach { seed ->
            val probed = drive(seed, tallies)
            val shipped = RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(seed)

            assertEquals(shipped.outcome, probed.outcome, "seed $seed: outcome diverged")
            assertEquals(shipped.peakDebt, probed.peakDebt, "seed $seed: peakDebt diverged")
            assertEquals(shipped.endHp, probed.endHp, "seed $seed: endHp diverged")
            assertEquals(
                shipped.defeatEncounterId, probed.defeatEncounterId,
                "seed $seed: defeatEncounterId diverged"
            )
            // RunSimulator records one turn count per combat: nodes + the final combat.
            assertEquals(
                shipped.turnsPerCombat.size - 1, probed.nodeCount,
                "seed $seed: node count diverged"
            )
            assertEquals(
                shipped.turnsPerCombat, probed.turnsPerCombat,
                "seed $seed: per-combat turn counts diverged"
            )
        }
    }

    /**
     * Transcription of [RunSimulator.simulate] (design D3) with read-only instrumentation added at
     * the NODE branch. It calls `NodePolicy.act(run, policy)` unchanged and owns no mutator of its
     * own — the trajectory-equality test above is the mechanical proof that it stays a transcription.
     */
    private fun drive(seed: Long, tallies: Map<Int, SlotTally>): ProbeRun {
        val rng = Random(seed)
        val engine = CombatEngine(registry, NoOpLocalizer, rng)
        val run = RunManager(engine, registry, enemies, sequence, rng)
        val cursor = RunSlotCursor(sequence)
        var actions = 0
        var peakDebt = 0
        var nodeCount = 0
        var mirrorChecks = 0
        val turnsPerCombat = mutableListOf<Int>()
        var currentCombatTurnStart: Int? = null

        // D2: the mirror is verified, never trusted — starting with the opening combat.
        assertEquals(
            cursor.expected, engine.getState().enemies.first().defId,
            "seed $seed: opening combat does not match the mirrored slot"
        )
        mirrorChecks++

        while (true) {
            actions++
            check(actions <= maxActionsPerRun) { "run exceeded max actions (seed=$seed phase=${run.phase})" }

            val state = engine.getState()
            check(state.debt >= 0) { "Debt observed negative (seed $seed)" }
            peakDebt = maxOf(peakDebt, state.debt)

            if (state.currentTurn == TurnPhase.PLAYER_DRAW) {
                currentCombatTurnStart = state.turnNumber
            }

            when (run.phase) {
                RunManager.Phase.COMBAT -> driveCombat(engine, run, state)
                RunManager.Phase.NODE -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))

                    // Sampled BEFORE acting: NodePolicy.act consumes the BREAK flag (via
                    // advanceToNextCombat), and a node LOAN can arm a new one inside act.
                    val pendingBefore = run.pendingBreakEncounter
                    val debtBefore = run.debt
                    val opportunitySlot =
                        if (cursor.nextEnemyId(pendingBefore) == LOAN_SHARK_ENEMY_ID) cursor.slotIndex + 1 else null

                    // D4: read-only affordability — this IS repayViaNode's guard, not a call to it.
                    var affordable = false
                    if (opportunitySlot != null) {
                        val fee = NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, run.nodeIndex)
                        affordable = run.debt > 0 && run.gold >= run.debt + fee
                        val tally = tallies.getValue(opportunitySlot)
                        tally.reached++
                        if (affordable) tally.affordable++
                    }

                    NodePolicy.act(run, policy)

                    if (opportunitySlot != null) {
                        // D5: repayViaNode is the only node action that zeroes debt, so observing
                        // debt 0 after act is an exact read of the NodePolicy.kt:45 rung firing —
                        // re-implementing its guard here would be a second source of truth.
                        val repaidByLadder = debtBefore > 0 && run.debt == 0
                        val tally = tallies.getValue(opportunitySlot)
                        if (repaidByLadder) tally.alreadyRepaidByLadder++
                        if (affordable && !repaidByLadder) tally.headroom++
                    }

                    cursor.advance(pendingBefore, debtBefore, run)
                    assertEquals(
                        cursor.expected, engine.getState().enemies.first().defId,
                        "seed $seed: combat after node ${run.nodeIndex} does not match the mirrored slot"
                    )
                    mirrorChecks++
                    nodeCount++
                    currentCombatTurnStart = null
                }
                RunManager.Phase.VICTORY -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    return ProbeRun(
                        RunOutcome.VICTORY, peakDebt, run.hp, null, nodeCount, turnsPerCombat,
                        cursor.loanArmedBreakCount, mirrorChecks,
                    )
                }
                RunManager.Phase.DEFEAT -> {
                    turnsPerCombat.add(turnsFor(currentCombatTurnStart, state.turnNumber))
                    return ProbeRun(
                        RunOutcome.DEFEAT, peakDebt, 0, currentEncounterId(state), nodeCount,
                        turnsPerCombat, cursor.loanArmedBreakCount, mirrorChecks,
                    )
                }
            }
        }
    }

    private fun driveCombat(engine: CombatEngine, run: RunManager, state: CombatState) {
        when (val action = policy.chooseAction(state)) {
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

    private fun currentEncounterId(state: CombatState): String? =
        state.enemies.firstOrNull { it.hp > 0 }?.defId ?: state.enemies.firstOrNull()?.defId

    private companion object {
        const val SEEDS = 200L
        const val LOAN_SHARK_ENEMY_ID = "loan_shark"

        /** 0-based `sequence.json` slots carrying `loan_shark` (lines 5 / 7 / 8). */
        val LOAN_SHARK_SLOTS = listOf(2, 4, 5)

        /** Proposal §6.4, closed by the owner: >= 30% aggregate, with one slot above 20%. */
        const val AGGREGATE_BAR = 0.30
        const val PER_SLOT_BAR = 0.20

        /** Measured 2026-08-29, 200 seeds: 28 affordable of 486 reached opportunities. */
        const val MEASURED_AGGREGATE = 28.0 / 486.0

        /** Design D7 floor canary shipped in place of the (failed) §6.4 bar: measured - 5pp. */
        const val CANARY_FLOOR = MEASURED_AGGREGATE - 0.05
    }
}
