# F1 — Design

## Shape

One new file in the test source set:

```kotlin
// app/src/test/java/com/debtsdecks/core/simulation/HarnessBands.kt
/**
 * Scale-free balance bands. Every debt threshold here is a FRACTION OF
 * [DebtConfig.EXECUTION_THRESHOLD] — the death line — because that is the only debt number
 * the design guarantees to keep meaning the same thing across a re-scale.
 *
 * At EXECUTION_THRESHOLD = 50 these resolve to the historical absolutes: 25.0, 45.0, 35, 25.
 * If they ever stop doing so at 50, this refactor was wrong.
 */
object HarnessBands {
    private val execution: Double get() = DebtConfig.EXECUTION_THRESHOLD.toDouble()

    const val LEVERAGE_BAND_LOW_RATIO  = 0.50   // was 25
    const val LEVERAGE_BAND_HIGH_RATIO = 0.90   // was 45
    const val WON_PEAK_MIN_RATIO       = 0.50   // was 25
    const val LEVERAGE_TARGET_RATIO    = 0.70   // was LeveragePolicy 35
    const val SAFE_AFTER_LOAN_RATIO    = 0.90   // was NodePolicy 45
    const val REPAY_BAND_RATIO         = 0.50   // was NodePolicy 25

    val leverageBandLow  get() = LEVERAGE_BAND_LOW_RATIO  * execution
    val leverageBandHigh get() = LEVERAGE_BAND_HIGH_RATIO * execution
    val wonPeakMin       get() = WON_PEAK_MIN_RATIO       * execution
    val leverageTarget   get() = (LEVERAGE_TARGET_RATIO   * execution).toInt()
    val safeAfterLoan    get() = (SAFE_AFTER_LOAN_RATIO   * execution).toInt()
    val repayBand        get() = (REPAY_BAND_RATIO        * execution).toInt()

    fun ratioOfExecution(debt: Double) = debt / execution
}
```

`get()` rather than `val` at construction: object initialisers freeze at class-load, and a
spike that mutates `EXECUTION_THRESHOLD` (scenario R1.3) needs the derivation to be live.

## Why `EXECUTION_THRESHOLD` is the anchor and `BREAK_THRESHOLD` is not

Three debt constants could serve: `STARTING_DEBT` (6), `BREAK_THRESHOLD` (30),
`EXECUTION_THRESHOLD` (50).

`EXECUTION_THRESHOLD` is the death line. It is the one number whose *meaning* is guaranteed
to survive any re-scale, because a game with debt-as-death always has one. `BREAK_THRESHOLD`
is a tuning knob — it exists to make the collector arrive before death, and F3 could
plausibly delete it. Anchoring the gate to a knob that might be removed is how you get a
second normalization phase.

Worth noticing what the ratios then say out loud: the band is `[0.50, 0.90)` and the
collector fires at `0.60`. So "playing the band" means *routinely crossing the line that
summons the collector, and not going to the wire*. That was always what the numbers meant;
it was just not legible as `[25, 45)`.

## Rounding

`leverageTarget`, `safeAfterLoan` and `repayBand` are `Int` because the policies compare
against integer debt. `toInt()` truncates: at 50 they give exactly 35, 45, 25. **The
zero-delta acceptance check is what proves the truncation is harmless** — not the argument in
this paragraph.

The two band bounds and the won-peak floor stay `Double`: they are compared against an
average, and rounding an average's bound would change results at the third seed.

## Gold: deferred, and why that is not laziness

`NodePolicy.LOAN_GOLD_NEED = 20` means "the simulated player has enough gold that the loan is
not urgent". There is no gold constant in the codebase that this is naturally a fraction of.
`BUY_BASE` (8) and `REMOVE_BASE` (10) are per-action costs that then get multiplied by
`NodeConfig.ESCALATION` per node, so any ratio against them is a ratio against a moving
target.

Two bad options were available: leave it and say nothing, or invent `2.5 × BUY_BASE` to look
complete. The third option is to name it as an open input to F3, where the gold economy is
actually being touched and the anchor can be chosen from the design instead of from
arithmetic. That is what this change does.

## Ordering with FV

F1 anchors ratios to today's calibrated numbers. FV adds three intent verbs, which will move
the win rate and probably the debt band. If F1 lands first and FV then re-baselines, the
ratios are still *structurally* right — they are ratios, that is the point — but the specific
values `0.50 / 0.90 / 0.70` were chosen to reproduce a baseline that no longer exists.

So: **FV deliverable 1 first, then re-read the sweep, then F1 with whatever ratios reproduce
the post-FV numbers exactly.** F1's zero-delta property is defined against the branch it
forks from, not against today.

## Test plan (TDD order)

1. **RED** — a test asserting `HarnessBands.leverageBandLow == 25.0`, `leverageBandHigh ==
   45.0`, `wonPeakMin == 25.0`, `leverageTarget == 35`, `safeAfterLoan == 45`,
   `repayBand == 25` at the current `EXECUTION_THRESHOLD`. Fails: the object does not exist.
2. **GREEN** — write `HarnessBands`.
3. **REFACTOR** — repoint the harness assertions, `LeveragePolicy`, `NodePolicy`.
4. **GATE** — run the full sweep, diff the printed report against the pre-change capture.
   Identical, or the change is rejected.
5. **SCALE PROOF** — a test that resolves the bands against a stubbed execution line of 100
   and asserts they land on 50/90/70/90/50, proving the derivation is live rather than
   frozen at class-load.

Step 5 is the one that would have caught the bug this whole change exists to prevent.
