# F2 — Districts

**Status:** PR1 merged; PR2 not started. **Date:** 2026-08-28.

> **PR1 shipped and merged 2026-08-28** as
> [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7), squash-merged into
> `develop` as **`6b50164`** — cite that, not the branch commits, which are not on the
> trunk's history. It carried the districts, the harness determinism fix (without which
> this phase's zero-delta gate could not mean anything), and the loader rejection of an
> unknown `districtId`. **Every PR1 task is now done, 2.4 included.** See `tasks.md` for
> what shipped and for the shipped names, which win over the planned ones.
>
> **PR2 — design system, art, render — is not started.**
**Depends on:** F0 (the vision names the districts). **Blocks:** F5 (bosses need zone seats).
**Balance delta:** zero, and provably so. That is the whole point of putting it here.

## Why

The run is currently a corridor of eight numbered slots. The vision is a city. The gap
between those two is not mechanics — it is **metadata and a background image**.

That makes this the cheapest phase in the program with the highest visible return, and the
only structural phase that can be shipped without touching balance at all. It also creates
the seats that F5's bosses will sit in, so doing it before the treasury means F3 calibrates
against the structure the game will actually keep.

## What changes

**Structure.** The 8 slots are grouped into 3 districts, 3 + 3 + 2, with the last slot of
each district marked as its boss seat. The enemy in every slot and the reward on every slot
are **unchanged**:

| District | Slots | Enemies (unchanged) | Boss seat |
| --- | --- | --- | --- |
| The Slaughterhouse of the Insolvent | 1-3 | thug, thug, loan_shark | 3 |
| The Vulture Funds Casino | 4-6 | thug, loan_shark, loan_shark | 6 |
| The Boardroom | 7-8 | collector, collector | 8 |

Three of the ten districts from the interactive-fiction mould, chosen because they already
match the enemy escalation the sequence has: street predation, then professional predation,
then the institution.

**Data.** A district catalog at `app/src/main/assets/districts/all.json` — the same shape of
relationship the repo already uses between `enemies/all.json` (a catalog) and `sequence.json`
(the authority). Slots gain `districtId` and `role`.

**Presentation.** Each district gets a background, a name, and a one-line descriptor, shown
on entry and on the node screen. All prose lives in the i18n bundles as keys.

## What does not change

- The number of combats. Eight, decided by the owner. Re-length is a separate, later change.
- Any enemy, any reward, any economy constant.
- The `RunManager.Phase` enum. **No new phase.** F2 adds no `EVENT` or `MARKET` — those are
  F6 and F7, and each of them has to pay for **five** `when (phase)` sites, of which only
  **three** are exhaustive: `CombatInputHandler.kt:34`, `GameScreen.kt:43` and
  `RunSimulator.kt:71` fail to compile, while `CombatInputHandler.kt:213` (`else -> Unit`)
  swallows the new phase silently and `NodePolicyTest.kt:32` (`else -> error(...)`) fails only
  at runtime. *Corrected 2026-08-28: this originally said "four exhaustive sites" and listed
  `NodePolicyTest.kt:32` among them, which would have let F6/F7 trust the compiler for a case
  it does not cover. See `tasks.md` 7.5 for the command and the table.* F2 pays none of it,
  because it introduces no phase.

## Acceptance: zero delta, measured

`RunSimulator` loads the sequence through `TestAssetLoader.loadSequence()`, so it is agnostic
to added metadata but **not** to the number of slots. Since F2 changes only metadata, the
200-seed sweep must produce an **identical report** before and after. Same gate as F1: diff
the output, and any difference means the change is not what it claims to be.

## The two debts this phase has to declare

**Art.** F2 generates three new backgrounds. Before generating a single asset, the fix the
pipeline already wrote down (`docs/ART-PIPELINE.md:90` — regenerate with an explicit "no text,
no lettering, no numbers" instruction) must be applied to the generation prompt. Otherwise a
phase that adds art *grows* the 15-of-27 baked-text debt instead of shrinking it.

F2 **pays** the background sizing debt (§3.6 of the pipeline: existing backgrounds are
undersized) for its own three assets, generated at the correct resolution.
F2 **carries** the card-illustration text debt (§3.1, 15/27) — it touches no card art. That
debt is assigned to F5, which regenerates art anyway and is where paying it is nearly free.

**Design system.** The UI style guide is a ZIP at `Arts/Debts & Decks Design System.zip`, and
`Arts/` is entirely gitignored — `git ls-files Arts/` returns nothing. F2 is the first phase
to add a UI surface, so it is the phase that has to stop pretending that dependency exists.
The fix is not to commit the binary: it is to extract the tokens it defines (palette, type
scale, spacing, the district-card treatment) into a tracked `docs/DESIGN-SYSTEM.md`. A style
guide no other machine can read is not a style guide.

## Risk

**Low.** The only real hazard is scope creep: "while we are in there" is how a zero-delta
phase acquires a balance delta. The acceptance gate exists precisely to catch that.

## Review workload forecast

Data + model + loader ~120 lines · renderer wiring ~100 lines · i18n ~30 keys × 2 languages ·
tests ~150 lines · `docs/DESIGN-SYSTEM.md` ~80 lines. **~500 lines plus 3 binary assets.**

Over 400 — split into two chained PRs:
- **PR1 `feat(run): district model + data + zero-delta proof`** — model, catalog, sequence
  metadata, tests, sweep diff. No UI, no art.
- **PR2 `feat(ui): district identity`** — backgrounds, names on screen, i18n,
  `docs/DESIGN-SYSTEM.md`.

PR1 is the one that carries risk and it reviews as pure data. PR2 is art and strings.
