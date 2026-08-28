# F0 — Vision & Program (documentation only)

**Status:** proposed, unverified. **Date:** 2026-08-28. **Branch base:** `develop`.
**Depends on:** nothing. **Blocks:** F1 (which needs the program's numbering to exist).

## Why

The vision currently lives in one prompt and in one head. Six phases are about to be planned
against it. Before any of that, the vision needs to be a **file in the repo that a reviewer
can diff and a future agent can read** — and the GDD needs to stop being the only document,
because it describes what the code does, not where it is going.

There is also a structural gap: this project has kept every SDD artifact in Engram only. The
owner has moved to a **dual store**. `openspec/` does not exist in this repo today, so F0
creates it — and gets it tracked in git on the first commit, because there is documented
precedent in this project of an apply agent deleting untracked files under
`openspec/changes/`.

## What changes

1. **New** `docs/VISION.md` — the consolidated vision with all nine §5 decisions resolved and
   their rejected alternatives recorded.
2. **Delta** to `docs/GDD.md` — traceable, not a rewrite. The GDD was resynced against
   `develop` on 2026-08-27 and is accurate about the code; it is only stale about *intent*.
3. **New** `openspec/` tree — `config.yaml`, `project.md`, the change folders for FV and
   F0-F8, and `VERIFICATION-CHECKLIST.md`.
4. **Engram mirror** — every artifact also written as `sdd/<change>/<artifact>` under project
   `debtsanddecks`.

## What does not change

No Kotlin. No JSON. No assets. No tests. `git diff --name-only` after F0 must show `docs/` and
`openspec/` only — that is the acceptance shape, and it is checkable in one command.

## Non-goals

- Not writing F3-F8 specs. Those are charters on purpose (see the reasoning in each charter).
- Not installing an OpenSpec CLI. There is no `package.json` in this repo; the layout is
  convention-only for now. Adding a validator is a candidate follow-up, not part of F0.
- Not marking anything verified. F0 ships a checklist for someone else to run.

## Risk

**Low, with one real trap.** The trap is `docs/GDD.md`: it is currently *correct*, and a
careless "update to the vision" pass would replace verified statements about the code with
aspirational statements about the plan. The spec below forbids that explicitly — every GDD
edit is either additive or a marked forward-reference.

## Review workload forecast

`docs/VISION.md` ~215 lines · GDD delta ~60 lines · openspec tree ~900 lines across 20 files.
**Total ~1200 lines, all prose.**

*The forecast missed by roughly 2x, recorded 2026-08-28.* At `0faa08d`,
`git diff --stat develop...HEAD | tail -1` reports **24 files changed, 2236 insertions(+),
8 deletions(-)**. Almost the whole gap is the reconciliation evidence every artifact now
carries — the command each tick cites. The figure grows with every further commit on this
branch, so re-run the command; do not trust this number. Over the 400-line threshold by volume, but it is
documentation with no execution semantics, so a single PR is defensible. If the reviewer
wants it split, the natural cut is **PR1: `docs/` (VISION + GDD delta)**, **PR2:
`openspec/` (tree + FV/F0/F1/F2 artifacts)**, **PR3: charters F3-F8**.
