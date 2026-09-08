# Delta for Debt Economy

No `openspec/specs/debt-economy/spec.md` baseline exists yet, so this delta is
written as ADDED (documenting current-code behavior being superseded, since
there is no prior spec text to copy and edit).

## ADDED Requirements

### Requirement: Split Constant — Arrears Threshold vs Debt Scale Anchor
The system MUST split the single prior `EXECUTION_THRESHOLD` constant into two
named constants instead of a blanket rename:
- `DebtConfig.ARREARS_THRESHOLD: Int = 40` — the behavioral value that arms
  the `arrears-lock` state (see `arrears-lock` spec). Call sites: the lock-arming
  check in `CombatEngine.addDebt`, the Gatillo B outcome check, `RunObservationTest`'s
  defeat classification, and `CombatRenderer`'s red-debt visual threshold.
- `DebtConfig.DEBT_SCALE_ANCHOR: Int = 50` — a rename of the old
  `EXECUTION_THRESHOLD`, value unchanged at 50. Call sites: `HarnessBands.kt`'s
  ratio-derivation anchor for all E2 bands, the borrow ceiling read by
  `ScriptedPolicy` and `LeveragePolicy` (which MUST remain blind to the lock
  and MUST NOT switch to reading `ARREARS_THRESHOLD`), and `RunManager.kt`'s
  loan-affordability guard (node loans never route through `addDebt`, so they
  cannot arm the lock and stay gated on the harness-scale number).

The system MUST replace instant-defeat-on-threshold behavior with arming the
`arrears-lock` state when `state.debt` crosses `ARREARS_THRESHOLD`.

(Supersedes: prior undocumented behavior where crossing the single
`EXECUTION_THRESHOLD` (50) ended combat immediately as a defeat. Also
supersedes this spec's own earlier "every reference MUST use 40" blanket
requirement, which would have silently moved `avgPeakDebt` out of its
required `[25,45)` band by re-deriving `HarnessBands.kt` off 40 instead of 50.)

#### Scenario: Crossing ARREARS_THRESHOLD does not end combat
- GIVEN `state.debt` is below `ARREARS_THRESHOLD` (40)
- WHEN a debt-increasing effect pushes `state.debt >= ARREARS_THRESHOLD`
- THEN combat MUST NOT end immediately
- AND the `arrears-lock` entry requirement arms instead

#### Scenario: Behavioral call sites use ARREARS_THRESHOLD, harness call sites use DEBT_SCALE_ANCHOR
- GIVEN the codebase and tests
- WHEN `CombatEngine.addDebt`'s lock-arming check, the Gatillo B check,
  `RunObservationTest`'s defeat classification, or `CombatRenderer`'s
  red-debt threshold reference the constant
- THEN they MUST reference `ARREARS_THRESHOLD` (40)
- AND WHEN `HarnessBands.kt`, `ScriptedPolicy.kt`, `LeveragePolicy.kt`, or
  `RunManager.kt`'s loan-affordability guard reference the constant
- THEN they MUST reference `DEBT_SCALE_ANCHOR` (50)

#### Scenario: E2 harness bands stay unchanged from historical baseline
- GIVEN `HarnessBands.kt` derives every E2 band as a ratio of
  `DEBT_SCALE_ANCHOR` (unchanged at 50)
- WHEN `RunSimulationHarnessTest` runs
- THEN `avgPeakDebt` MUST fall in `[25,45)`
- AND every other E2 band absolute (leverageTarget, safeAfterLoan, repayBand,
  wonPeakMin — historically 25.0, 45.0, 35, 45, 25 per `HarnessBands.kt`
  and `docs/BALANCE-BASELINE.md`) MUST remain unchanged
