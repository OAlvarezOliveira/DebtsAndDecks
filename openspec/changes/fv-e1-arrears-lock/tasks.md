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

- [ ] 3.1 RED: `core/model/CombatStateTest.kt` (or nearest existing state test) — assert `CombatState` default-constructs with `inArrears = false`, `arrearsUsedThisCombat = false`.
- [ ] 3.2 GREEN: `core/model/CombatState.kt` — append `inArrears: Boolean = false`, `arrearsUsedThisCombat: Boolean = false` (preserve existing constructor call-site compilation).
- [ ] 3.3 RED: `core/combat/CombatEngineTest.kt` — write failing cases: debt crosses 40 first time → `inArrears=true`/`arrearsUsedThisCombat=true`/combat continues; debt at 39 → no arm; second crossing after charge spent → stays false; dip 40→10 without hitting 0 → lock stays true; `debt==0` → lock clears; `wipe_debt` while unlocked → charge not consumed. Use `Random(seed)`, concrete expected values.
- [ ] 3.4 GREEN: `core/combat/CombatEngine.kt` — implement `armArrearsIfCrossed()` at tail of `addDebt` (replaces `endCombat(false)` return); implement `clearArrearsIfEscaped()` (`debt == 0 ⇒ inArrears = false`) invoked after `RepayDebt`/`WipeDebt`; drop the three defeat paths at `:211,:278,:455`; add `arrearsArmedCount` (Int, private set) incremented on arm; reset both flags + counter in `startCombat`; expose both flags via `getState()`.
- [ ] 3.5 GREEN: freeze passive interest — guard the `beginTurn` interest tick (`:361`) with `if (!inArrears)`; leave active card-applied debt increases unguarded.
- [ ] 3.6 RED: `core/combat/CombatEngineTest.kt` — interest-frozen-while-locked case: tick fires while `inArrears=true` → `state.debt` unchanged from that tick; active debt-applying card still increases debt.
- [ ] 3.7 GREEN: confirm 3.6 passes against 3.5's implementation (should already be green; add the test as a distinct triangulation case, not a new production change).
- [ ] 3.8 TRIANGULATE (3.3-3.7): add one more seed/case per RED item confirming no accidental coupling (e.g., arming at exactly 40 via a card that jumps debt 38→42 in one step, not just +1 increments).
- [ ] 3.9 REFACTOR: `CombatEngine.kt` — extract `armArrearsIfCrossed`/`clearArrearsIfEscaped` as named private functions per the design's Interfaces section; remove now-dead `levyExecution` defeat-path code.

## Phase 4: Gatillo B — Outcome Resolution

- [ ] 4.1 RED: `core/combat/RunManagerTest.kt` — enemy HP reaches 0 with `state.inArrears == true` → resolves `Phase.DEFEAT` (not victory); enemy HP reaches 0 with `inArrears == false` (post-escape) → resolves `Phase.VICTORY`.
- [ ] 4.2 GREEN: `core/combat/RunManager.kt` `refresh()` — insert Gatillo B check after the `allEnemiesDead` check, before garnishment (`:177-189`): `allEnemiesDead && state.inArrears ⇒ DEFEAT`.
- [ ] 4.3 TRIANGULATE: add a case where `allEnemiesDead` is false and `inArrears` is true (regular ongoing-combat state, not `COMBAT_END`) — confirm Gatillo B does not fire outside `COMBAT_END`.
- [ ] 4.4 REFACTOR: confirm `takeLoan`'s affordability guard (unchanged) still reads `DEBT_SCALE_ANCHOR`, not `ARREARS_THRESHOLD` — node loans never route through `addDebt` and must stay ungated by the lock.

## Phase 5: Harness Policy Wiring

- [ ] 5.1 GREEN: `test/simulation/RespondingPolicy.kt` — add one new lock branch as the first check in `chooseAction`: if `state.inArrears`, reuse the existing HP-aware cheapest-playable `wipe_debt` selection (`:75-86`); else fall through unchanged to the FORECLOSE branch.
- [ ] 5.2 GREEN: `test/simulation/ScriptedPolicy.kt`, `test/simulation/LeveragePolicy.kt` — mechanical rename only (`EXECUTION_THRESHOLD` → `DEBT_SCALE_ANCHOR` at their borrow-ceiling checks, `:75` and `:95`). No new lock branch — policies stay blind by design (D3).
- [ ] 5.3 GREEN: `test/simulation/RunSimulator.kt`, `test/simulation/SimulationReport.kt` — thread `arrearsArmed` from `CombatEngine.arrearsArmedCount` into `SimulationResult` and the report output, mirroring the existing `forecloseSeizureCount` pattern.
- [ ] 5.4 RED then GREEN: `test/simulation/RunObservationTest.kt` — rename `DefeatCause.EXECUTION` → `DefeatCause.ARREARS`; update classifier to read the lock state (`inArrears` at combat end) instead of the old `endDebt`-based check.
- [ ] 5.5 REFACTOR: verify `HarnessDeterminismTest` stays green — new state is two `Boolean`s + one `Int`, no UUID-keyed maps or iteration-order dependence introduced.

## Phase 6: Render and i18n

- [ ] 6.1 GREEN: `gdx/render/CombatRenderer.kt` — repoint red-debt visual threshold (`:365,:387`) to `ARREARS_THRESHOLD`; repoint loan affordance display to `DEBT_SCALE_ANCHOR`.
- [ ] 6.2 GREEN: `assets/i18n/strings.properties`, `assets/i18n/strings_es.properties` — rename `log.debt_execution` → `log.arrears_locked`, `log.debt_execution_levy` → `log.arrears_locked_levy`; EN/ES thematic (debt/collections tone), neutral Spanish, no voseo.
- [ ] 6.3 GREEN: update all `Localizer.get(...)` call sites in `CombatEngine.kt` referencing the old log keys to the renamed keys.
- [ ] 6.4 Run `I18nBundleTest` — confirm per-key coverage in both locales for the two renamed keys.

## Phase 7: Post-Change Empirical Validation (required before this change is accepted)

- [ ] 7.1 Run `IntentVerbsE1Test` (200 seeds) post-change — assert response-gap >= 10.0pp; record in `docs/BALANCE-BASELINE.md` under "Post fv-e1-arrears-lock".
- [ ] 7.2 Run `RunSimulationHarnessTest` (200 seeds) post-change — assert greedy win rate in `[0.35,0.55]`, `avgPeakDebt` in `[25,45)`, neither policy >= 70% win rate, lock fire-rate > 0 per policy; record same doc.
- [ ] 7.3 Joint diagnostic sweep: run `ARREARS_THRESHOLD ∈ {40,45}` × Gatillo B `{on,off}` as a local 2×2 (temporary local overrides, not shipped code) — 4 combinations, 200 seeds each. Append all 4 result sets to `docs/BALANCE-BASELINE.md`. Informational only — does not re-decide the owner-locked 40/on choice.
- [ ] 7.4 Confirm `HarnessDeterminismTest` still green post-change (compare against Phase 1.2 control).

## Phase 8: Documentation Cleanup

- [ ] 8.1 Update `docs/GDD.md` — replace `EXECUTION_THRESHOLD`/instant-defeat description with the arrears-lock mechanic and the two-constant split.
- [ ] 8.2 Update `openspec/VERIFICATION-CHECKLIST.md` — add this change's verification line items (Phase 7 results, determinism check).
- [ ] 8.3 Confirm `docs/ANALISIS-*` historical files are left untouched (they are records, not live docs — do not rewrite).
