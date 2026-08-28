# sdd-verify — init

**Change:** `f2-districts` (PR2 — `feat(ui): district identity`)
**Task in scope:** Section 7 — Render (wire `RunManager.currentDistrict` end-to-end).
**Sub-tasks:** 7.1, 7.2, 7.3, 7.4 only.
**Out of scope (explicit):** 4.x (design system), 5.x (art/asset generation), and every task outside section 7. PR2's spec delta (R2.7–R2.9) already exists in `specs/run-structure/spec.md`; archiving/sync-to-main is deferred until PR2 ships (matches the PR1 merge-then-archive precedent).

## Context gathered

- `proposal.md` — PR2 = backgrounds, names on screen, i18n, `docs/DESIGN-SYSTEM.md`. Empty by design: PR1 (`6b50164`) carried model + data + zero-delta proof only.
- `design.md` — `RunManager` gains `currentDistrict` (read-only, derived from `slots[slotIndex].districtId`); combat + node renderers select the background from it; text placement goes through `HandLayout`/`CombatLayout`, no fixed 1280 coordinate; `RunManager.Phase` MUST stay `{ COMBAT, NODE, VICTORY, DEFEAT }`.
- `specs/run-structure/spec.md` — R2.7: district identity visible on entering + on node screen; title position derives from viewport width, no fixed 1280 coordinate. R2.6: no new phase.
- `tasks.md` — 7.1/7.2/7.3/7.4 unchecked; 7.5 already done (phase machine unchanged, 5 `when(phase)` sites, 3 exhaustive). 6.1–6.3 (i18n district keys) shipped in PR1.

## Code map (files this task touches)

- `core/combat/RunManager.kt` — add `currentDistrict` + `isDistrictEntrance` (7.1, 7.3 trigger).
- `core/model/District.kt` — add `backgroundKey()` (7.2, pure + testable, no data change).
- `gdx/render/CombatLayout.kt` — add `districtTitle(worldWidth)` (7.4).
- `gdx/render/CombatRenderer.kt` — draw `currentDistrict.backgroundKey()` as background (7.2); draw district title card on entrance + node screen (7.3). `render` gains `run` param.
- `gdx/screens/GameScreen.kt` — pass `runManager` into `renderer.render(...)`.
- `di/Module.kt` — bind `List<District>`; pass to `RunManager`.
- Tests: `RunManagerTest.kt` (7.1), `DistrictTest.kt` (7.2), new `DistrictTitleLayoutTest.kt` (7.4).

## Constraints

- Strict TDD (RED → GREEN). 7.4 explicitly requires a RED-then-GREEN layout test.
- No `RunManager.Phase` change; no balance-affecting code (zero-delta gate holds by construction).
- Do not edit PR1 tests (`DataLoaderDistrictTest`, the 5-arg `RunManager(...)` call sites) — `RunManager.districts` defaults to `emptyList()` so they compile unchanged; production wiring passes the real catalog.

## Environment

- Gradle 8.11.1, offline cache warm, Android SDK at `/home/oscardev/Android/Sdk`.
- Baseline green: `DistrictTest` + `RunManagerTest` build & pass (`testDebugUnitTest`).
- Render code needs LibGDX GL; only pure helpers (layout, `backgroundKey`, `currentDistrict`) get unit tests. Pixel output is not headlessly verifiable — 7.2/7.3 are proven by the pure helpers + the layout test + the wiring (review).
