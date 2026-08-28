# F1 — Tasks

Strict TDD. Every code task is RED before GREEN.

> **Blocking dependency, added 2026-08-28.** Every acceptance gate in this phase is a
> report-versus-report diff (§0, §5). That is only meaningful once the harness is
> deterministic, and on `develop` it is **not**: three sweeps of identical code returned
> 53.5% / 54.0% / 54.5% greedy win rate, because the card-choice comparators tie-broke on
> `CardInstance.instanceId`, a fresh `UUID.randomUUID()` per instance. The fix is
> `f02b421`, which ships in [PR #7](https://github.com/OAlvarezOliveira/DebtsAndDecks/pull/7).
>
> **Do not start F1 until PR #7 is merged.** Branching off today's `develop` means chasing
> a diff that reappears on every run and is not caused by your change.

## 0. Baseline capture (do this first, it is the acceptance evidence)

- [ ] 0.1 On the fork point, run `./gradlew test --tests '*RunSimulationHarnessTest*' --info`
      and save the full printed report to `/tmp/f1-baseline.txt`. Not in the repo — it is
      evidence for one PR, not an artifact.
- [ ] 0.2 Record in the PR body: greedy win rate, leverage win rate, both avg peak debts,
      won-run peak debt, defeat breakdowns. These are the numbers the change must reproduce.

## 1. HarnessBands

- [ ] 1.1 **RED** — `HarnessBandsTest`: at the current `EXECUTION_THRESHOLD`, the bands
      resolve to 25.0 / 45.0 / 25.0 / 35 / 45 / 25. Fails to compile — the object is absent.
- [ ] 1.2 **GREEN** — write `HarnessBands` with ratio constants and `get()`-derived values.
      KDoc names the anchor and states why it is `EXECUTION_THRESHOLD` and not
      `BREAK_THRESHOLD`.
- [ ] 1.3 **RED** — scale-proof test: resolve the bands against a stubbed execution line of
      100 and assert 50.0 / 90.0 / 50.0 / 70 / 90 / 50. Fails if the derivation froze at
      class-load.
- [ ] 1.4 **GREEN** — make the derivation live.

## 2. Repoint the assertions

- [ ] 2.1 `leverage policy comparison sweep`: both `avgPeakDebt in [25.0, 45.0)` assertions
      derive from `HarnessBands`.
- [ ] 2.2 `wonPeak > 25` derives from `HarnessBands.wonPeakMin`.
- [ ] 2.3 Leave `[0.35, 0.55]`, `< 0.70` and the one-sided 5pp gap **unchanged in value and
      direction**. Relocating them into the object is optional; altering them is not.

## 3. Repoint the policies

- [ ] 3.1 `LeveragePolicy.LEVERAGE_TARGET` derives from `HarnessBands.leverageTarget`.
- [ ] 3.2 `NodePolicy.SAFE_AFTER_LOAN` derives from `HarnessBands.safeAfterLoan`.
- [ ] 3.3 `NodePolicy.REPAY_BAND` derives from `HarnessBands.repayBand`.
- [ ] 3.4 Leave `LOAN_GOLD_NEED`, `THIN_DECK`, `THIN_NODE` alone. Add a one-line comment on
      `LOAN_GOLD_NEED` naming it a deferred F3 input.

## 4. Readable failures

- [ ] 4.1 `SimulationReport` exposes peak debt as a fraction of the execution line and prints
      both forms in `summary()`.
- [ ] 4.2 Band assertion messages carry absolute, ratio, and the violated bounds.

## 5. Zero-delta gate

- [ ] 5.1 Re-run the harness. Diff the report against `/tmp/f1-baseline.txt`.
- [ ] 5.2 **Identical, or the change is wrong.** No tolerance, no explaining away a
      one-seed difference.
- [ ] 5.3 `./gradlew test` — all tests green, and the total grows by exactly the
      number of new `HarnessBandsTest` cases and by nothing else.
      *Corrected 2026-08-28: the original text hardcoded 180, which was already
      stale — PR #7 adds cases. A count relative to the fork point cannot rot.*

## 6. Deliver

- [ ] 6.1 Branch `refactor/f1-harness-ratio-normalization` off `develop` **with PR #7
      merged**.
      *Corrected 2026-08-28: this said "the post-FV `develop`". FV cannot complete —
      its exit criteria E3/E4 need an external playtest, but `release` has no
      `signingConfig` and no keystore exists, so nothing is distributable. Gating F1
      on FV would block it indefinitely. The real prerequisite is PR #7, for the
      determinism fix; FV is independent of this phase.*
- [ ] 6.2 Commits: `test(harness): ...`, `refactor(sim): ...` — conventional.
- [ ] 6.3 PR body carries the before/after report side by side. That diff **is** the review.
- [ ] 6.4 Do not self-close. An independent pass re-runs 5.1 from the branch.
