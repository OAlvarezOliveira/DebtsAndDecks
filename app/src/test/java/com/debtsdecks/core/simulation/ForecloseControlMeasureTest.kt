package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.EnemyRewards
import com.debtsdecks.core.enemies.EnemyTier
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType
import com.debtsdecks.core.model.CardDefinition
import com.debtsdecks.core.model.CombatState
import com.debtsdecks.core.model.EncounterSlot
import com.debtsdecks.core.model.RunSequence
import com.debtsdecks.core.model.SlotRole
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
        val control = VerbControl.verbsOffControl(enemies)
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

    /**
     * R3-001 guard: the run-level wiring ([RunSimulator] reading [CombatEngine.forecloseSeizureCount]
     * at run end) must carry a POSITIVE value end-to-end, not only the zero and same-seed cases.
     * Deterministic fixture: one FORECLOSE-only enemy whose fee is 0 damage and whose threshold is
     * 27. A policy that only ends its turn lets the compounding interest (start 6, ceil(15%)/turn)
     * cross 27 on turn 9, where the seizure fires exactly once and ends the run in defeat. If the
     * simulator stopped reading the engine's counter (stale engine, per-combat reset, or a literal
     * 0), this test fails while every real sweep would still print a silently undercounted number.
     */
    @Test
    fun `run-level wiring carries a positive seizure count end-to-end`() {
        val registry = CardRegistry.create(TestAssetLoader.loadCards())
        val bailiff = EnemyDefinition(
            id = "bailiff",
            name = "Bailiff",
            hp = 60,
            intentPattern = listOf(IntentStep(IntentType.FORECLOSE, damage = 0, param = 27)),
            rewards = EnemyRewards(gold = 10, cardChoices = 1),
            tier = EnemyTier.ELITE,
        )
        val oneSlot = RunSequence(
            slots = listOf(
                EncounterSlot(
                    enemyId = "bailiff",
                    districtId = "slaughterhouse",
                    rewards = EnemyRewards(gold = 10, cardChoices = 1),
                    role = SlotRole.BOSS,
                )
            )
        )
        val result = RunSimulator(registry, listOf(bailiff), sequence = oneSlot, policy = EndTurnOnlyPolicy).simulate(0)
        assertEquals(RunOutcome.DEFEAT, result.outcome, "the seizure must end the fixture run")
        assertEquals(1, result.forecloseSeizures, "exactly one seizure: debt crosses 27 on turn 9, run ends there")
    }

    /**
     * Minimal policy for [run-level wiring carries a positive seizure count end-to-end]: never plays
     * a card, so nothing repairs, attacks, or blocks the fixture.
     */
    private object EndTurnOnlyPolicy : RunPolicy {
        override fun chooseAction(state: CombatState): ScriptedPolicy.CombatAction {
            return ScriptedPolicy.CombatAction.EndTurn
        }

        override fun chooseReward(choices: List<CardDefinition>): CardDefinition = choices.first()
    }
}