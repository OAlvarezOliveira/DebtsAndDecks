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

    /** Debt the run starts with (design D: debt is NOT optional — the title demands it).
     *  The interest tick and garnishment thus pressure every run from turn 1; a defensive
     *  zero-debt walk is no longer free. Calibrated by the sim harness sweep. */
    const val STARTING_DEBT: Int = 6

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
    const val MAX_GARNISH_RATE: Double = 0.6


    // --- C4 leverage-payoff-cards constants ---

    /** Extra damage per hit for `debt_scaling` ATTACK cards: floor(debt / N), ON TOP of the
     *  unconditional flat Leverage bonus (floor(debt / 5)) every attack already gets. Tagged
     *  attacks thus double-dip by design (flat /5 + tag /10). */
    const val DEBT_SCALING_ATTACK_DIVISOR: Int = 8

    /** Flat leverage on ALL attacks: +floor(debt / N) per hit (the pivot's unconditional bonus). */
    const val LEVERAGE_DIVISOR: Int = 6

    /** Divisor for `debt_payoff` cards (ATTACK damage or SKILL Block) = floor(debt / N).
     *  Deliberately NO wipe and NO repayment: the "keep the band" sibling of the all-in
     *  `execution_damage` wipe. */
    const val DEBT_PAYOFF_DIVISOR: Int = 2

    /**
     * Divisor for `execution_damage` damage = floor(debt / N), paired WITH the full wipe.
     *
     * The wipe is the reward, so the damage cannot also be the maximum: at 1:1 the card dealt the
     * whole Debt AND cleared it, which strictly dominated its designed counterweight
     * ([DEBT_PAYOFF_DIVISOR], same divisor but NO wipe) and inverted the Debt axis — parking at
     * [EXECUTION_THRESHOLD] - 1 became optimal because interest compounded into free damage.
     * Sharing the divisor with `debt_payoff` makes the trade explicit: identical raw damage, but
     * `debt_payoff` adds the flat leverage bonus and keeps the engine hot, while this one resets
     * the pressure and exhausts.
     */
    const val EXECUTION_DAMAGE_DIVISOR: Int = 2

    /** Divisor for `debt_draw` cards: draw = [DEBT_DRAW_BASE] + floor(debt / N). */
    const val DEBT_DRAW_DIVISOR: Int = 10

    /** Base draw for `debt_draw` cards at zero/low Debt. */
    const val DEBT_DRAW_BASE: Int = 1

    /** HP cost of the Chapter 11 card's full Debt wipe. */
    const val CHAPTER_11_HP_COST: Int = 15

    /**
     * Applies one per-turn interest tick to [debt], clamped to [INTEREST_CAP].
     * No-ops (returns [debt] unchanged) when [debt] is already zero or negative.
     */
    // --- Archetype-strategy-rework (WU1): synergy tier + leverage band-cap constants ---
    // These are the design's LOCKED values; the band cap and diminishing divisor are validated
    // against the headless sim harness (Engram #1405) before merge.

    /** Tags of one archetype that advance its synergy tier. Every [ARCHETYPE_TIER_TAGS_PER_TIER]
     *  economy-tagged cards of an archetype in the deck raises that archetype's tier by 1
     *  (capped at [ARCHETYPE_TIER_MAX]). Thresholds fall out as 2/4/6 cards -> tier 1/2/3. */
    const val ARCHETYPE_TIER_TAGS_PER_TIER: Int = 2

    /** Maximum synergy tier any archetype can reach. */
    const val ARCHETYPE_TIER_MAX: Int = 3

    /** Debt level where LEVERAGE payoff enters diminishing returns (the band cap). Parking at
     *  EXECUTION-1 yields less incremental power than playing in-band, killing the exploit. */
    const val LEVERAGE_PAYOFF_BAND_CAP: Int = 40

    /** Divisor M applied to Debt above [LEVERAGE_PAYOFF_BAND_CAP] in the band-capped payoff
     *  (`floor(40 / N) + floor((debt - 40) / M)`). */
    const val LEVERAGE_PAYOFF_DIMINISHING_DIVISOR: Int = 5

    /** Named constant replacing the hardcoded `/10` in the `debt_scaling` SKILL strength path
     *  (CardResolver line 188). Value preserved at 10 so current behavior is unchanged; the repo
     *  rule is "named constant, not magic number" (see leverage-archetype spec).
     *  NOTE: the ATTACK `debt_scaling` path keeps its own [DEBT_SCALING_ATTACK_DIVISOR] (8) — a
     *  distinct path deliberately left untouched by WU1 to avoid changing attack numbers. */
    const val DEBT_STRENGTH_DIVISOR: Int = 10

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

    /**
     * Band-capped LEVERAGE payoff component of a `debt_payoff` card:
     * `floor(min(debt, [LEVERAGE_PAYOFF_BAND_CAP]) / [DEBT_PAYOFF_DIVISOR])`.
     *
     * Above the band cap the marginal payoff is frozen, so parking Debt at EXECUTION-1 (49) yields
     * the SAME payoff component as sitting exactly at the cap (40) — the WU2 exploit guard against
     * the "keep the band" over-leverage loop. The [LEVERAGE_PAYOFF_DIMINISHING_DIVISOR] constant is
     * the tuning surface for a softer diminishing curve; the locked band-cap decision uses the hard
     * freeze (orchestrator-specified) to make the EXECUTION-1 equality exact rather than merely
     * reduced.
     */
    fun leveragePayoffBandCapped(debt: Int): Int =
        min(debt, LEVERAGE_PAYOFF_BAND_CAP) / DEBT_PAYOFF_DIVISOR
}