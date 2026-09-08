# sdd-init — FORECLOSE Temporal Deadline (`fv-e1-leverage-temporal-deadline`)

**Generated:** 2026-08-29 (chain execution start).
**Change dir:** `openspec/changes/fv-e1-leverage-temporal-deadline/`
**Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22). Current git branch: `feat/fv-verbs-foreclose-hedge`.
**Artifact store:** BOTH — git tree (`openspec/`) is authoritative; Engram mirrors `sdd/<change>/<artifact>`. (config.yaml confirms; no OpenSpec CLI installed, run by hand.)

## Task (verbatim ask)
Implement `openspec/changes/fv-e1-leverage-temporal-deadline` per its 37-task `tasks.md`, strict TDD
(RED before GREEN per task). Hard constraints from the request:
- Do **not** weaken `IntentVerbsE1Test`'s `responseGap >= -5.0` floor.
- Do **not** change any enemy HP/damage value.
- Do **not** touch the HEDGE branch (CombatEngine.kt `:296–304`).
- Do **not** move `HarnessBands` ratios or `DebtConfig.EXECUTION_THRESHOLD`.
- The cancel-threshold numeric value is picked **only** from the Phase 5 sweep's measured data — never guessed.

## Context read (no guessing)
- `proposal.md` — root-cause lever: FORECLOSE `debt >= param` snapshot shares the same raw `CombatState.debt`
  integer as HEDGE's proportional read and the E2 leverage band, so all three prior levers (policy/pool/threshold)
  ceilinged at ~+4pp. Decoupling = make FORECLOSE a 3-turn **temporal window** (announce arms, repay below an
  independent `cancelThreshold` during the window cancels the seizure). Owner decisions (2026-08-29): N=3,
  independent constant, run-ending on expiry, HEDGE unchanged, constants in `all.json`.
- `design.md` — D1 per-`EnemyInstance` window state (3 private vars, NOT a map/UUID key); D2 two thresholds
  (`cancelThreshold` during window, `param` at close); D3 `intentPattern` advance untouched; D4 tick once/turn in
  `endPlayerTurn()` before enemy loop; D5 re-announce ignored; D6 resolve-at-close; D7 `FORECLOSE_WINDOW_TURNS=3`
  private const; D8 sweep in-test via `VerbControl.withForecloseCancelThreshold`; D9 window exposed on `EnemyState`.
- `tasks.md` — 37 tasks across Phases 0–8. Single PR. Estimate 90–160 lines; 400-line budget risk Low.

## Specs (3 delta files, all ADDED requirements)
- `combat-engine-foreclose/spec.md` — window opens on announcement; cancel-check during window; uncancelled
  expiry stays run-ending. Non-negotiables inherited.
- `enemy-intent-data/spec.md` — new per-intent `cancelThreshold` field, independent of `param`, not a
  `DebtConfig` constant.
- `balance-measurement/spec.md` — new minimal window-exploiting fixture; single-pass measurement gate; gap <10pp
  is a valid recorded fail.

## Files in scope (from design File Changes)
- `core/enemies/EnemyDefinition.kt` — `IntentStep.cancelThreshold` + `EnemyInstance.Intent.cancelThreshold`
- `core/enemies/EnemyInstance.kt` — window vars + `openForecloseWindow`/`tickForecloseWindow`/accessors
- `core/combat/CombatEngine.kt` — FORECLOSE arms only; per-enemy window tick; `FORECLOSE_WINDOW_TURNS`
- `core/model/EnemyState.kt` — `forecloseWindowTurnsLeft`/`forecloseCancelThreshold` mirror fields
- `assets/enemies/all.json` — `cancelThreshold: 27` on loan_shark FORECLOSE (seeded; final value from sweep)
- `test/simulation/VerbControl.kt` — `withForecloseCancelThreshold` sweep helper
- `test/simulation/WindowRespondingPolicy.kt` — NEW fixture (fresh `object : RunPolicy`, not a variant)
- `test/simulation/IntentVerbsE1Test.kt`, `ForecloseControlMeasureTest.kt` — measure the new fixture
- `docs/BALANCE-BASELINE.md` — sweep table

## Toolchain
- Gradle binary (cached): `/home/oscardev/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle`
- Run a single test class: `gradle --no-daemon :app:testDebugUnitTest --tests '*<FQCN>'`
- Measurement gate (Phase 5): `gradle --no-daemon :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest'`
- Core purity: no `com.badlogic.gdx.*` imports under `core/`. 4-space indent.

## Execution plan
1. `sdd-init` (this file) — context.
2. `sdd-apply` — Phases 0–7 with RED→GREEN per task; Phase 8 prep (no auto-commit/push: lifecycle-gated).
3. `sdd-verify` — run focused + full gate; record blockers.
4. `sdd-sync` — fold the 3 delta specs into `openspec/specs/` (combat-engine-foreclose / enemy-intent-data /
   balance-measurement) if not already present.
5. `sdd-archive` — only if verification + sync succeed (or sync N/A); otherwise leave active and report blocker.

## Risk flags carried into apply
- The Phase 5 sweep result is genuinely open (proposal §8: under 10pp = valid fail, write it down and stop). The
  cancel-threshold value and the exit-criterion verdict are DATA, not decisions.
- Phase 6 difficulty-floor risk: the window may push `weightResponding`/`weightIgnoring` down; if so, record as a
  fail for that candidate, do NOT relax the floors.
- The bailiff fixture (`ForecloseControlMeasureTest`) timing moves under the window; Phase 4 re-derives the count,
  keeping `1` seizure + `DEFEAT` if the arithmetic supports it (it does: window arms turn 1, re-arms every 3 turns,
  first seize resolves at turn 12 when debt≈44 ≥ 27 — to be confirmed by running the test).
