# F1 — Normalize the balance gate to ratios

**Status:** proposed, unverified. **Date:** 2026-08-28.

> **Blocked until PR #7 merges (added 2026-08-28).** This phase's entire acceptance argument
> is "the harness report is identical before and after". On `develop` the harness is
> non-deterministic, so that sentence has no meaning: two runs of the same code disagree.
> `f02b421` fixes it. Starting F1 before that merges means fighting noise.
**Depends on:** FV deliverable 1 (the new intent verbs re-baseline the numbers F1 anchors).
**Blocks:** F3. Nothing may re-scale the economy while the gate is coupled to the old scale.

## Why

`RunSimulationHarnessTest` is the balance gate of this project. Its invariants are
**absolute numbers**, and they are silently anchored to `EXECUTION_THRESHOLD = 50`:

| Assertion (as it stands in the test file) | What it means today |
| --- | --- |
| `greedy.avgPeakDebt in [25.0, 45.0)` | between 50% and 90% of the execution line |
| `leverage.avgPeakDebt in [25.0, 45.0)` | same |
| `wonPeak > 25` | winners go past half the execution line |

Re-scale the economy and those three keep passing — or keep failing — for reasons that no
longer mean anything. A gate that survives the thing it was built to catch is worse than no
gate, because it produces confidence.

## The part the brief did not cover, and it is the worse half

The simulated players are scale-coupled too. As the test sources stand — checklist row B1
carries the command that confirms it:

| Constant | File | Value | As a fraction of `EXECUTION_THRESHOLD` |
| --- | --- | --- | --- |
| `LeveragePolicy.LEVERAGE_TARGET` | `LeveragePolicy.kt:25` | 35 | 0.70 |
| `NodePolicy.SAFE_AFTER_LOAN` | `NodePolicy.kt:64` | 45 | 0.90 |
| `NodePolicy.REPAY_BAND` | `NodePolicy.kt:65` | 25 | 0.50 |

These decide **how the simulated player behaves**: how deep it borrows, when it refuses a
loan, when it starts repaying. A broken assertion fails loudly. A broken *policy* keeps
running and reports a number about a player nobody would recognize. If F3 moves the scale
and these stay at 35/45/25, `LeveragePolicy` stops leveraging and the harness cheerfully
measures a stranger.

Normalizing the assertions without normalizing the policies would fix the alarm and leave the
sensor unplugged.

## What changes

1. One place — a `HarnessBands` object in the test source set — holding every band as a
   **ratio**, with the anchor it is a ratio *of* named explicitly.
2. The harness assertions derive their thresholds from it.
3. `LeveragePolicy` and `NodePolicy` derive their debt thresholds from it.
4. `SimulationReport` prints peak debt as both an absolute and a fraction of the execution
   line, so a failure is readable at any scale.

## What explicitly does not change

- **No gameplay.** No file under `app/src/main/` changes except, at most, a KDoc line.
- **No numeric behaviour.** `0.50 × 50 = 25.0` and `0.90 × 50 = 45.0` exactly. The sweep must
  produce **identical results on identical seeds** before and after. That is the acceptance
  test, and it is checkable by diffing the printed report.
- **Win-rate invariants are left alone.** `[0.35, 0.55]`, `< 0.70` and the 5pp policy gap are
  *already* ratios. Touching them would be change for its own sake.
- **Gold-scale constants are deferred, on purpose.** `NodePolicy.LOAN_GOLD_NEED = 20` is
  coupled to the gold economy, not to the execution line, and there is no honest anchor for
  it today. Inventing one to look thorough would either change behaviour (breaking the
  zero-delta property) or produce a contrived formula. It is listed as a required input to
  F3, where the gold economy is actually on the table.

## A correction to record

The brief describes the policy gap as "gap ≤ 5pp". The assertion in the file is
`leverage.winRate + 0.05 >= greedy.winRate` — **one-sided**. It forbids the leverage policy
winning much *less* than greedy; it permits it winning arbitrarily *more*. That asymmetry is
deliberate (it is the "borrowing must not be a trap" invariant, not a "policies must be
equivalent" invariant) and F1 preserves it verbatim rather than quietly making it symmetric.

## Risk

**Low, with one sharp edge.** The edge is that this change must be behaviour-preserving and
the only proof of that is the sweep output. If the report differs by a single seed, the
change is wrong and must not be argued into acceptance.

Second-order risk: F1 depends on FV. If FV's verbs move the win band, F1's ratios must be
computed from the **post-FV** baseline. Normalizing against today's numbers and then landing
FV would bake a stale anchor into the gate.

## Review workload forecast

~180-250 lines, entirely in `app/src/test/`. Single PR.
