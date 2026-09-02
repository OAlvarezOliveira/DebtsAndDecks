# sdd-archive — `fv-e1-arrears-lock`, Phase 8

## Outcome: NOT archived. Artifacts stay active.

The chain archives only when verification succeeds **and** file-backed sync is complete or not
applicable. Verification passed and sync is not applicable, so those two gates are clear — but
archiving would still be wrong, for reasons that have nothing to do with Phase 8's quality.

| # | Blocker | Evidence |
|---|---|---|
| 1 | **The change is not committed.** Every changed file is a working-tree modification; the branch's only commit touching this change's folder is `3680a9d` (the Phase 2 constant split). Archiving a change whose implementation exists only as uncommitted edits would record delivery of something no reviewer can diff. | `git status --porcelain` → **18 `M` + 6 `??`** (the untracked six: the new `CombatStateTest.kt` plus this chain's five reports); `git log --oneline -- openspec/changes/fv-e1-arrears-lock/` → one commit |
| 2 | **Nothing has been reviewed or merged.** The change lives on the `feat/fv-verbs-foreclose-hedge` branch (the `DebtsAndDecks-fv-e1-leverage` worktree), unmerged into `develop`. `docs/GDD.md`'s footer now states this explicitly so the file cannot be misread as describing trunk. | branch state |
| 3 | **The chained-PR decision is still open, and now due.** `tasks.md`'s Review Workload Forecast says "Decision needed before apply: Yes / Chain strategy: **pending**". The diff is **692 insertions / 159 deletions across 18 files**, past the repo's 400-line-per-PR rule, so the split is a real decision the owner has not made. | `git diff --stat` |
| 4 | **`design.md` still carries an unticked Open Question.** Its D1 spec-amendment item is resolved in substance by `specs/debt-economy/spec.md:29-31` but its checkbox is `[ ]`. Archiving over an open question would bury it. `design.md` is outside 8.1-8.3's scope, so this pass did not tick it. | read `design.md` §Open Questions |
| 5 | **Commit is lifecycle-gated in this harness, and nothing was committed by design.** The task explicitly forbade it. Archiving is downstream of delivery, and delivery has not happened. | task constraint + repo skill note |

## What is genuinely complete

- Phases 1-7: previously applied and verified; re-read, not re-done, by this pass.
- Phase 8: tasks 8.1, 8.2, 8.3 done and marked `[x]` in `tasks.md` with inline evidence.
- **All eight phases of `tasks.md` are now `[x]`.** The change is implementation-complete and
  documentation-complete; it is *delivery*-incomplete.

## Next steps, in order, for the owner

0. **Decide on `hud.execution_warning` first.** Both bundles still tell the player
   `ANY NEW DEBT KILLS` / `CUALQUIER DEUDA NUEVA MATA`, and `CombatRenderer:388` now shows that
   warning exactly when the arrears lock arms. It is a two-string fix, it is player-facing, and no
   test can catch it (no headless GL harness). Out of Phase 8's documentation scope, so untouched
   here and recorded as checklist row **AL10**. Shipping the change without deciding this means
   shipping a lie in the HUD.
1. Decide the chain strategy the forecast left pending (suggested split: PR1 config, PR2
   engine + policies + tests, PR3 render/i18n/docs).
2. Route the commits through the sanctioned review path — conventional commits, no AI attribution;
   never force a commit that fail-closes the lifecycle gate.
3. Tick or re-scope `design.md`'s D1 Open Question, whose amendment already exists in the delta spec.
4. Open a follow-up change for the four **live** documents still naming `EXECUTION_THRESHOLD`
   (`openspec/config.yaml` `balance_gate.status`, `docs/VISION.md`, `docs/TRACKING.md`,
   `docs/PLAN-PI.md`) and, if wanted, the five unowned `docs/GDD.md` constant-table rows
   (3 missing constants, 2 wrong values — all pre-existing).
5. Only then archive.
