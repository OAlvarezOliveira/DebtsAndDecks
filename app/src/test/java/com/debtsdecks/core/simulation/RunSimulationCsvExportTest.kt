package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale

/**
 * Manual balance-data export: runs a wide seed sweep per policy and writes one CSV row per run.
 * Not a balance assertion (that lives in RunSimulationHarnessTest) — this test only exists to
 * produce a dataset for offline analysis. Output is gitignored (app/build/).
 */
class RunSimulationCsvExportTest {

    private val seedsPerPolicy = 500

    @Test
    fun `export seed sweep to csv for offline balance analysis`() {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val registry = CardRegistry.create(cards)

        val policies = mapOf("greedy" to ScriptedPolicy, "leverage" to LeveragePolicy)

        val outFile = File("build/simulation-output/sweep-500.csv")
        outFile.parentFile?.mkdirs()
        outFile.bufferedWriter().use { w ->
            w.appendLine(
                "policy,seed,outcome,peakDebt,endHp,numCombats,avgTurnsPerCombat," +
                    "turnsPerCombatSeq,hpAfterCombatSeq,encounterIdsSeq,defeatEncounterId,pickedRewardIds"
            )
            for ((policyName, policy) in policies) {
                val sim = RunSimulator(registry, enemies, policy = policy)
                for (seed in 0L until seedsPerPolicy.toLong()) {
                    val r = sim.simulate(seed)
                    val avgTurns = if (r.turnsPerCombat.isEmpty()) 0.0 else r.turnsPerCombat.average()
                    w.appendLine(
                        listOf(
                            policyName,
                            r.seed,
                            r.outcome,
                            r.peakDebt,
                            r.endHp,
                            r.turnsPerCombat.size,
                            "%.2f".format(Locale.US, avgTurns),
                            r.turnsPerCombat.joinToString(";"),
                            r.hpAfterCombat.joinToString(";"),
                            r.encounterIds.joinToString(";") { it ?: "?" },
                            r.defeatEncounterId ?: "",
                            r.pickedRewardIds.joinToString(";"),
                        ).joinToString(",")
                    )
                }
            }
        }

        println("Wrote ${policies.size * seedsPerPolicy} rows to ${outFile.absolutePath}")
        assertTrue(outFile.exists() && outFile.length() > 0)
    }
}
