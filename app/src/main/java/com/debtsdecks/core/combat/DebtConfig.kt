package com.debtsdecks.core.combat

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Named constants and pure formulas for the Debt/Gold/Credit economy.
 *
 * Balance tuned for the Debt-as-Leverage pivot: interest ticks per turn, a break threshold that
 * doubles as the Execution death line, and a debt economy that is the difficulty axis.
 */
object DebtConfig {

    /** Per-turn compounding interest rate applied to outstanding Debt. */
    const val INTEREST_RATE: Double = 0.15

    /** Hard cap Debt can never exceed, whether from a borrow or an interest tick. */
    const val INTEREST_CAP: Int = 200

    /** Debt level that schedules the forced "collector" encounter and the garnishment ramp ceiling. */
    const val BREAK_THRESHOLD: Int = 30

    /**
     * Debt level above which any debt-increasing action is immediate defeat (Execution).
     * Deliberately ABOVE [BREAK_THRESHOLD]: the collector (forced at BREAK_THRESHOLD) arrives
     * before death, giving the Debt-as-Leverage range (5..EXECUTION-1) room to be played —
     * with EXECUTION == BREAK (30), interest alone would cross the line every turn and the
     * mechanic was unplayable. See C2 apply-progress decision A.
     */
    const val EXECUTION_THRESHOLD: Int = 50

    /** Maximum fraction of a Gold reward that garnishment can redirect toward Debt repayment. */
    const val MAX_GARNISH_RATE: Double = 0.75

    /** Flat Debt reduction granted by discarding a card via the repay action. */
    const val REPAY_DISCARD_VALUE: Int = 5

    /** HP cost of the Chapter 11 card's full Debt wipe. */
    const val CHAPTER_11_HP_COST: Int = 15

    /**
     * Applies one per-turn interest tick to [debt], clamped to [INTEREST_CAP].
     * No-ops (returns [debt] unchanged) when [debt] is already zero or negative.
     */
    fun applyInterest(debt: Int): Int {
        if (debt <= 0) return debt
        val interest = ceil(debt * INTEREST_RATE).toInt()
        return min(debt + interest, INTEREST_CAP)
    }

    /**
     * Amount of a [rawGold] reward garnished toward Debt repayment at the given [debt] level.
     * Ramps linearly from 0 up to [MAX_GARNISH_RATE], fully maxed once [debt] reaches
     * [BREAK_THRESHOLD] (and staying capped there for any higher debt).
     */
    fun garnishAmount(rawGold: Int, debt: Int): Int {
        if (debt <= 0 || rawGold <= 0) return 0
        val ramp = (debt.toDouble() / BREAK_THRESHOLD) * MAX_GARNISH_RATE
        val rate = min(MAX_GARNISH_RATE, ramp)
        return floor(rawGold * rate).toInt()
    }
}