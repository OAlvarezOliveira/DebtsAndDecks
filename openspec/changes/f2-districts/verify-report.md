# sdd-verify — verify-report

**Change:** `f2-districts` (PR2, section 7) · **Tasks:** 7.1–7.4 · **Branch:** `feat/f2-districts-runmanager`

## Focused verification (per task)
| Task | Test(s) | Result |
| --- | --- | --- |
| 7.1 | `RunManagerTest.currentDistrict is derived from the slot the run is on`, `...isDistrictEntrance is true only on the first slot of each district` | GREEN |
| 7.2 | `DistrictTest.district exposes a backdrop texture key derived from its id` | GREEN |
| 7.4 | `DistrictTitleLayoutTest` (2 tests) | GREEN |
| 7.3 | `drawDistrictTitle` wiring + `isDistrictEntrance` trigger | reviewed; not pixel-testable headlessly |

## Full verification
- `./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL**, **235 tests, 0 failures, 0 errors**.
- `RunSimulationHarnessTest` (13 tests, 200 seeds) GREEN → zero balance delta (R2.5) holds.
- Compile: `compileDebugKotlin` + `compileDebugUnitTestKotlin` clean (no new warnings introduced by this change).

## Review / judgment blockers
1. **Not pixel-verifiable headlessly.** 7.2/7.3 draw via LibGDX GL; only the pure helpers
   (`backgroundKey`, `districtTitle`) and the `isDistrictEntrance` trigger are unit-tested. The actual
   district card and per-district backdrop need a device/screenshot, which is task 5's domain. Known
   limit, not a defect.
2. **Backdrop art absent (task 5 / PR2 5.x).** Until the `bg_district_*` PNGs exist, combat and node
   screens fall back to the gradient. R2.7 ("background is drawn") is satisfied by that fallback; the
   real art lands with 5.x.
3. **Archiving deferred.** PR2 is incomplete (4.x design system + 5.x art unstarted), so the change is
   not archived and its delta spec is not synced to the main spec store yet — see `sync-report.md` /
   `archive-report.md`. Matches the PR1 precedent (archive at squash-merge `6b50164`).

## What this task did NOT touch
- Tasks 4.x, 5.x, and every item outside section 7.
- `RunManager.Phase`, the five `when (phase)` sites, `run/sequence.json`, the district catalog content,
  and all PR1 tests (`DataLoaderDistrictTest`, `DistrictTest` beyond the new method, `RunManagerTest`
  beyond the two new methods).
