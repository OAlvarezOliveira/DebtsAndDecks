package com.debtsdecks.core.simulation

import com.debtsdecks.core.cards.CardRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The simulation harness is this project's balance gate: every balance decision is argued from its
 * 200-seed report. A gate that answers differently on identical input cannot support that argument,
 * and it makes "this change moved nothing" impossible to demonstrate.
 *
 * [RunSimulationHarnessTest] already asserts one seed reproduces once. That is not enough: the
 * observed drift was a rare tie-break, so it only shows up over repeats and across a full sweep.
 * These tests pin reproducibility where it was actually broken — repeated runs of the same seed, and
 * a whole sweep run twice.
 */
class HarnessDeterminismTest {

    private fun simulator() = RunSimulator(
        CardRegistry.create(TestAssetLoader.loadCards()),
        TestAssetLoader.loadEnemies()
    )

    private fun fingerprint(r: SimulationResult) =
        "${r.outcome}|${r.peakDebt}|${r.endHp}|${r.defeatEncounterId}|${r.pickedRewardIds}"

    @Test
    fun `one seed repeated twenty times on a fresh simulator yields one outcome`() {
        val fingerprints = (1..20).map { fingerprint(simulator().simulate(SEED_WITH_TIE)) }.toSet()

        assertEquals(1, fingerprints.size, "seed $SEED_WITH_TIE produced ${fingerprints.size} distinct outcomes: $fingerprints")
    }

    @Test
    fun `one seed repeated twenty times on a reused simulator yields one outcome`() {
        val sim = simulator()
        val fingerprints = (1..20).map { fingerprint(sim.simulate(SEED_WITH_TIE)) }.toSet()

        assertEquals(1, fingerprints.size, "seed $SEED_WITH_TIE produced ${fingerprints.size} distinct outcomes: $fingerprints")
    }

    @Test
    fun `the full sweep replays identically`() {
        val sim = simulator()
        val first = (0L until 200L).map { fingerprint(sim.simulate(it)) }
        val second = (0L until 200L).map { fingerprint(sim.simulate(it)) }

        val drifted = first.indices.filter { first[it] != second[it] }
        assertEquals(emptyList<Int>(), drifted, "seeds $drifted changed outcome between two identical sweeps")
    }

    private companion object {
        /** Empirically the first seed observed to flip: its greedy line hits a damage-per-cost tie. */
        const val SEED_WITH_TIE = 172L
    }
}
