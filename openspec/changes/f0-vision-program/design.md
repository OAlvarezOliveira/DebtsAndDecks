# F0 — Design

## The only interesting decision in this phase

**How to amend a document that is currently correct.**

`docs/GDD.md` was resynced against `develop` on 2026-08-27. It is not stale about the code —
it is stale about *ambition*. The failure mode of "update the GDD to the vision" is that
someone replaces `STARTING_DEBT = 6` prose with treasury prose, and six months later a
contributor implements against a document that describes a game that was never built.

So the GDD delta uses three moves and only three:

1. **Append.** A new `## Vision (planned)` section near the end, pointing at `docs/VISION.md`.
   Nothing above it is touched.
2. **Annotate.** Where a current statement is about to be superseded, add a one-line forward
   reference next to it — `> Planned: ... (see docs/VISION.md §4 D3, change f2-districts)`.
   The original statement stays, unedited, as the description of `develop`.
3. **Correct.** Only where the GDD is factually wrong about `develop` today. Each correction
   cites the file it was checked against.

Nothing else. In particular, the success criteria section is **not** rewritten: criterion #4
is the thing FV exists to measure, and softening it now would erase the reason FV exists.

## Structure of the openspec tree

```
openspec/
  config.yaml                    # project context, conventions, store policy
  project.md                     # where truth lives; program shape
  VERIFICATION-CHECKLIST.md      # claim -> source -> command
  changes/
    fv-core-validation/proposal.md
    f0-vision-program/{proposal,design,tasks}.md + specs/vision-program/spec.md
    f2-districts/{proposal,design,tasks}.md + specs/run-structure/spec.md
    f3-treasury/charter.md
    f4-ballast-cards/charter.md
    f5-zone-bosses/charter.md
    f6-events/charter.md
    f7-market/charter.md
    f8-leads/charter.md
```

**F1's folder is deliberately absent from this PR.** Its four artifacts were written on this
branch and F1 was implemented from them, shipping as `3a7c201` (PR #9) before this PR merged.
Carrying them here would land a snapshot that describes F1 as unstarted -- 20 unticked tasks
for work already on `develop`. They are reintroduced in their own PR as a record of what
shipped. `git log --oneline -S 'HarnessBands' develop -- app/src/test/` finds the code.

Delta specs use the OpenSpec convention (`## ADDED Requirements`, `### Requirement:` in SHALL
language, `#### Scenario:` in GIVEN/WHEN/THEN). There is no OpenSpec CLI in this repo — no
`package.json` exists — so the convention is upheld by review, not by a validator. Adding
`openspec validate` to CI is a reasonable follow-up and is deliberately **not** smuggled into
F0, because it would make a documentation phase depend on a toolchain decision.

## Why charters instead of specs for F3-F8

Every F3-F8 spec would be written against numbers that do not exist. The treasury's
calibration comes out of the harness, not out of prose; the ballast card counts come out of
the treasury; the boss intents come out of FV's verbs. Writing them now produces documents
that *look* ready for apply and are not, which is strictly worse than an honest one-pager.

This also matches how this repo has always worked: C1, C2, C4, C5, C7, C8, `card-upgrades` —
one change at a time, each calibrated on the last one's measured output.

## Engram mirror

One observation per artifact, topic `sdd/<change>/<artifact>`, `capture_prompt: false`
(these are automated SDD artifacts). Plus `sdd/vision-programa/done` carrying the program
summary, so a future session can find the whole thing from one search.

The mirror is written **after** the files, never instead of them. A memory write is not a
deliverable.
