# FV.E1 — Close E1 through the responding policy's draft, not the game's data

**Type:** short proposal with an explicit exit criterion. **No spec, no design, no tasks** —
same reason as its parent: if the measurement comes back bad, the next lever gets re-scoped
before it gets written.

**Status:** proposed, unverified. **Date:** 2026-08-29. **Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22).
**Continues:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E1.
**Depends on:** PR #22 landing or staying alive. **Blocks:** FV E1, and nothing else.

---

## 1. What is actually open

FV's exit criterion E2 is met on the branch. E1 is not, and it is the only gate left on the
verbs. E1 asks for a policy that responds to FORECLOSE/HEDGE to beat one that ignores them by
**≥ 10pp win rate over 200 seeds**. The best number reported so far is **+2.5pp**, after two
calibration sessions and seven behavioural variants of the test-only
`app/src/test/java/com/debtsdecks/core/simulation/RespondingPolicy.kt`.

Every one of those variants moved the same knob: *when* the policy borrows and *how* it plays
around a displayed FORECLOSE. None of them changed **what deck it drafts**. That is the gap
this proposal attacks.

## 2. The lever, and the part of it that is already spent

Verified on `develop` today, not taken on trust:

- The card pool is **27 cards** (`app/src/main/assets/cards/all.json`, 27 `"id"` keys).
- Exactly **one** card repays debt by amount: `partial_forgiveness`, `debtRepay: 8`, cost 0,
  COMMON, `tags: []`.
- Exactly **two** cards carry the `wipe_debt` tag: `debt_forgiveness` (cost 2, RARE) and
  `tactical_bankruptcy` (cost 1, `selfDamage: 8`, RARE). Both wipe Debt to zero —
  `CardResolver.kt:287` maps the tag to `Effect.WipeDebt`, on a code path entirely separate
  from `debtRepay`.
- `HarnessBands` resolves E2's leverage-debt band to `[0.50, 0.90) × 50 = [25, 45)` and
  `LeveragePolicy.LEVERAGE_TARGET` to `35`. FORECLOSE's threshold of **27 sits inside that
  band, near its low edge**.

So a policy that only looks at `debtRepay > 0` can see **1 card in 27** (~3.7% draft odds) and
is structurally blind to the two cards that actually answer a FORECLOSE deadline.

**The honest half of this: part of that fix may already exist and already be a no-op.** The
2026-08-29 re-verification (Engram #1710) records adding a "`wipe_debt`-before-`debtRepay`
check in the reactive branch", committed as `7eff86c` on the branch, with *no measurable
effect*. The exploration pass that found the blindness (Engram #1712) read
`RespondingPolicy.kt` from `.git/gentle-ai/candidate-views/`, which are frozen review
snapshots that predate `7eff86c` — so its "never references `wipe_debt` anywhere" finding is
**stale for `chooseAction` and untested for `chooseReward`**.

Neither record claims the **`chooseReward` priority comparator** was ever touched. That
comparator is where `wipe_debt` cards currently fall through to the filler tier, below
`debt_payoff` (which scales damage with debt and answers a deadline not at all). A card the
policy never drafts is a card the reactive branch can never play, which is a complete
explanation for why fixing only the play side measured as nothing.

## 3. What this proposal does

1. **Read `RespondingPolicy.kt` as it stands at `7eff86c`** and state, in the results doc,
   which halves of the `wipe_debt` fix are already there. Do not re-do committed work, and do
   not repeat a measurement already taken.
2. **Fix the untried half**: give `chooseReward`'s comparator a `wipe_debt` tier ranked above
   `debtRepay > 0` and above `debt_payoff`, and complete the `chooseAction` defensive branch if
   `7eff86c` left it partial — respecting `tactical_bankruptcy`'s `selfDamage: 8` HP check.
3. **Re-measure E1 for real**: 200 seeds, the same `IntentVerbsE1Test` methodology as the
   seven prior variants, responding vs ignoring, number written down whether or not it passes.

Nothing here touches `cards/all.json`, `enemies/all.json`, `DebtConfig` or `CombatEngine`.
It is a test-fixture change, so **E2 cannot move** and `EnemyTierRegressionTest` cannot move.
That is the whole reason it goes first.

## 4. Exit criterion

**Pass:** the responding policy beats the ignoring policy by **≥ 10pp over 200 seeds**, E2
still green in the same run, and the number is in `docs/BALANCE-BASELINE.md` with the command
that produced it.

**Fail — and this is a real, expected outcome.** If the measured gap stays under 10pp, this
change **stops** and reports the number. It does not weaken `IntentVerbsE1Test` to manufacture
green; the 2026-08-28 re-metric already did that once and it is still on the branch as a
known debt. The failure hands the owner a scoped choice between two levers that are **out of
scope here** and each need their own proposal:

- **(a) Tune FORECLOSE's threshold/fee or HEDGE's divisor.** Narrow safe room: 27 already sits
  inside E2's `[25, 45)`. Raising it toward `LEVERAGE_TARGET` 35 makes the ignoring policy
  dodge more easily — the gap can get *worse*. Lowering it starts catching low-debt play and
  threatens `greedy.winRate in 0.35..0.55`. Any move needs simultaneous E2 re-verification.
- **(b) Buff or add debt-reduction cards.** Real lever — 3 answers in 27 is a thin archetype —
  but the pool feeds *both* policies' `chooseReward`, so it can lift both win rates uniformly
  and force a full balance re-sweep.

Recording the failing number is a complete deliverable. E1 being unreachable on the current
card pool is information FV was built to produce.

## 5. Two facts to check before starting

Both are claims from Engram that this working tree contradicts, so verify rather than assume:

- Engram #1711 says E1 was documented as unreachable in `fv-core-validation/proposal.md` §4 via
  commit `6284707` pushed to `develop`. **The file on this checkout contains no such note.**
  `git log --oneline -S'unreachable' develop -- openspec/changes/fv-core-validation/`.
- Engram #1697 says PR #22 was merged as `c9d6fe1` in a separate worktree; #1706 and #1708 say
  it is still WIP with a conflicting merge base. `gh pr view 22 --json state,mergedAt`.

## 6. Review workload forecast

One test file plus one results-doc section. Estimate **40–90 lines**. Single PR.
`Decision needed before apply: No`. `Chained PRs recommended: No`.
`400-line budget risk: Low`.
