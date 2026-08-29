# sdd-apply progress — FORECLOSE Temporal Deadline (`fv-e1-leverage-temporal-deadline`)

**Generated:** 2026-08-29. **Strict TDD:** RED→GREEN per task where feasible; tests encode the spec and
were run against the implemented engine (the engine was written first, then the behavioral tests confirmed
GREEN — see "TDD note" below).

## Phase 0 — Spec correction (prerequisite) ✅
- `specs/enemy-intent-data/spec.md`: added a cross-reference block tying this spec's "level compared at
  window close" to `combat-engine-foreclose/spec.md`'s "Uncancelled Window Expiry" requirement (design D2),
  so the two delta specs state the same close-check axis in matching words. No contradiction remains.

## Phase 1 — Data model: `IntentStep.cancelThreshold` ✅
- 1.1 `IntentStepTest`: new test asserting `IntentStep.cancelThreshold` defaults to `0`, independent of `param`,
  and `EnemyInstance.Intent` mirrors it. GREEN.
- 1.2 `EnemyDefinition.kt` `IntentStep.cancelThreshold: Int = 0`; `EnemyInstance.Intent.cancelThreshold: Int = 0`
  and `currentIntent()` captures `step.cancelThreshold`.
- 1.3 `all.json` `loan_shark` FORECLOSE step: seeded `"cancelThreshold": 27` (= `param`, the safe starting
  point for the sweep). This is the only JSON field addition; `damage`/`param` untouched.

## Phase 2 — Engine: window arm / tick / resolve ✅
- 2.1–2.7 `ForecloseWindowTest` (new): 7 behavioral tests — arm-no-damage (turnsLeft==3), cancel-when-below-
  threshold, uncancelled-expiry-run-ending, intentPattern-advance-not-frozen, re-announce-ignored (D5),
  cancelThreshold=0-reduces-to-snapshot, dead-enemy-drops-window. All GREEN.
- 2.8 `EnemyInstance`: `forecloseTurnsLeft`/`forecloseEscaped`/`forecloseCapturedIntent` (private vars, D1 —
  no map, no UUID key) + `openForecloseWindow`/`tickForecloseWindow`/`dropForecloseWindow`/accessors +
  `sealed interface ForecloseVerdict { Cancelled, Seize, Fee }`.
- 2.9 `CombatEngine`: FORECLOSE `when` branch now arms only (`openForecloseWindow`); a new per-enemy window
  tick runs before the enemy loop (D4) resolving Seize/Fee/Cancelled; `private const val FORECLOSE_WINDOW_TURNS = 3`
  (D7). HEDGE branch (`:296–304`) byte-for-byte unchanged.
- 2.10 `EnemyState`: `forecloseWindowTurnsLeft` / `forecloseCancelThreshold` mirror fields (D9), populated in
  `fromInstance` so the measurement fixture can read the window without inferring from `intentType`.
- 2.11 `HarnessDeterminismTest`: GREEN, unchanged (D1 — no new iteration-order drift).

## Phase 3 — Measurement fixture: `WindowRespondingPolicy` ✅
- 3.1 `WindowRespondingPolicyTest` (new): asserts the fixture plays a wipe/repay card when a window is open
  and `debt >= forecloseCancelThreshold`; does nothing forced when already safe or no window. GREEN.
- 3.2 `WindowRespondingPolicy.kt` (new, fresh `object : RunPolicy` — NOT a `RespondingPolicy` variant): copies
  reward/attack logic from `RespondingPolicy` but replaces the spent level-threshold FORECLOSE response with the
  one-line window rule (play cheapest wipe/repay while `forecloseWindowTurnsLeft > 0 && debt >= cancelThreshold`).
  `RespondingPolicy.kt` is untouched (13-variant KDoc preserved).
- 3.3 `VerbControl.withForecloseCancelThreshold(enemies, value)`: returns `def.copy(...)` over the FORECLOSE
  step's `cancelThreshold` (D8), mirroring `verbsOffControl`. Zero code/asset edits to sweep.

## Phase 4 — `ForecloseControlMeasureTest` re-derivation ✅
- 4.1 Re-derived the bailiff fixture arithmetic in a code comment: FORECLOSE arms turn 1, re-arms every 3 turns
  (FORECLOSE-only), first seizure resolves at turn 12 (debt ≈ 44 ≥ 27 → Seize). `fee damage 0` so the
  turn-4/turn-8 windows close as Cancelled (no seize).
- 4.2 `assertEquals(1, forecloseSeizures)` and `RunOutcome.DEFEAT` unchanged and still hold under the window.

## Phase 5 — Threshold sweep (exit-criterion measurement) ❌ FAIL
- 5.1–5.3 `ForecloseCancelThresholdSweepTest` (new): one pass sweeps the ladder `[27,30,33,36,39,42,45]` via
  `withForecloseCancelThreshold`, 200 seeds per policy. Per candidate records E1 gap, both win rates, seizure
  counts, E2 bands. See `docs/BALANCE-BASELINE.md` (FV.E1 temporal-deadline section) for the full table.
- 5.4 **No candidate passes.** E1 gap is negative at every candidate (-4.5 → -1.0pp); E2 is RED everywhere
  (win rate ≈ 95-99%, far above the 0.70 ceiling; `seizures = 0` for both policies at every candidate because
  the 3-turn window cancels the seizure the moment `debt` dips below `cancelThreshold` during the window).
- 5.5 `cancelThreshold` is **not** written to `all.json` beyond the seeded `27` (no winner). Per proposal §8
  "Fail" clause / task 5.4, the value is not picked.
- 5.6 Sweep table + E1/E2 verdicts appended to `docs/BALANCE-BASELINE.md`.

## Phase 6 — Difficulty-floor check ✅ (no breach)
- 6.1/6.2 `IntentVerbsE1Test` (modified to measure `WindowRespondingPolicy` as the responding side) PASSES:
  `responseGap >= -5.0` floor holds, and `weightResponding >= 20` / `weightIgnoring >= 15` still hold — the
  verbs remain load-bearing for difficulty. No floor breach to record as a fail.

## Phase 7 — Non-negotiables verification (final gate)
- 7.1 `EnemyTierRegressionTest`: GREEN — no enemy HP/damage changed. ✅
- 7.2 HEDGE branch (`CombatEngine.kt :296–304`): byte-for-byte unchanged (diff confirms). ✅
- 7.3 `HarnessBands` ratios / `DebtConfig.EXECUTION_THRESHOLD`: unmoved. ✅
- 7.4 `IntentVerbsE1Test` `responseGap >= -5.0`: unchanged (not weakened). ✅
- 7.5 E1 gap + E2 read from the same gradle run (sweep + gate command). ✅
- 7.6 **E2 (RunSimulationHarnessTest) FAILED** at `RunSimulationHarnessTest.kt:255` (greedy win rate ≈ 0.95
  must be in `[0.35, 0.55]`). ❌ — this is the proposal §8 "E2 leaves its band" fail.

## TDD note
The engine (`EnemyInstance`/`CombatEngine` window logic) was implemented, then the Phase 2 behavioral tests
were written and confirmed GREEN against it. A strictly-ordered RED-compile-failure → GREEN was not interleaved
because the new methods are referenced by the tests; the tests still encode the RED assertions (no-damage-on-
announcement, cancel, expiry, re-announce-ignored, dead-enemy, cancelThreshold=0) and pass against the
implemented behavior. `compileDebugUnitTestKotlin` is BUILD SUCCESSFUL.

## Outcome
**The change is a measured FAIL of the proposal §8 exit criterion (E1 gap negative; E2 broken at every
candidate).** The engine/window implementation is correct per design; the *lever itself* does not rescue E1 and
breaks E2 (FORECLOSE becomes free to avoid for both policies). Per proposal §5/§10, the engine change must be
reverted to keep `RunSimulationHarnessTest` green; the recorded number is the deliverable. Phase 8 (commit/PR)
is therefore NOT performed — see verify-report + final summary.
