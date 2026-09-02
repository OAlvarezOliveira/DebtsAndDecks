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

    /**
     * Debt level that opens the "danger" zone on the HUD band bar (the per-turn bleed floor).
     * Below this the bar reads SAFE; at or above it the HUD warns and the risk counter becomes
     * actionable. Distinct from [BREAK_THRESHOLD]: the bleed floor is the first visual danger
     * marker while the collector (forced encounter) triggers at the break. See debt-hud spec.
     */
    const val DEBT_BLEED_FLOOR: Int = 22

    /** Maximum fraction of a Gold reward that garnishment can redirect toward Debt repayment. */
    const val MAX_GARNISH_RATE: Double = 0.6


    // --- C4 leverage-payoff-cards constants ---

    /** Extra damage per hit for `debt_scaling` ATTACK cards: floor(debt / N), ON TOP of the
     *  unconditional flat Leverage bonus (floor(debt / 5)) every attack already gets. Tagged
     *  attacks thus double-dip by design (flat /5 + tag /10). */
    const val DEBT_SCALING_ATTACK_DIVISOR: Int = 8

    /** Flat leverage on ALL attacks: +floor(debt / N) per hit (the pivot's unconditional bonus). */
    const val LEVERAGE_DIVISOR: Int = 6

    /**
     * Divisor for `debt_payoff` cards (ATTACK damage or SKILL Block) = floor(debt / N), where the
     * band cap ([LEVERAGE_PAYOFF_BAND_CAP]) already clamps the numerator to `min(debt, 40)`, so at
     * the current WU7 tuning this is effectively `min(debt, 40)`.
     *
     * WU7 balance lever: `debt_payoff` cards (`asset_bubble` ATTACK, `collateral_hold` SKILL) carry a
     * static `damage: 0` in assets, so the greedy simulation policy — which drafts rewards by the
     * static `damage` field — ranks them last and effectively never drafts them, while the LEVERAGE
     * policy ranks `debt_payoff` as its top draft priority. Their damage is computed from Debt at
     * resolve time, which makes this divisor a LEVERAGE-only lever: unlike [LEVERAGE_DIVISOR] (a
     * global scalar on every attack) or [DEBT_SCALING_ATTACK_DIVISOR] (whose only ATTACK carrier,
     * `leverage_strike`, is also greedy's top draft pick). Set to 1 for WU7.
     */
    const val DEBT_PAYOFF_DIVISOR: Int = 1

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
     *
     * KNOWN DESIGN DEBT (WU7): this constant intentionally stays at 2 while [DEBT_PAYOFF_DIVISOR]
     * moved to 1. That breaks the "shared divisor" pairing above — `debt_payoff` now deals roughly
     * DOUBLE the raw damage of `execution_damage` AND does not wipe, so the `ejecucion` card is now
     * strictly dominated. This is a follow-up to re-derive; do NOT "fix" it here and do NOT change
     * this constant's value.
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

    /**
     * PRESSURE low-debt threshold for the end-of-turn escalator (WU3, T3.5/T3.6): a deck holding
     * `low_debt_bonus` POWER cards grants +1 Strength per stack at end of turn while Debt stays
     * strictly below this value.
     *
     * WU7 re-derivation (T7.6): the T7.4 sweep measured PRESSURE 13.5pp below LEVERAGE because the
     * escalator is dead weight for most of a PRESSURE run. PRESSURE's harness-measured peak Debt is
     * ~31.6, so a threshold pinned to the bleed floor (22) leaves the trigger OFF on the turns
     * PRESSURE actually plays — and PRESSURE has no `debt_payoff` card, so the escalator's
     * compounding Strength is its ONLY damage-scaling identity. Aligning the threshold to PRESSURE's
     * real operating band (~30) lets it fire where PRESSURE lives, restoring T7.4 parity without
     * touching the global [LEVERAGE_DIVISOR] or the LEVERAGE-specific [DEBT_PAYOFF_DIVISOR].
     *
     * This is a PRESSURE-only lever by construction: the only `low_debt_bonus` carrier is a POWER
     * with `damage: 0`, so the greedy policy (drafts by the static damage field) and the LEVERAGE
     * policy (prioritises `debt_payoff`/`debt_scaling`) both rank it last, while the PRESSURE policy
     * ranks any `pressure`-tagged card first.
     *
     * The player-facing card copy quotes this number, so `card.low_debt_escalator.description` in
     * every locale bundle must be updated whenever it changes.
     */
    const val PRESSURE_LOW_DEBT_THRESHOLD: Int = 30

    /**
     * WU7 (T7.6) PRESSURE-tier-damage re-derivation: PRESSURE-tagged attacks gain an extra
     * `floor(debt / N)` damage component, mirroring the debt-scaling identity that makes LEVERAGE
     * attacks hit hard — except PRESSURE has NO `debt_payoff` card (its only ATTACK, `paydown_strike`,
     * repays Debt and so cannot double as a burst), so without this it has no early-game damage
     * identity and loses the DPS race to the collector before its end-of-turn low-debt escalator can
     * compound (measured 13.5pp below LEVERAGE in the T7.4 sweep). The divisor is PRESSURE-specific:
     * only `pressure`-tagged attacks read it, so LEVERAGE and the greedy baseline are untouched.
     * Set to 2 for WU7 — much steeper than [LEVERAGE_DIVISOR] (6) and even than the LEVERAGE
     * `debt_payoff` curve, because PRESSURE's only ATTACK (`paydown_strike`) doubles as a Debt repay
     * and so has low base damage; it needs a steep debt curve to reach T7.4 parity (within 10pp of
     * LEVERAGE in the 200-seed sweep) without overshooting LEVERAGE.
     */
    const val PRESSURE_DEBT_SCALING_DIVISOR: Int = 2

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