package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * FV calibration control measurement: the same 8-slot enemy pattern with HEDGE and FORECLOSE
 * switched OFF, each verb slot reverted to the intent it announced before d94b155 landed
 * (FORECLOSE -> ATTACK 9 on loan_shark, HEDGE -> MULTI_ATTACK 7x2 on collector, per the
 * pre-verb all.json). Same pattern shape, verbs absent. Run under the same two policies and the
 * same seeds as [IntentVerbsE1Test], so the verbs' win-rate share is attributable, and it exposes
 * the instrumentation for the FORECLOSE seizure branch ([CombatEngine.forecloseSeizureCount]).
 *
 * Manual measurement, not a balance gate: like [RunSimulationCsvExportTest], it produces the
 * numbers for the calibration decision and only asserts that the instrumentation is honest.
 */
class ForecloseControlMeasureTest {

    private fun verbsOffControl(enemies: List<EnemyDefinition>): List<EnemyDefinition> {
        val predecessorStep = mapOf(
            "loan_shark" to (IntentType.FORECLOSE to IntentStep(IntentType.ATTACK, damage = 9)),
            "collector" to (IntentType.HEDGE to IntentStep(IntentType.MULTI_ATTACK, damage = 7, param = 2)),
        )
        return enemies.map { def ->
            val swap = predecessorStep[def.id] ?: return@map def
            def.copy(
                intentPattern = def.intentPattern.map { step ->
                    if (step.type == swap.first) swap.second else step
                }
            )
        }
    }

    private fun sweep(
        registry: CardRegistry,
        enemies: List<EnemyDefinition>,
        policy: RunPolicy,
    ): List<SimulationResult> = (0L until 200L).map { seed ->
        RunSimulator(registry, enemies, policy = policy).simulate(seed)
    }

    private fun summarize(
        buildName: String,
        policyName: String,
        results: List<SimulationResult>,
    ) {
        val report = SimulationReport.from(results)
        val seizures = results.sumOf { it.forecloseSeizures }
        val runsWithSeizure = results.count { it.forecloseSeizures > 0 }
        println(
            String.format(
                Locale.US,
                "%-30s %-10s win %5.1f%% | peak %5.1f | hp@win %5.1f | defeats %s | forecloseSeizures %d (in %d runs)",
                buildName, policyName, report.winRate * 100, report.avgPeakDebt,
                report.avgHpAtVictory, report.defeatsByEncounter, seizures, runsWithSeizure,
            )
        )
    }

    @Test
    fun `control build with verbs off vs verbs on, instrumented for FORECLOSE seizures`() {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val control = verbsOffControl(enemies)
        val registry = CardRegistry.create(cards)

        val policies = listOf("responding" to RespondingPolicy, "ignoring" to LeveragePolicy)

        println()
        println("=== FV control: same 8-slot pattern, HEDGE/FORECLOSE on vs off (200 seeds per policy) ===")
        for ((policyName, policy) in policies) {
            summarize("verbs-on (current)", policyName, sweep(registry, enemies, policy))
        }
        val controlResults = policies.map { (policyName, policy) ->
            val results = sweep(registry, control, policy)
            summarize("verbs-off (control)", policyName, results)
            results
        }

        // Instrumentation sanity: the control build has no FORECLOSE step, so its seizure count must
        // be exactly zero across every run — the counter only fires on the seizure branch.
        for (results in controlResults) {
            val controlSeizures = results.sumOf { it.forecloseSeizures }
            assertEquals(0, controlSeizures, "control build replaced every FORECLOSE step; seizures must stay 0")
        }

        // Determinism of the new field: same seeds, same results (outcome and seizure count).
        for (seed in 0L until 20L) {
            val a = RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(seed)
            val b = RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(seed)
            assertEquals(a.outcome, b.outcome, "outcome drifted on seed $seed")
            assertEquals(a.forecloseSeizures, b.forecloseSeizures, "seizure count drifted on seed $seed")
        }
    }
}