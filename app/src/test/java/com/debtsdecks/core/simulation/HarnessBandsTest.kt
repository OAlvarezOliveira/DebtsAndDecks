package com.debtsdecks.core.simulation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Scale-proof of [HarnessBands]. Task 1.1: the bands resolve to the expected absolutes at the
 * current `EXECUTION_THRESHOLD` — note leverageTarget is 41 (0.82), the WU7 tuning value, not the
 * pre-WU7 35. Task 1.3: the derivation is LIVE — a stubbed execution line
 * resolves to the expected ratios, which a class-load-frozen object would fail.
 */
class HarnessBandsTest {

    @Test
    fun `bands resolve to the historical absolutes at the current execution threshold`() {
        assertEquals(25.0, HarnessBands.leverageBandLow, 1e-9)
        assertEquals(45.0, HarnessBands.leverageBandHigh, 1e-9)
        assertEquals(25.0, HarnessBands.wonPeakMin, 1e-9)
        assertEquals(41, HarnessBands.leverageTarget)
        assertEquals(45, HarnessBands.safeAfterLoan)
        assertEquals(25, HarnessBands.repayBand)
    }

    @Test
    fun `bands resolve against a stubbed execution line of one hundred`() {
        val bands = HarnessBands.resolve(100)
        assertEquals(50.0, bands.leverageBandLow, 1e-9)
        assertEquals(90.0, bands.leverageBandHigh, 1e-9)
        assertEquals(50.0, bands.wonPeakMin, 1e-9)
        assertEquals(82, bands.leverageTarget)
        assertEquals(90, bands.safeAfterLoan)
        assertEquals(50, bands.repayBand)
    }
}