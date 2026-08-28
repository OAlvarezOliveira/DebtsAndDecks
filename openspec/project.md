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
| Whether the game is fun | **nothing yet** — never measured (see `fv-core-validation`) | the harness |

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

`FV -> F0 -> F1 -> F2 -> F3 -> F4 -> F5 -> F6 -> F7 -> F8` is the **planned** order, and it is
already out of date: **F2 PR1 merged into `develop` as `6b50164` on 2026-08-28, before F1
started**, because F2 PR1 carried the harness determinism fix that F1's own acceptance argument
depends on. The sequence is a default, not a constraint. The constraints are the **Depends on**
and **Blocks** lines in each proposal; read those, not this arrow. See `docs/VISION.md` for the
reasoning and `openspec/changes/<phase>/` for the artifacts. This batch specs FV (short
proposal), F0, F1 and F2 in full; F3-F8 are one-page charters on purpose.
