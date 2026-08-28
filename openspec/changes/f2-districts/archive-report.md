# sdd-verify — archive-report

**Change:** `f2-districts` (PR2, section 7) · **Tasks:** 7.1–7.4 · **Branch:** `feat/f2-districts-runmanager`

## Gate (from the chain)
> Archive `{task}` only when verification succeeds and file-backed sync is complete or not applicable.
> If verification or sync fails, leave artifacts active.

- **Verification:** succeeded — 225 tests, 0 failures, 0 errors, 2 skipped (JUnit XML, independently re-verified); balance harness GREEN.
- **File-backed sync:** not applicable / deferred — see `sync-report.md`. The R2.7 delta is complete, but
  PR2 as a whole is not (4.x, 5.x open).

## Decision: DO NOT ARCHIVE
The archive step archives the *whole* `f2-districts` change (`proposal` / `design` / `tasks` /
`specs/run-structure/spec.md`). Archiving now would mark PR2 complete while 4.x (design system) and 5.x
(art) are unstarted and R2.8/R2.9 are unimplemented — wrong. Per the chain's own rule, sync is "not
applicable" only for a *ready* delta; here the delta is real but the change is partial, so the safe action
is to **leave all artifacts active** and archive at PR2's merge (the PR1 precedent).

**Artifacts left active:**
- `openspec/changes/f2-districts/{proposal,design,tasks,specs/run-structure/spec.md}`
- this run's `{init,apply-progress,verify-report,sync-report,archive-report}.md`

**Next:** when 4.x + 5.x land and PR2 merges, run the archive step to merge R2.7–R2.9 into the main
`openspec/specs/run-structure/spec.md`.
