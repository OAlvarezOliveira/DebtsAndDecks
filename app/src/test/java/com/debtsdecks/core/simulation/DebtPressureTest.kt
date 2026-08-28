package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.combat.CombatEngine
import com.debtsdecks.core.combat.DebtConfig
import com.debtsdecks.core.combat.RunManager
import com.debtsdecks.core.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * F2 door: Debt must be a live pressure, not a decoration.
 *
 * The baseline these gates were written against (80 seeds, greedy ScriptedPolicy, develop @ 672d9e0):
 *   - defeat causes: EXECUTION = 1, HP_ZERO = 37  -> Debt kills 2.6% of the time
 *   - mean debt per node: 7, 9, 20, 20, 19, 16, 13 -> peaks at node 3 and DECAYS to the end
 *
 * These are DESIGN CLAIMS, not observations: they assert what the pivot ("Debt is the difficulty
 * axis") requires, and they fail on the baseline on purpose. Every threshold is scale-free - a
 * share of defeats, or a ratio between two measured debts - so a re-scale of the economy
 * (F1 R1.3) keeps them meaning the same thing.
 *
 * WHY THE TWO FAILING GATES ARE @Disabled RATHER THAN RED
 * ------------------------------------------------------
 * The first hypothesis for the numbers above was garnishment: [DebtConfig.garnishAmount], applied
 * in RunManager.onCombatWon, repays Debt 1:1 for free at a rate that SCALES WITH DEBT, which makes
 * it a negative-feedback governor - more debt, more garnishment, less debt. Removing the
 * `debt -= garnished` settlement was predicted to let Debt run away and raise difficulty.
 *
 * It was implemented and measured across the same 80 seeds. The prediction was WRONG, and not by a
 * little:
 *
 * |                    | trunk        | no debt settlement |
 * |--------------------|--------------|--------------------|
 * | victories          | 42/80 (52%)  | 78/80 (97%)        |
 * | mean end HP (win)  | 11           | 39                 |
 * | mean peak debt     | 20           | 109                |
 *
 * Debt ran away exactly as predicted and the game became TRIVIAL, because of CardResolver's
 * leverage rule: every ATTACK gains an uncapped `state.debt / DebtConfig.LEVERAGE_DIVISOR` bonus.
 * At debt 109 a strike goes from 6 to 24 damage. Debt is not a cost in this build - it is a damage
 * stat, and garnishment was the governor MASKING an unbounded payoff.
 *
 * Both gates below PASSED under that change while the game degenerated. That is the lesson worth
 * keeping: they measure "is Debt large" and "does Debt sometimes kill", neither of which implies
 * the run is hard. What caught the regression were the pre-existing win-rate bands in
 * RunSimulationHarnessTest - any future work on this axis must keep those in the loop.
 *
 * They stay here, disabled, as the recorded shape of the open design debt. Re-enable them together
 * with a cap on the leverage bonus, not on their own: they are necessary, not sufficient.
 */
class DebtPressureTest {

    private val registry = CardRegistry.create(TestAssetLoader.loadCards())
    private val enemies = TestAssetLoader.loadEnemies()
    private val sequence = TestAssetLoader.loadSequence()

    private class Run(
        val defeated: Boolean,
        val cause: DefeatCause?,
        /** Debt sampled on arriving at each node, in node order. */
        val debtByNode: List<Int>,
    )

    /** Minimum share of defeats that Debt itself must cause for the axis to be load-bearing. */
    private val EXECUTION_SHARE_FLOOR = 0.20

    /** Debt at the last node, over debt at its peak node. Below this the run DEFLATES as it goes. */
    private val LATE_OVER_PEAK_FLOOR = 0.90

    private fun simulate(seed: Long, maxActions: Int = 600): Run {
        val rng = Random(seed)
        val engine = CombatEngine(registry, NoOpLocalizer, rng)
        val run = RunManager(engine, registry, enemies, sequence, rng)
        val debtByNode = mutableListOf<Int>()
        var actions = 0

        while (actions < maxActions) {
            actions++
            val state = engine.getState()

            if (run.phase == RunManager.Phase.COMBAT && state.currentTurn == TurnPhase.PLAYER_ACTION) {
                when (val action = ScriptedPolicy.chooseAction(state)) {
                    is ScriptedPolicy.CombatAction.Play -> engine.playCard(action.instanceId, action.targetId)
                    ScriptedPolicy.CombatAction.EndTurn -> engine.endPlayerTurn()
                }
                run.refresh()
            }

            if (run.phase == RunManager.Phase.NODE) {
                debtByNode.add(run.debt)
                NodePolicy.act(run, ScriptedPolicy)
                run.refresh()
            }

            if (run.phase == RunManager.Phase.VICTORY || run.phase == RunManager.Phase.DEFEAT) {
                val isDefeat = run.phase == RunManager.Phase.DEFEAT
                return Run(isDefeat, if (isDefeat) classifyDefeat(run.debt) else null, debtByNode)
            }
        }
        error("seed $seed max-actions")
    }

    private fun sweep(): List<Run> = (1L..80L).map { simulate(it) }

    @Disabled("F2 open design debt: fails on trunk by design, and passing it is NOT sufficient - see the class KDoc.")
    @Test
    fun `Debt causes a material share of defeats`() {
        val runs = sweep()
        val defeats = runs.filter { it.defeated }
        val byExecution = defeats.count { it.cause == DefeatCause.EXECUTION }
        val share = byExecution.toDouble() / defeats.size

        assertTrue(
            share >= EXECUTION_SHARE_FLOOR,
            "Debt is decorative: only $byExecution of ${defeats.size} defeats " +
                "(${"%.1f".format(java.util.Locale.US, share * 100)}%) were caused by Debt itself, " +
                "below the ${"%.0f".format(java.util.Locale.US, EXECUTION_SHARE_FLOOR * 100)}% floor. " +
                "Everything else died to HP loss, which any deck-builder has."
        )
    }

    @Disabled("F2 open design debt: fails on trunk by design, and passing it is NOT sufficient - see the class KDoc.")
    @Test
    fun `Debt pressure does not decay over the course of a run`() {
        val runs = sweep()
        val nodeCount = runs.minOf { it.debtByNode.size }
        val meanAt = { i: Int -> runs.map { it.debtByNode[i] }.average() }
        val means = (0 until nodeCount).map(meanAt)

        val peak = means.max()
        val late = means.last()
        val ratio = late / peak

        assertTrue(
            ratio >= LATE_OVER_PEAK_FLOOR,
            "Debt deflates as the run progresses: mean debt per node is " +
                means.joinToString(", ") { "%.1f".format(java.util.Locale.US, it) } +
                " — it peaks at ${"%.1f".format(java.util.Locale.US, peak)} and ends at " +
                "${"%.1f".format(java.util.Locale.US, late)} (ratio " +
                "${"%.2f".format(java.util.Locale.US, ratio)} < $LATE_OVER_PEAK_FLOOR). " +
                "The back half of the run is financially EASIER than the front half."
        )
    }

    @Test
    fun `the execution line stays above the break threshold`() {
        // Guard for the re-scale: the collector must arrive before death, or the leverage band
        // has no room to be played (see DebtConfig.EXECUTION_THRESHOLD).
        assertTrue(
            DebtConfig.EXECUTION_THRESHOLD > DebtConfig.BREAK_THRESHOLD,
            "EXECUTION_THRESHOLD (${DebtConfig.EXECUTION_THRESHOLD}) must stay above " +
                "BREAK_THRESHOLD (${DebtConfig.BREAK_THRESHOLD})"
        )
    }
}
