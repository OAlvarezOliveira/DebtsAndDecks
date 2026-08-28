# Project context — Debts & Decks

## What this is

An Android roguelike deck-builder (Kotlin + LibGDX) about surviving debt in a noir city.
The player is Alistair Vance, ex-appraiser at Liquidations. A run is 8 combats, 12-18
minutes, and the thesis of the current build is **debt is leverage**: every attack gets
`+floor(debt / 6)` damage, so borrowing is a weapon and the execution threshold is the
price of using it.

## Where the truth lives

| Question | Source | Not |
| --- | --- | --- |
| What the economy does | `app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt` | the GDD |
| What a card does | `app/src/main/assets/cards/all.json` | the card art, the description string |
| What the run looks like | `app/src/main/assets/run/sequence.json` | the GDD's prose |
| Whether balance holds | `RunSimulationHarnessTest` (200 seeds, 2 policies) | anyone's judgement |
| Whether the game is fun | **nothing yet** — no measurement of it is recorded in this repo (checklist row B5; see `fv-core-validation`) | the harness |

The last row is the reason the program starts with a validation phase instead of a feature.

## Non-negotiables

- **Strict TDD.** Red, then green. Tests are never edited to pass.
- **`docs/CONVENTIONS.md` wins** over any shortcut a design proposes.
- **Data files hold keys, prose holds text.** No literals in JSON or in Kotlin.
- **No text in art.** Objective norm; 15/27 card illustrations currently violate it and
  contradict `all.json`. Any phase that touches art declares whether it pays that debt
  down or carries it forward.
- **The harness is the balance gate.** A design that cannot be measured there is not a
  design, it is an opinion.

## Program shape

`FV -> F0 -> F1 -> F2 -> F3 -> F4 -> F5 -> F6 -> F7 -> F8` is the **planned** order, and the
trunk has already executed a different one. What actually merged, in order:

| Commit | PR | What |
| --- | --- | --- |
| `6b50164` | #7 | F2 PR1 — districts, plus the harness determinism fix |
| `3a7c201` | #9 | F1 — balance bands normalized to ratios |
| `c8ebcd3` | #10 | tracking docs |

So **F2 shipped before F1, and both shipped before F0** — this document's own phase. F2 PR1
went first because it carried the determinism fix that F1's acceptance argument ("the report
is identical before and after") needs in order to mean anything. F1 went next because nothing
blocked it. F0 is last because documentation blocks nobody, which is the opposite of what
this program's `Blocks:` lines predicted.

The sequence is a default, not a constraint. The constraints are the **Depends on** and
**Blocks** lines in each proposal; read those, not this arrow — and treat them as claims that
can be falsified, because two of them already were. Verify the table with
`git log --oneline develop -3`. See `docs/VISION.md` for the reasoning and
`openspec/changes/<phase>/` for the artifacts.

This batch specs FV (short proposal), F0 and F2 in full; F3-F8 are one-page charters on
purpose. **F1's folder is not here**: it was written, F1 shipped from it as `3a7c201`, and the
snapshot on this branch still describes F1 as unstarted, so carrying it would write a false
project state into the tree on day one. It returns in its own PR, reconciled to what shipped.
