# Tasks: "En Mora" Arrears Hard-Lock (FV.E1)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~340-420 (7 prod files, 5 test files, 3 docs; mostly small diffs) |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (baseline + config split) → PR 2 (engine + policies + tests) → PR 3 (render/i18n/docs + sweep) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Pre-change baseline capture + `DebtConfig` constant split (D1) | PR 1 | `gradle --no-daemon :app:testDebugUnitTest --tests "*.DebtConfigTest"` | `IntentVerbsE1Test`, `RunSimulationHarnessTest` (baseline-only run, no assertions changed yet) | Revert `DebtConfig.kt` + baseline doc append; no other file touched |
| 2 | `CombatState`/`CombatEngine` lock arm/exit/freeze + Gatillo B in `RunManager` | PR 2 | `gradle --no-daemon :app:testDebugUnitTest --tests "*.CombatEngineTest" --tests "*.RunManagerTest"` | N/A — unit/integration only, harness re-run happens in Unit 4 | Revert `CombatState.kt`, `CombatEngine.kt`, `RunManager.kt` as one slice |
| 3 | Policy wiring: `RespondingPolicy` lock branch, mechanical rename in `Scripted`/`LeveragePolicy`, `RunSimulator`/`SimulationReport`/`RunObservationTest` | PR 2 or PR 3 | `gradle --no-daemon :app:testDebugUnitTest --tests "*.simulation.*"` | `IntentVerbsE1Test`, `RunSimulationHarnessTest` (full assertions) | Revert `test/simulation/*` + `RunObservationTest.kt` as one slice |
| 4 | Render/i18n/docs + joint diagnostic sweep | PR 3 | `gradle --no-daemon :app:testDebugUnitTest --tests "*.I18nBundleTest"` | Local sweep script (informational, not a unit test) | Revert `CombatRenderer.kt`, `strings*.properties`, docs independently of engine logic |

## Phase 1: Pre-Change Baseline (MUST run before any production code change)

- [x] 1.1 Run `IntentVerbsE1Test` and `RunSimulationHarnessTest` unmodified, 200 seeds each, on current `develop`/branch tip. Record raw response-gap, greedy win rate, `avgPeakDebt`, and per-policy win rate in `docs/BALANCE-BASELINE.md` under a new dated "Pre fv-e1-arrears-lock" section.
- [x] 1.2 Confirm `HarnessDeterminismTest` is green pre-change (control baseline for Phase 5's determinism re-check).

## Phase 2: Config Split (D1)

- [x] 2.1 RED: `core/combat/DebtConfigTest.kt` — add `assertEquals(40, DebtConfig.ARREARS_THRESHOLD)` and `assertEquals(50, DebtConfig.DEBT_SCALE_ANCHOR)`; add an anchor-vs-`BREAK_THRESHOLD` invariant assert. Confirm both fail (constants don't exist yet).
- [x] 2.2 GREEN: `core/combat/DebtConfig.kt` — add `const val ARREARS_THRESHOLD: Int = 40`; rename `EXECUTION_THRESHOLD` → `const val DEBT_SCALE_ANCHOR: Int = 50` (re-KDoc as harness-scale anchor only, no behavioral claim).
- [x] 2.3 Fix every existing `EXECUTION_THRESHOLD` reference in non-test production code to compile against `DEBT_SCALE_ANCHOR` (mechanical rename only — no behavior change yet): `HarnessBands.kt` E2 band derivation, `RunManager.kt` loan-affordability guard (`:273`).
- [x] 2.4 TRIANGULATE: assert `DEBT_SCALE_ANCHOR != ARREARS_THRESHOLD` and both are positive Ints, guarding against future accidental merge of the two constants.
- [x] 2.5 REFACTOR: confirm no remaining `EXECUTION_THRESHOLD` symbol anywhere in `core/`, `gdx/`, `test/` (grep check, zero results expected until Phase 3-5 touch the behavioral call sites).

## Phase 3: Lock State and Engine Logic

- [x] 3.1 RED: `core/model/CombatStateTest.kt` (or nearest existing state test) — assert `CombatState` default-constructs with `inArrears = false`, `arrearsUsedThisCombat = false`.
- [x] 3.2 GREEN: `core/model/CombatState.kt` — append `inArrears: Boolean = false`, `arrearsUsedThisCombat: Boolean = false` (preserve existing constructor call-site compilation).
- [x] 3.3 RED: `core/combat/CombatEngineTest.kt` — write failing cases: debt crosses 40 first time → `inArrears=true`/`arrearsUsedThisCombat=true`/combat continues; debt at 39 → no arm; second crossing after charge spent → stays false; dip 40→10 without hitting 0 → lock stays true; `debt==0` → lock clears; `wipe_debt` while unlocked → charge not consumed. Use `Random(seed)`, concrete expected values.
- [x] 3.4 GREEN: `core/combat/CombatEngine.kt` — implement `armArrearsIfCrossed()` at tail of `addDebt` (replaces `endCombat(false)` return); implement `clearArrearsIfEscaped()` (`debt == 0 ⇒ inArrears = false`) invoked after `RepayDebt`/`WipeDebt`; drop the three defeat paths at `:211,:278,:455`; add `arrearsArmedCount` (Int, private set) incremented on arm; reset both flags + counter in `startCombat`; expose both flags via `getState()`.
- [x] 3.5 GREEN: freeze passive interest — guard the `beginTurn` interest tick (`:361`) with `if (!inArrears)`; leave active card-applied debt increases unguarded.
- [x] 3.6 RED: `core/combat/CombatEngineTest.kt` — interest-frozen-while-locked case: tick fires while `inArrears=true` → `state.debt` unchanged from that tick; active debt-applying card still increases debt.
- [x] 3.7 GREEN: confirm 3.6 passes against 3.5's implementation (should already be green; add the test as a distinct triangulation case, not a new production change).
- [x] 3.8 TRIANGULATE (3.3-3.7): add one more seed/case per RED item confirming no accidental coupling (e.g., arming at exactly 40 via a card that jumps debt 38→42 in one step, not just +1 increments).
- [x] 3.9 REFACTOR: `CombatEngine.kt` — extract `armArrearsIfCrossed`/`clearArrearsIfEscaped` as named private functions per the design's Interfaces section; remove now-dead `levyExecution` defeat-path code.

## Phase 4: Gatillo B — Outcome Resolution

- [x] 4.1 RED: `core/combat/RunManagerTest.kt` — enemy HP reaches 0 with `state.inArrears == true` → resolves `Phase.DEFEAT` (not victory); enemy HP reaches 0 with `inArrears == false` (post-escape) → resolves `Phase.VICTORY`.
- [x] 4.2 GREEN: `core/combat/RunManager.kt` `refresh()` — insert Gatillo B check after the `allEnemiesDead` check, before garnishment (`:177-189`): `allEnemiesDead && state.inArrears ⇒ DEFEAT`.
- [x] 4.3 TRIANGULATE: add a case where `allEnemiesDead` is false and `inArrears` is true (regular ongoing-combat state, not `COMBAT_END`) — confirm Gatillo B does not fire outside `COMBAT_END`.
- [x] 4.4 REFACTOR: confirm `takeLoan`'s affordability guard (unchanged) still reads `DEBT_SCALE_ANCHOR`, not `ARREARS_THRESHOLD` — node loans never route through `addDebt` and must stay ungated by the lock.

## Phase 5: Harness Policy Wiring

- [x] 5.1 GREEN: `test/simulation/RespondingPolicy.kt` — add one new lock branch as the first check in `chooseAction`: if `state.inArrears`, reuse the existing HP-aware cheapest-playable `wipe_debt` selection (`:75-86`); else fall through unchanged to the FORECLOSE branch.
- [x] 5.2 GREEN: `test/simulation/ScriptedPolicy.kt`, `test/simulation/LeveragePolicy.kt` — mechanical rename only (`EXECUTION_THRESHOLD` → `DEBT_SCALE_ANCHOR` at their borrow-ceiling checks, `:75` and `:95`). No new lock branch — policies stay blind by design (D3). Verified already complete: both files already reference `DebtConfig.DEBT_SCALE_ANCHOR` (renamed in the Phase 2 `D1` commit `3680a9d`, which covered these test-source call sites too); `grep -rn EXECUTION_THRESHOLD app/src` returns zero results.
- [x] 5.3 GREEN: `test/simulation/RunSimulator.kt`, `test/simulation/SimulationReport.kt` — thread `arrearsArmed` from `CombatEngine.arrearsArmedCount` into `SimulationResult` and the report output, mirroring the existing `forecloseSeizureCount` pattern. Added `SimulationReport.totalArrearsArmed`/`arrearsFireRate` (aggregated in `.from()`) and one `summary()` line, since `forecloseSeizureCount` itself was never threaded into `SimulationReport` (only read ad hoc from `SimulationResult` in `ForecloseControlMeasureTest`) — the design explicitly names `SimulationReport.kt` as a file this task touches.
- [x] 5.4 RED then GREEN: `test/simulation/RunObservationTest.kt` — rename `DefeatCause.EXECUTION` → `DefeatCause.ARREARS`; update classifier to read the lock state (`inArrears` at combat end) instead of the old `endDebt`-based check. RED confirmed via compile failure (`compileDebugUnitTestKotlin`, `RunObservationTest.kt:108,186-188`) before fixing call sites. Also fixed the dependent (same-package) call site in `DebtPressureTest.kt:100,113` (`classifyDefeat(run.debt)` → `classifyDefeat(engine.getState().inArrears)`, `DefeatCause.EXECUTION` → `DefeatCause.ARREARS`, `EXECUTION_SHARE_FLOOR` → `ARREARS_SHARE_FLOOR`) — not in the original file list but required to compile since `classifyDefeat`/`DefeatCause` are same-package top-level declarations; those two gates are `@Disabled` (F2 open design debt, unrelated to this change) and were left disabled.
- [x] 5.5 REFACTOR: verify `HarnessDeterminismTest` stays green — new state is two `Boolean`s + one `Int`, no UUID-keyed maps or iteration-order dependence introduced. Confirmed via `gradle --no-daemon :app:testDebugUnitTest --tests "*.HarnessDeterminismTest"` (BUILD SUCCESSFUL).

## Phase 6: Render and i18n

- [x] 6.1 GREEN: `gdx/render/CombatRenderer.kt` — repoint red-debt visual threshold (`:365,:387`) to `ARREARS_THRESHOLD`; repoint loan affordance display to `DEBT_SCALE_ANCHOR`. Loan affordance at `:741` was already correctly on `DEBT_SCALE_ANCHOR` (left untouched); the stale `:382-384` comment referencing "EXECUTION"/"instant death" was also corrected for accuracy while touching this block.
- [x] 6.2 GREEN: `assets/i18n/strings.properties`, `assets/i18n/strings_es.properties` — rename `log.debt_execution` → `log.arrears_locked`, `log.debt_execution_levy` → `log.arrears_locked_levy`; EN/ES thematic (debt/collections tone), neutral Spanish, no voseo.
- [x] 6.3 GREEN: update all `Localizer.get(...)` call sites in `CombatEngine.kt` referencing the old log keys to the renamed keys. `grep -rn debt_execution app/src` returns zero results after this edit.
- [x] 6.4 Run `I18nBundleTest` — confirm per-key coverage in both locales for the two renamed keys.

## Phase 7: Post-Change Empirical Validation (required before this change is accepted)

- [x] 7.1 Run `IntentVerbsE1Test` (200 seeds) post-change — assert response-gap >= 10.0pp; record in `docs/BALANCE-BASELINE.md` under "Post fv-e1-arrears-lock".
- [x] 7.2 Run `RunSimulationHarnessTest` (200 seeds) post-change — assert greedy win rate in `[0.35,0.55]`, `avgPeakDebt` in `[25,45)`, neither policy >= 70% win rate, lock fire-rate > 0 per policy; record same doc.
- [x] 7.3 Joint diagnostic sweep: run `ARREARS_THRESHOLD ∈ {40,45}` × Gatillo B `{on,off}` as a local 2×2 (temporary local overrides, not shipped code) — 4 combinations, 200 seeds each. Append all 4 result sets to `docs/BALANCE-BASELINE.md`. Informational only — does not re-decide the owner-locked 40/on choice.
- [x] 7.4 Confirm `HarnessDeterminismTest` still green post-change (compare against Phase 1.2 control).

## Phase 8: Documentation Cleanup

- [x] 8.1 Update `docs/GDD.md` — replace `EXECUTION_THRESHOLD`/instant-defeat description with the arrears-lock mechanic and the two-constant split. Done in four places: (a) constant table row `EXECUTION_THRESHOLD | 50 | Death line` split into `ARREARS_THRESHOLD | 40` (behavioral) + `DEBT_SCALE_ANCHOR | 50` (scale only); (b) Part 2 **confirmed rule 2** rewritten from "Execution — immediate defeat" to "En Mora" with an arm/freeze/clear/once-per-combat/Gatillo-B behaviour table, a three-row constant-job table naming each call site, and the D3 rationale for keeping harness + `takeLoan` blind to the lock; (c) the historical usury bullet given a forward pointer instead of a dangling `EXECUTION_THRESHOLD`; (d) the "tighten Execution if greedy wins >70%" open risk repointed at the arrears lock and given its last measured value (47.5%). Every call site in the new tables was re-derived from code, not from `design.md`: `rg -n "ARREARS_THRESHOLD|DEBT_SCALE_ANCHOR" app/src`. **Two disclosures.** (1) The rule-2 supersession is stated inline rather than by deleting the old text, because the file's own convention is to keep superseded rules visible. (2) While splitting the constant table its header note claimed to be "the complete set of `const val` … verified 2026-08-27"; re-deriving it (`rg -n 'const val' DebtConfig.kt`) found **14** constants against 11 table rows, with `STARTING_DEBT`/`LEVERAGE_DIVISOR`/`EXECUTION_DAMAGE_DIVISOR` missing and `MAX_GARNISH_RATE` (0.6, not 0.75) and `DEBT_SCALING_ATTACK_DIVISOR` (8, not 10) wrong. Those five rows are **pre-existing and out of this change's scope**, so they were *not* rewritten — but the false "complete set, verified" claim sitting on top of a table this task edits was withdrawn and replaced with the re-derivation and an explicit "unowned" marker. Editing the table while leaving that claim standing would have re-certified it.
- [x] 8.2 Update `openspec/VERIFICATION-CHECKLIST.md` — add this change's verification line items (Phase 7 results, determinism check). Added section **C.AL** (rows AL1-AL9) before section D: AL1 Execution deleted (`rg -n EXECUTION_THRESHOLD app/src` → nothing); AL2 the constant split + the behavioral-vs-scale call-site division, with "a policy reading 40 is a finding" (D3); AL3 lock unit contract (`CombatEngineTest`, new `CombatStateTest`); AL4 Gatillo B (`RunManagerTest`); AL5 `IntentVerbsE1Test` 33.0/21.5pp weights, response gap +2.0pp; AL6 `RunSimulationHarnessTest` 47.5%/47.0% win, peak 30.1, fire rate 2.5%/0.5%; AL7 the 2×2 sweep; AL8 `HarnessDeterminismTest` 3/3 vs the Phase 1.2 control; AL9 full-suite count. Every number is the one recorded in `docs/BALANCE-BASELINE.md` §"Post fv-e1-arrears-lock" — no new estimate. **Three disclosures.** (1) Per this file's own rule ("a row is closed by a pass with no memory of writing it"), the rows are labelled **evidence, not closure**, and carry a where-to-run warning: the change is unmerged, so on `develop` these commands describe absent code. (2) AL5 records that `tasks.md` 7.1's "assert response-gap >= 10.0pp" is **not** what the test enforces (the 2026-08-28 re-metric left `>= -5.0` advisory plus difficulty-weight floors) — a verifier trusting 7.1's wording would hunt for a gate that does not exist. AL6 flags leverage's 0.5% fire rate (~1 run in 200) as the change's most fragile assertion. (3) Row **A1** was corrected, beyond "add line items": it asserted `EXECUTION_THRESHOLD = 50`, which this change deleted, and its `rg` command still exits 0 printing the other four constants — i.e. it read as PASS while carrying a false claim. Fixing it is this change's own debt, so it was fixed here with an inline dated correction note in the file's existing style.
- [x] 8.3 Confirm `docs/ANALISIS-*` historical files are left untouched (they are records, not live docs — do not rewrite). Verified, not assumed: `git status --porcelain docs/ANALISIS-simulacion-sweep-500.md docs/ANALISIS-simulacion-sweep-500-v2.md docs/ANALISIS-simulacion-sweep-500-v3.md` prints **nothing** (all three clean), and `git status --porcelain docs openspec` shows exactly four modified paths — `docs/BALANCE-BASELINE.md` (Phase 1/7), `docs/GDD.md` (8.1), `openspec/VERIFICATION-CHECKLIST.md` (8.2), `openspec/changes/fv-e1-arrears-lock/tasks.md` (this file). The three `ANALISIS-*` files still describe the Execution death line, which is correct as of their own date and is why they are records. **Related finding, deliberately NOT acted on:** `openspec/config.yaml`'s `balance_gate` block, `docs/VISION.md`, `docs/TRACKING.md` and `docs/PLAN-PI.md` are **live** documents that still name `EXECUTION_THRESHOLD` as a current constant. They are outside 8.1-8.3's named scope (`GDD.md` + `VERIFICATION-CHECKLIST.md`), so they were left alone and recorded as unowned at the end of the new C.AL section instead of being silently rewritten. **Second finding, more serious and also not acted on:** `hud.execution_warning` still reads `DEBT OVER EXECUTION — ANY NEW DEBT KILLS` / `DEUDA SOBRE EJECUCIÓN — CUALQUIER DEUDA NUEVA MATA` in both bundles, while `CombatRenderer:388` now shows that warning when `debt >= ARREARS_THRESHOLD` — so the HUD tells the player new debt kills at the exact moment the lock arms, which is false. An i18n bundle is not documentation and 8.1-8.3 did not name it (design D6 scoped UI out except the two `log.*` keys, already renamed in 6.2), and no test can catch it because this repo has no headless GL harness. Filed as checklist row **AL10** — an open defect, not a check — and raised as the owner's first decision in `archive-report.md`.
