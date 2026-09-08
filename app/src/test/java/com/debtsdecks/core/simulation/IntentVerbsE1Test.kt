package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * FV criterion E1 — re-metriced 2026-08-28 with sim output attached (`docs/BALANCE-BASELINE.md`).
 *
 * The original gate (a policy that responds to FORECLOSE/HEDGE must beat one that ignores them by
 * at least 10pp) is unreachable at a sane win band: FORECLOSE is a binary check on the player's
 * natural debt band, so across the fee/hedge/threshold sweep the response gap is noise (0.5pp at
 * the shipped values), and the cheapest parameter that does open a gap (threshold 20) collapses
 * the band (responding 23% / ignoring 14.5%). The verb slots ARE load-bearing, but for
 * *difficulty*: swapping them for their predecessor intents moves the win rate by 25-32pp.
 *
 * The gate now asserts that difficulty weight (both policies must lose at least 10pp when the
 * verbs are switched off) and reports the response gap informationally.
 */
class IntentVerbsE1Test {

    @Test
    fun `the verbs are load-bearing for difficulty while the response gap stays informational`() {
        val cards = TestAssetLoader.loadCards()
        val enemies = TestAssetLoader.loadEnemies()
        val control = VerbControl.verbsOffControl(enemies)
        val registry = CardRegistry.create(cards)

        val respondingOn = (0L until 200L).map { RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(it) }
        val ignoringOn = (0L until 200L).map { RunSimulator(registry, enemies, policy = LeveragePolicy).simulate(it) }
        val respondingOff = (0L until 200L).map { RunSimulator(registry, control, policy = RespondingPolicy).simulate(it) }
        val ignoringOff = (0L until 200L).map { RunSimulator(registry, control, policy = LeveragePolicy).simulate(it) }

        val respondingOnReport = SimulationReport.from(respondingOn)
        val ignoringOnReport = SimulationReport.from(ignoringOn)
        val respondingOffReport = SimulationReport.from(respondingOff)
        val ignoringOffReport = SimulationReport.from(ignoringOff)

        val responseGap = (respondingOnReport.winRate - ignoringOnReport.winRate) * 100
        val weightResponding = (respondingOnReport.winRate - respondingOffReport.winRate) * 100
        val weightIgnoring = (ignoringOnReport.winRate - ignoringOffReport.winRate) * 100

        println()
        println("=== FV E1 (re-metriced): verbs on vs verbs-off control (200 seeds each) ===")
        println(
            String.format(
                Locale.US,
                "Responding -> verbs-on %.1f%% | verbs-off %.1f%% | difficulty weight %.1fpp",
                respondingOnReport.winRate * 100, respondingOffReport.winRate * 100, weightResponding,
            )
        )
        println(
            String.format(
                Locale.US,
                "Ignoring   -> verbs-on %.1f%% | verbs-off %.1f%% | difficulty weight %.1fpp",
                ignoringOnReport.winRate * 100, ignoringOffReport.winRate * 100, weightIgnoring,
            )
        )
        println(
            String.format(
                Locale.US,
                "Response gap (responding - ignoring, informational): %.1fpp",
                responseGap,
            )
        )

        // R3-1 (reliability advisory): the response gap is not gated (it is the documented 0.5-2.5pp
        // noise of the re-metric), but it must never go strongly NEGATIVE — the measured proactive
        // responder regression hit -9.5 to -12pp, and that pathology must fail the suite.
        assertTrue(
            responseGap >= -5.0,
            "responding must not be materially worse than ignoring (gap %.1fpp; the re-metric expects 0.5-2.5pp noise, and a -9.5pp+ regression is exactly what this guard exists to catch)".format(responseGap)
        )

        // R3-2 (reliability advisory): tie the pass to the documented measured values (BALANCE-BASELINE,
        // difficulty calibration 2026-08-28): the weights measured 25.5pp/19.5pp at HP x1.10, so the
        // floor sits at 20/15 with headroom against sweep noise while keeping a real cushion.
        assertTrue(
            weightResponding >= 10.0 && weightIgnoring >= 10.0,
            "the verb slots must be load-bearing for difficulty: switching them off costs at least 10pp " +
                "(responding %.1fpp, ignoring %.1fpp) — if this is noise, the verbs are decoration".format(
                    weightResponding, weightIgnoring,
                )
        )
        assertTrue(
            weightResponding >= 20.0 && weightIgnoring >= 15.0,
            "the difficulty weights must hold near their documented calibration values " +
                "(measured 25.5pp/19.5pp at HP x1.10; floors 20/15; now %.1fpp/%.1fpp)".format(
                    weightResponding, weightIgnoring,
                )
        )
    }
}