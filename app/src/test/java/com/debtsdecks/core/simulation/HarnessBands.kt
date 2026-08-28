package com.debtsdecks.core.simulation

import com.debtsdecks.core.combat.DebtConfig

/**
 * Scale-free balance bands. Every debt threshold here is a FRACTION OF
 * [DebtConfig.EXECUTION_THRESHOLD] — the death line — because that is the only debt number
 * the design guarantees to keep meaning the same thing across a re-scale.
 *
 * At EXECUTION_THRESHOLD = 50 these resolve to the historical absolutes: 25.0, 45.0, 35, 45, 25.
 * If they ever stop doing so at 50, this refactor was wrong.
 *
 * The thresholds are derived live ([get] rather than constructor values) so a spike that mutates
 * the execution line (spec R1.3) is picked up on the next access instead of being frozen at
 * class-load. Reasoning for the anchor: the execution line is the death line and every re-scale
 * keeps one; `BREAK_THRESHOLD` is a tuning knob F3 could plausibly delete, so anchoring the gate
 * to it would buy a second normalization phase.
 */
object HarnessBands {

    const val LEVERAGE_BAND_LOW_RATIO  = 0.50   // was 25
    const val LEVERAGE_BAND_HIGH_RATIO = 0.90   // was 45
    const val WON_PEAK_MIN_RATIO       = 0.50   // was 25
    const val LEVERAGE_TARGET_RATIO    = 0.70   // was LeveragePolicy 35
    const val SAFE_AFTER_LOAN_RATIO    = 0.90   // was NodePolicy 45
    const val REPAY_BAND_RATIO         = 0.50   // was NodePolicy 25

    /** One set of debt thresholds resolved against a concrete execution line. */
    data class Bands(
        val leverageBandLow: Double,
        val leverageBandHigh: Double,
        val wonPeakMin: Double,
        val leverageTarget: Int,
        val safeAfterLoan: Int,
        val repayBand: Int,
    )

    /**
     * Pure derivation — the only place a ratio becomes a number. [executionLine] is left explicit
     * so the scale-proof test can resolve against 100 without mutating [DebtConfig].
     */
    fun resolve(executionLine: Int): Bands = Bands(
        leverageBandLow  = LEVERAGE_BAND_LOW_RATIO  * executionLine,
        leverageBandHigh = LEVERAGE_BAND_HIGH_RATIO * executionLine,
        wonPeakMin       = WON_PEAK_MIN_RATIO       * executionLine,
        leverageTarget   = (LEVERAGE_TARGET_RATIO   * executionLine).toInt(),
        safeAfterLoan    = (SAFE_AFTER_LOAN_RATIO   * executionLine).toInt(),
        repayBand        = (REPAY_BAND_RATIO        * executionLine).toInt(),
    )

    /** Debt thresholds against the current execution line, re-resolved on every access. */
    val leverageBandLow: Double  get() = resolve(DebtConfig.EXECUTION_THRESHOLD).leverageBandLow
    val leverageBandHigh: Double get() = resolve(DebtConfig.EXECUTION_THRESHOLD).leverageBandHigh
    val wonPeakMin: Double       get() = resolve(DebtConfig.EXECUTION_THRESHOLD).wonPeakMin
    val leverageTarget: Int      get() = resolve(DebtConfig.EXECUTION_THRESHOLD).leverageTarget
    val safeAfterLoan: Int       get() = resolve(DebtConfig.EXECUTION_THRESHOLD).safeAfterLoan
    val repayBand: Int           get() = resolve(DebtConfig.EXECUTION_THRESHOLD).repayBand

    /** [debt] as a fraction of the current execution line — for readable failures at any scale. */
    fun ratioOfExecution(debt: Double): Double = debt / DebtConfig.EXECUTION_THRESHOLD
}