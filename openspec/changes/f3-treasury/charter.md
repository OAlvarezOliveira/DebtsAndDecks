# F3 — Treasury (Life is Money) — CHARTER

> A charter, not a spec. It states intent, boundaries and what is still unknown. It is
> **not apply-ready and must not be treated as such.** The numbers this phase needs come out
> of the harness, not out of prose.

## Intent

Make the balance sheet the life of the run. The player starts owing, loses if the debt hits
the execution line, and loses if he cannot meet the monthly minimum when it falls due.

Per decision D1 this is **hybrid**: HP survives as physical integrity — what attacks damage,
what block absorbs — and the treasury is the run's life. Two axes, three ways to die.

## Inputs required

- **F1 complete. ✅ Met — shipped as `3a7c201` (PR #9, 2026-08-28).** The gate must be on
  ratios before the economy moves; it now is. Non-negotiable, and the whole reason F1 existed
  was to stand in front of this phase. Confirm before starting F3, do not take this line's
  word for it:
  `git show develop:app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt | rg LEVERAGE_TARGET`
  must read `get() = HarnessBands.leverageTarget`.
- **F2 complete.** The node/district rhythm defines when a "month" falls due (D2: one node,
  one month — seven due dates per run).
- **Card descriptions parametrized.** Every number on a card exists in four places today:
  `all.json`, `strings.properties`, `strings_es.properties`, and the illustration. Of the 27
  live cards, 23 have their number hand-written into the description in each language. The
  pattern to copy already exists in the same bundle for intents (`intent.levy=Levy {0} Debt`).
  This is prep work **inside F3**, done first, not a refactor to schedule later.
- **A decision on `NodePolicy.LOAN_GOLD_NEED`** (deferred from F1 with reason, and F1 did in
  fact defer it): it is a gold threshold with no honest anchor today. F3 touches the gold
  economy, so F3 picks the anchor. Still absolute on `develop` — `NodePolicy.kt:63` reads
  `private const val LOAN_GOLD_NEED = 20`, with the reason in the comment two lines below,
  while `SAFE_AFTER_LOAN` and `REPAY_BAND` beside it are now `get() = HarnessBands.…`.

## Outputs

- Monthly minimum, due at nodes, payable in cash. Default adds unpaid interest to principal
  and inserts ballast (the hook F4 consumes).
- Technical bankruptcy as a second defeat condition.
- **A UI contract distinguishing all three defeat causes.** Mandatory deliverable, not a
  follow-up: without it the logical-permadeath rule in the vision is unverifiable, and an
  unverifiable rule is not a rule. Note this lands on `renderRunEnd`, which is still
  placeholder art (`docs/ART-PIPELINE.md` §5) — F3 either fixes it or says out loud that it
  shipped a mandatory contract on a placeholder.
- A money presentation scale (D9): engine units unchanged, rendered as dollars in one place.

## Open decisions

1. **Starting pressure.** The vision's illustrative "-$58,000 of -$100,000" implies the run
   starts 58% of the way to execution. Today it starts at 12% (`STARTING_DEBT` 6 of
   `EXECUTION_THRESHOLD` 50). That is a **design** question, not a formatting one, and F3 owns
   it: does the run open under pressure, or does pressure build? Whatever the answer, it is
   measured on the harness, not argued.
2. **What the minimum is a fraction of.** D4 says recalibrated accrued interest. The
   recalibration factor is unknown until the sweep says so.
3. **Does defaulting cost HP?** If not, the physical axis is decorative in the late run. If
   yes, the two axes couple and the hybrid stops being two clean readings.
4. **Where the money scale constant lives.** A core constant is simplest; a render-layer
   constant keeps `core/` free of presentation. `docs/CONVENTIONS.md` §Architecture Rules
   decides this, not preference.

## Risk: HIGH — the highest in the program

This phase changes the resource every later phase spends. It adds a defeat condition, which
means it can make the game unfair in a way that is invisible to the harness (the sim never
reads the UI, so it cannot notice that a death was unexplained). And it carries the one
mandatory UI deliverable in the program.

Mitigation: land it in slices — presentation scale first (zero delta, provable), then the
minimum-payment mechanic, then the bankruptcy condition with its UI contract. Do not ship
three at once and calibrate the sum.

## Forecast

Well over 400 lines. **Expect three or four chained PRs.** Attempting one is how a phase this
central becomes unreviewable.
