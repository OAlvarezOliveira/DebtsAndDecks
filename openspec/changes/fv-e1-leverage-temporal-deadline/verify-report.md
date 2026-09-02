# sdd-verify report — FORECLOSE Temporal Deadline (`fv-e1-leverage-temporal-deadline`)

**Generated:** 2026-08-29. **Verdict: FAIL** — the change breaks E2 (proposal §8 "E2 leaves its band" fail);
the exit criterion's E1 prong is also unmet (gap negative at every candidate). Per the chain's archive rule,
artifacts are left **active** and not archived.

## Single-pass measurement gate (proposal §7, tasks 5.2 / 7.6)

Exact command run:

```
./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' \
  --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest' \
  --tests '*HarnessDeterminismTest' --tests '*EnemyTierRegressionTest'
```

Plus the in-test candidate sweep:

```
./gradlew :app:testDebugUnitTest --tests '*ForecloseCancelThresholdSweepTest'
```

Environment: Gradle 8.11.1 (cached), `ANDROID_HOME` set, `--offline` (deps cached). Toolchain per
`docs/CONVENTIONS.md` and the debtsanddecks skill (direct cached gradle binary, 4-space indent).

## Results

| Test | Result | Notes |
|------|--------|-------|
| `ForecloseWindowTest` (Phase 2 RED→GREEN) | PASS | 7/7 behavioral specs GREEN |
| `IntentStepTest` (1.1) | PASS | `cancelThreshold` defaults to 0, mirrored on `EnemyInstance.Intent` |
| `WindowRespondingPolicyTest` (3.1) | PASS | fixture exploits the window |
| `HarnessDeterminismTest` (2.11/7.1) | PASS | no iteration-order drift (per-`EnemyInstance` window state) |
| `EnemyTierRegressionTest` (7.1) | PASS | boss HP 57 untouched — no HP/damage change |
| `ForecloseControlMeasureTest` (4) | PASS | bailiff re-derivation: 1 seizure + DEFEAT still holds |
| `IntentVerbsE1Test` (6.1/7.4) | PASS | `responseGap >= -5.0` floor intact; `weightResponding >= 20`, `weightIgnoring >= 15` hold |
| `RunSimulationHarnessTest` (7.x / E2) | **FAIL** | `greedy.winRate ≈ 0.95 must be in [0.35, 0.55]` at `RunSimulationHarnessTest.kt:255` |

## Sweep (Phase 5, recorded in `docs/BALANCE-BASELINE.md`)

Ladder `[27,30,33,36,39,42,45]`, 200 seeds/policy. `resp` = `WindowRespondingPolicy`, `ign` = `LeveragePolicy`,
`greedy` = `ScriptedPolicy`. `seizures = 0` for both policies at **every** candidate.

| cancelThreshold | respWin | ignWin | E1 gap | E2 | seizures (resp/ign) |
|---|---|---|---|---|---|
| 27 | 95.0% | 99.5% | -4.5pp | RED | 0 / 0 |
| 30 | 97.0% | 99.5% | -2.5pp | RED | 0 / 0 |
| 33 | 97.0% | 99.5% | -2.5pp | RED | 0 / 0 |
| 36 | 97.5% | 99.5% | -2.0pp | RED | 0 / 0 |
| 39 | 98.0% | 99.5% | -1.5pp | RED | 0 / 0 |
| 42 | 98.0% | 99.5% | -1.5pp | RED | 0 / 0 |
| 45 | 98.5% | 99.5% | -1.0pp | RED | 0 / 0 |

## Exit-criterion evaluation (proposal §8)

1. **Response gap ≥ 10pp** — FAIL: gap is negative (-4.5 → -1.0pp) at every candidate. The temporal window
   lets the *ignoring* policy avoid the seizure for free (debt dips below `cancelThreshold` during the 3-turn
   window), so the window-exploiting policy only wastes cards repaying and wins *less*, not more.
2. **E2 green in the same run** — FAIL: win rate ≈ 95-99% at every candidate, far above the `0.70` ceiling and
   outside `[0.35, 0.55]`. The window cancels FORECLOSE whenever `debt` dips below `cancelThreshold`, which it
   does every cycle inside the shared leverage band — so FORECLOSE effectively never fires (`seizures = 0`).
3. **Numbers + exact command recorded** — DONE (`docs/BALANCE-BASELINE.md`, `/tmp/fv-e1-cancelThreshold-sweep.txt`).

## Non-negotiables (proposal §5) — all hold EXCEPT E2

- No enemy HP/damage changed (7.1 GREEN). ✅
- HEDGE branch (`:296–304`) byte-for-byte unchanged (7.2 GREEN). ✅
- `HarnessBands` ratios / `DebtConfig.EXECUTION_THRESHOLD` unmoved (7.3 GREEN). ✅
- `IntentVerbsE1Test` `responseGap >= -5.0` floor unchanged (7.4 GREEN). ✅
- E1 gap + E2 read from the same run (7.5 GREEN). ✅
- **E2 (`RunSimulationHarnessTest`) GREEN — FAILED (7.6).** ❌ This is the proposal §8 "E2 leaves its band" fail.

## Blocker / Disposition

The change **must not be shipped**: it breaks a non-negotiable (E2 band assertion). Per proposal §5 and §10
(rollback), the engine/window change should be **reverted** to restore snapshot FORECLOSE (`debt >= param`, fee 9)
so `RunSimulationHarnessTest` is green again. No `cancelThreshold` value is picked (tasks 5.4: no candidate passed).
The measured number is the deliverable — a genuine, new FV.E1 result (the temporal-deadline axis does not rescue E1
and destroys the FORECLOSE difficulty sink).

## sdd-sync / sdd-archive

- **sdd-sync: BLOCKED** — verification failed (E2), so the delta specs are not folded into `openspec/specs/`.
  They remain in this change dir.
- **sdd-archive: BLOCKED** — chain rule: "If verification or sync fails, leave artifacts active and report the
  blocker." The change is left active (not archived) pending the revert decision below.

## Pending decisions (require user input — not auto-applied)

1. **Revert the engine change** (restore snapshot FORECLOSE, remove `cancelThreshold` from `all.json`, drop the
   window fixture/tests) to keep the baseline green? (Recommended — per proposal §10.)
2. **Commit/PR** is NOT performed: lifecycle-gated, and the change is a fail. No `git commit`/`push`/PR was made.
