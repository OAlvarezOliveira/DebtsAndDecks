# sdd-apply — `fv-e1-arrears-lock`, Phase 8 (Documentation Cleanup)

**This file covers tasks 8.1-8.3 only.** Phases 1-7's evidence lives inline in `tasks.md` and in
`docs/BALANCE-BASELINE.md` §"Pre/Post fv-e1-arrears-lock"; this pass did not re-apply, re-edit or
re-verify any of it beyond reading it.

Strict TDD does not apply: no production code, no test, no behaviour was touched. The applicable
discipline for a docs phase is different and was followed instead — **every claim written was
re-derived from the code, not copied from `design.md`.** Where a re-derivation disagreed with an
existing doc claim, the disagreement is recorded rather than quietly resolved.

## 8.1 — `docs/GDD.md`

Four edits, all in the "record of what the code does" half of the file:

| Where | Was | Now |
|---|---|---|
| Debt-economy constant table | one row: `EXECUTION_THRESHOLD ｜ 50 ｜ Death line. Debt above this makes any debt-increasing action immediate defeat.` | two rows: `ARREARS_THRESHOLD ｜ 40` marked **behavioral**, and `DEBT_SCALE_ANCHOR ｜ 50` marked **not behavioral — scale only**, each naming who reads it |
| Part 2 confirmed rule 2 | "Execution — Debt above `EXECUTION_THRESHOLD` (50) is immediate defeat", plus a two-row table and a why-they-differ paragraph | "En Mora — Debt reaching `ARREARS_THRESHOLD` (40) locks the run, it does not end it": a 5-row arm/freeze/clear/once-per-combat/**Gatillo B** behaviour table, a 3-row constant-job table with call sites, the D3 rationale for the lock-blind harness and `takeLoan` sites, the 40-above-30 rationale, and a why-a-lock-not-a-death-line paragraph carrying the measured cost |
| Historical usury bullet | "…replaced by the Execution death line at `EXECUTION_THRESHOLD`" (dangling symbol) | same sentence plus "— itself since replaced by the 'En Mora' arrears lock at `ARREARS_THRESHOLD`". The strikethrough history is **not** rewritten; only the forward pointer added |
| Open risk, MVP scope tail | "if it wins above ~70%, tighten Execution" | "tighten the debt loss condition (as of FV.E1, the arrears lock and Gatillo B)", plus the last measured greedy win rate (47.5%) so the risk can be read as live or not |

Change Log row and footer date updated; the footer now states that rule 2 describes **the branch,
not `develop`**, because the change is unmerged.

### Call sites verified before writing them down

`rg -n "ARREARS_THRESHOLD|DEBT_SCALE_ANCHOR" app/src`:

- **Behavioral, reads 40**: `CombatEngine.armArrearsIfCrossed` (`:93`), `CombatRenderer:365` (red
  debt colour), `CombatRenderer:388` (explicit warning).
- **Scale only, reads 50**: `HarnessBands` (every E2 band ratio + `ratioOfExecution`),
  `ScriptedPolicy`, `LeveragePolicy`, **`RespondingPolicy`**, `RunManager.takeLoan` affordability
  guard, `CombatRenderer:742` loan affordance, `DebtConfigTest`.
- Correction made while writing: `design.md`'s Harness Policy Contract names the anchor ceiling for
  `ScriptedPolicy`/`LeveragePolicy` only, but `RespondingPolicy` has one too (`debtAfter > DEBT_SCALE_ANCHOR`).
  The GDD says "the sim policies' borrow ceilings (`Scripted`, `Leverage`, `Responding`)" — the code,
  not the design table.
- `addDebt` call sites confirmed for the Arm row: `:252` Credit-shortfall borrow, `:316` enemy
  `LEVY`, `:489` card-applied debt. The interest tick at `:391` is a **read** of the flag, not an
  arm path — stated explicitly in the table because a reader would otherwise assume compounding can
  put them in arrears (design D2).

### Disclosure: a false claim withdrawn, five stale rows deliberately not fixed

The constant table's header note claimed to be "the complete set of `const val` declared in
`core/combat/DebtConfig.kt`, verified 2026-08-27". Re-derived on 2026-08-29
(`rg -n 'const val' app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt`): the file declares
**14** constants; the table lists **11** after this edit.

- Missing rows: `STARTING_DEBT = 6`, `LEVERAGE_DIVISOR = 6`, `EXECUTION_DAMAGE_DIVISOR = 2`.
- Wrong values: `MAX_GARNISH_RATE` is **0.6**, not 0.75. `DEBT_SCALING_ATTACK_DIVISOR` is **8**,
  not 10.

All five are **pre-existing and unrelated to the arrears lock**, so they were not rewritten — that
is another change's job, with its own verification. But the "complete set / verified" claim sitting
directly on top of a table this task edits could not be left standing: editing the table under it
would have re-certified a claim now known false. The note is replaced with the re-derivation, the
five open items named, and an explicit "read the file, not this table" marker. This is the
`VERIFICATION-CHECKLIST` E14 failure mode ("a number nobody re-ran is a guess wearing a fact's
clothes") caught in the act.

## 8.2 — `openspec/VERIFICATION-CHECKLIST.md`

New section **`### C.AL`** (rows AL1-AL9) inserted after the §C6 recipe, before section D:

| Row | Checks | Cited evidence |
|---|---|---|
| AL1 | Execution deleted, not bypassed | `rg -n EXECUTION_THRESHOLD app/src` → nothing; plus read `armArrearsIfCrossed` |
| AL2 | Constant split + **behavioral-vs-scale call-site division**, with "a policy reading 40 is a finding" (D3) | `DebtConfigTest`, `rg` over both symbols |
| AL3 | Lock unit contract: 40 not 39, 38→42 jump, no re-arm, dip keeps, zero clears, interest frozen | `CombatEngineTest`, new `CombatStateTest` |
| AL4 | Gatillo B both directions + does not fire outside `COMBAT_END`; branch order after `allEnemiesDead`, before garnishment | `RunManagerTest` |
| AL5 | E1 gate green **and the gate is not the 10pp response gap `tasks.md` 7.1 names** | weights 33.0 / 21.5pp, gap +2.0pp |
| AL6 | E2 bands + fire rate > 0 for both blind policies | 47.5% / 47.0% win, peak 30.1, fire 2.5% / 0.5% |
| AL7 | Delta attributable; 40/on not re-decided by measurement | the 2×2 sweep, overrides reverted |
| AL8 | Determinism preserved | `HarnessDeterminismTest` 3/3 vs the Phase 1.2 control |
| AL9 | Full-suite count as a timestamped observation, not a threshold | 29 classes / 251 tests / 0 failures / 2 skipped |

Every number is the one already recorded in `docs/BALANCE-BASELINE.md` §"Post fv-e1-arrears-lock".
No figure was estimated, re-derived by eye, or re-run for the harness rows.

Three deliberate departures from a literal "add line items":

1. **The rows are labelled evidence, not closure.** This file's own rule is that a row closes only
   when a pass with no memory of writing it re-runs the command. The pass that produced these
   numbers implemented the change, so a preamble says so, and adds where-to-run and locale
   warnings — the change is unmerged, so on `develop` these commands describe absent code.
2. **AL5 records a gate mismatch against this change's own `tasks.md`.** Task 7.1 says "assert
   response-gap >= 10.0pp"; the test does not contain that assertion and deliberately does not
   restore it (the 2026-08-28 re-metric replaced it with `responseGap >= -5.0` advisory plus
   difficulty-weight floors). A verifier trusting 7.1's wording would hunt for a gate that does not
   exist. AL6 similarly flags leverage's 0.5% fire rate — about one run in 200 — as the most
   fragile assertion in the change.
3. **Row A1 was corrected, which is more than adding items.** It asserted
   `EXECUTION_THRESHOLD = 50`; this change deleted that symbol, and its `rg` command still exits 0
   printing the other four constants, so the row read as PASS while carrying a false claim. That
   staleness is this change's own debt, so it was repaired here, with an inline dated correction
   note in the file's existing style, and a separate command for the deletion half.

## 8.3 — `docs/ANALISIS-*` left untouched

Verified, not assumed:

```
$ git status --porcelain docs/ANALISIS-simulacion-sweep-500.md \
    docs/ANALISIS-simulacion-sweep-500-v2.md docs/ANALISIS-simulacion-sweep-500-v3.md
(no output)
$ git status --porcelain docs openspec
 M docs/BALANCE-BASELINE.md
 M docs/GDD.md
 M openspec/VERIFICATION-CHECKLIST.md
 M openspec/changes/fv-e1-arrears-lock/tasks.md
```

The three sweep files still describe the Execution death line. That is correct as of their own
date, which is exactly why they are records and not live docs.

**Finding that outranks the rest, deliberately not acted on.** `hud.execution_warning` still reads
`DEBT OVER EXECUTION — ANY NEW DEBT KILLS` (EN) / `DEUDA SOBRE EJECUCIÓN — CUALQUIER DEUDA NUEVA
MATA` (ES) in both bundles, and `CombatRenderer:388` now shows it when `debt >= ARREARS_THRESHOLD`.
New debt no longer kills, so the HUD lies to the player at the exact moment the new mechanic fires.
An earlier Phase 5-6 pass flagged this as a Phase 8 follow-up; it is still live. **Not fixed here:**
an i18n bundle is not documentation, 8.1-8.3 did not name it, design D6 scoped UI/i18n out except
the two `log.*` keys, and no test can catch it (this repo has no headless GL harness). Recorded as
checklist row **AL10** — the only AL row filed as an open defect rather than a check — and raised to
the owner as the first decision in `archive-report.md`.

**Finding, deliberately not acted on.** Four **live** documents still name `EXECUTION_THRESHOLD` as
a current constant: `openspec/config.yaml` (`balance_gate.status`), `docs/VISION.md`,
`docs/TRACKING.md`, `docs/PLAN-PI.md`. Unlike `ANALISIS-*`, these are not records, so each is a real
stale reference caused by this change's rename. They are outside 8.1-8.3's named scope, so they were
left alone and recorded as **unowned** at the end of the new `C.AL` section instead of being
silently rewritten. A follow-up change should own them.

## Files touched by this phase

| File | Lines changed | Why |
|---|---|---|
| `docs/GDD.md` | 98 | 8.1 |
| `openspec/VERIFICATION-CHECKLIST.md` | 39 | 8.2 |
| `openspec/changes/fv-e1-arrears-lock/tasks.md` | 8.1-8.3 marked `[x]` with inline evidence | tracking |
| `openspec/changes/fv-e1-arrears-lock/{init,apply-progress,verify-report,sync-report,archive-report}.md` | new | chain artifacts |

No production file, no test file, no i18n bundle, no `docs/ANALISIS-*`, and no other phase's work
was touched. Nothing was committed — git commit is lifecycle-gated in this harness.
