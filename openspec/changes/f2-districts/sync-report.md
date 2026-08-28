# sdd-verify — sync-report

**Change:** `f2-districts` (PR2, section 7)

## File-backed delta spec
The delta spec already lives at `openspec/changes/f2-districts/specs/run-structure/spec.md`
(R2.7 district identity visible; R2.8 art debt; R2.9 design system). It was authored before this task;
this task's wiring implements R2.7. No new requirement was added, so there is **no new delta file to
write**.

## Sync into `openspec/specs/` (main store)
**Deferred — not applicable yet.** The chain step says sync "without archiving" only when the verified
file-backed delta is ready to merge. R2.7 is implemented and verified, but the change as a whole (PR2) is
incomplete: 4.x (design system) and 5.x (art) are unstarted, and R2.8/R2.9 have no implementation.
Syncing the delta into the main `openspec/specs/run-structure/spec.md` now would merge a half-finished
PR2's spec into the trunk's spec — exactly what the PR1 precedent avoids (PR1's delta was archived only
at its squash-merge, `6b50164`).

**Decision:** leave the delta spec as a change-local artifact until PR2 ships, then archive (which merges
R2.7–R2.9 into the main spec). Consistent with `openspec/config.yaml`'s "git wins when mirrors disagree"
rule and the two-chained-PR plan in `proposal.md`.

## Engram mirror
`artifact_store.engram` topic `sdd/<change>/<artifact>` is a mirror of the git tree; the git artifacts
above are the source of truth. No Engram write performed by this chain run (the harness owns that).
