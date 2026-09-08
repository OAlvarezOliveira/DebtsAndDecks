package com.debtsdecks.core.simulation

/**
 * Pure aggregator over [List][SimulationResult]: folds per-seed results into aggregate balance
 * stats for the seed sweep report. No simulation logic here — testable with hand-built fixtures.
 */
data class SimulationReport(
    val winRate: Double,
    val avgPeakDebt: Double,
    val avgHpAtVictory: Double,
    val avgTurnsPerCombat: Double,
    val defeatsByEncounter: Map<String, Int>,
    /** FV.E1 instrumentation: total arrears-lock arms summed across every run in the sweep
     *  (mirrors [SimulationResult.arrearsArmed]'s per-run accumulation pattern). */
    val totalArrearsArmed: Int = 0,
    /** Fraction of runs in the sweep that armed the lock at least once — the "fire-rate > 0
     *  per policy" signal required by the arrears-lock spec's Empirical Balance Validation. */
    val arrearsFireRate: Double = 0.0,
) {
    companion object {
        fun from(results: List<SimulationResult>): SimulationReport {
            require(results.isNotEmpty()) { "report requires at least one simulated run" }
            val victories = results.filter { it.outcome == RunOutcome.VICTORY }
            val winRate = victories.size.toDouble() / results.size
            val avgPeakDebt = results.map { it.peakDebt }.average()
            // Only winning runs have meaningful "HP at victory"; losing runs end at 0 HP.
            val avgHpAtVictory = if (victories.isEmpty()) 0.0 else victories.map { it.endHp }.average()
            val allCombats = results.flatMap { it.turnsPerCombat }
            val avgTurnsPerCombat = if (allCombats.isEmpty()) 0.0 else allCombats.average()
            val defeatsByEncounter = results
                .filter { it.outcome == RunOutcome.DEFEAT }
                .mapNotNull { it.defeatEncounterId }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()
            val totalArrearsArmed = results.sumOf { it.arrearsArmed }
            val arrearsFireRate = results.count { it.arrearsArmed > 0 }.toDouble() / results.size
            return SimulationReport(
                winRate, avgPeakDebt, avgHpAtVictory, avgTurnsPerCombat, defeatsByEncounter,
                totalArrearsArmed, arrearsFireRate,
            )
        }
    }

    /** F1 R1.4: avgPeakDebt as a fraction of the execution line — readable at any scale. */
    val peakDebtRatio: Double get() = HarnessBands.ratioOfExecution(avgPeakDebt)

    fun summary(): String = buildString {
        appendLine("=== Run Simulation Sweep Report ===")
        appendLine("Win rate:          ${(winRate * 100).let { "%.1f".format(it) }}%")
        appendLine("Avg peak Debt:     %.1f".format(avgPeakDebt))
        appendLine("  as fraction:     %.3f of execution line".format(peakDebtRatio))
        appendLine("Avg HP at victory: %.1f".format(avgHpAtVictory))
        appendLine("Avg turns/combat:  %.1f".format(avgTurnsPerCombat))
        appendLine("Arrears armed:     $totalArrearsArmed total | fire rate ${(arrearsFireRate * 100).let { "%.1f".format(it) }}%")
        appendLine("Defeats by encounter:")
        if (defeatsByEncounter.isEmpty()) {
            appendLine("  (none)")
        } else {
            defeatsByEncounter.forEach { (enc, count) -> appendLine("  $enc: $count") }
        }
    }
}