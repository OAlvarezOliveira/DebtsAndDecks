package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FV criterion E1 — the verbs are load-bearing: a policy that ignores FORECLOSE/HEDGE must
 * measurably lose to one that responds to them. Over 200 seeds per policy, the gap must sit at
 * least 10pp in the responding policy's favour. If the gap is noise, the verb is decoration.
 */
class IntentVerbsE1Test {

    @Test
    fun `responding to FORECLOSE and HEDGE beats ignoring them by at least ten points`() {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val registry = CardRegistry.create(cards)

        val responding = (0L until 200L).map { RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(it) }
        val ignoring = (0L until 200L).map { RunSimulator(registry, enemies, policy = LeveragePolicy).simulate(it) }

        val respondingReport = SimulationReport.from(responding)
        val ignoringReport = SimulationReport.from(ignoring)

        println()
        println("=== FV E1: responding vs ignoring FORECLOSE/HEDGE (200 seeds each) ===")
        println("Responding -> win ${"%.1f".format(respondingReport.winRate * 100)}% | peak debt ${"%.1f".format(respondingReport.avgPeakDebt)} | HP@win ${"%.1f".format(respondingReport.avgHpAtVictory)}")
        println("Ignoring   -> win ${"%.1f".format(ignoringReport.winRate * 100)}% | peak debt ${"%.1f".format(ignoringReport.avgPeakDebt)} | HP@win ${"%.1f".format(ignoringReport.avgHpAtVictory)}")
        println("Defeats responding: ${respondingReport.defeatsByEncounter}")
        println("Defeats ignoring:   ${ignoringReport.defeatsByEncounter}")
        val gap = (respondingReport.winRate - ignoringReport.winRate) * 100
        println("Gap: ${"%.1f".format(gap)}pp")

        assertTrue(
            gap >= 10.0,
            "ignoring the verbs must cost at least 10pp (responding ${"%.1f".format(respondingReport.winRate * 100)}% vs " +
                "ignoring ${"%.1f".format(ignoringReport.winRate * 100)}%) — if this is noise, the verb is decoration"
        )
    }
}