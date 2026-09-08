# sdd-init — `fv-e1-arrears-lock`, Phase 8 (Documentation Cleanup) only

**Scope of this chain run: tasks 8.1-8.3 and nothing else.** Phases 1-7 were applied and recorded
by earlier passes, inline in `tasks.md` and in `docs/BALANCE-BASELINE.md`. This file exists because
the `sdd-verify` chain declares it; it does not describe the whole change.

## SDD configuration, read not created

`openspec/config.yaml` already exists, so nothing was generated. What it says, as read on
2026-08-29:

| Field | Value |
|---|---|
| Artifact store | **both** — `openspec/` (git-versioned) and Engram (`sdd/<change>/<artifact>`, project `debtsanddecks`). Mirrors, not layers; on disagreement **the git tree wins**, because that is what a reviewer can diff. |
| Process | strict TDD (red → green → refactor, tests first) |
| Commits | conventional (`feat｜fix｜balance｜docs｜chore`), no AI attribution |
| Language | artifacts English; UI locales `en`, `es` |
| Balance gate | `RunSimulationHarnessTest`, 200 seeds, `ScriptedPolicy` (greedy) + `LeveragePolicy` |
| Tooling note in the file | this repo has **no OpenSpec CLI and no `package.json`**; the layout follows the convention by hand and there is no `openspec validate` gate |

Two facts about the config that matter to Phase 8 and are recorded rather than acted on:

1. Its `balance_gate.status` block still names `DebtConfig.EXECUTION_THRESHOLD` as a live constant.
   This change deleted that symbol (`rg -n EXECUTION_THRESHOLD app/src` → nothing). The block is
   therefore stale, and it is **outside** 8.1-8.3's declared scope (`docs/GDD.md` +
   `openspec/VERIFICATION-CHECKLIST.md`). Left untouched, recorded as unowned in the new
   `C.AL` section of the checklist. See also `docs/VISION.md`, `docs/TRACKING.md`,
   `docs/PLAN-PI.md` — same defect, same disposition.
2. There is **no `openspec/specs/` directory in this repository at all**. Canonical spec sync has
   never been performed here by any change. This determines the `sdd-sync` step's outcome.

## Preconditions checked before touching any file

| Check | Command | Result |
|---|---|---|
| Phases 1-7 complete in `tasks.md` | read `openspec/changes/fv-e1-arrears-lock/tasks.md` | 1.1-7.4 all `[x]` with inline evidence; 8.1-8.3 the only open items |
| Production code present, Execution deleted | `rg -n "EXECUTION_THRESHOLD" app/src` | nothing (exit 1) |
| Lock implemented as designed | `rg -n "arrears\|Arrears" CombatEngine.kt RunManager.kt CombatState.kt` | arm/clear/freeze/Gatillo B/counter all present at the sites `design.md` names |
| Suite state before docs edits | `gradle --no-daemon :app:testDebugUnitTest` | `BUILD SUCCESSFUL`, results `UP-TO-DATE` from the 16:20 run: 29 classes, 251 tests, 0 failures, 2 skipped |
| Historical files clean | `git status --porcelain docs/ANALISIS-*` | nothing |

## Skills loaded

`.pi/skills/debtsanddecks/SKILL.md` — repo conventions. The two rules that bound this phase: docs
claims must be re-derived from code rather than copied from a design document, and **git commit is
lifecycle-gated** in this harness, so nothing here is committed.
