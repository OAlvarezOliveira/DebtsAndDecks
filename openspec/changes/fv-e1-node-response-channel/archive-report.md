# sdd-archive — archive decision

## Chain gating
- Verification succeeded (verify-report.md): probe runs green, the FAIL is reproducible.
- File-backed sync not applicable (sync-report.md): no spec deltas.
- Therefore the archive step's precondition (verification succeeds AND sync complete-or-N/A) is met.

## Decision: archive NOT performed
- This repo keeps changes in `openspec/changes/`; there is **no `openspec/changes/archive/` directory**, and
  every sibling FV.E1 lever (`fv-e1-leverage-temporal-deadline`, `fv-e1-card-pool-expansion`,
  `fv-e1-foreclose-hedge-tuning`, `fv-e1-wipe-debt-response`) lives un-archived there even though spent /
  measured. A recorded FAIL is this change's terminal state (direction stopped, proposal §4 / §6.4) —
  consistent with that convention.
- Creating an `archive/` dir and moving this change would diverge from established practice and from the
  repo's hand-maintained OpenSpec layout (config.yaml notes "no OpenSpec CLI; layout followed by hand").
- The change stays in `openspec/changes/fv-e1-node-response-channel/` as a recorded FAIL.

## What this means
- Phase-one gate 1.16 satisfied as **FAIL**: phase two (2.x — the `respondToNode` hook) must not start. The
  fifth FV.E1 lever is spent.
- The probe ships permanently as a green regression canary (D7 floor canary `aggregate >= 5.8% − 5pp`); never
  red, never `@Disabled`, never deleted; `IntentVerbsE1Test` untouched.
- The deliverable (the number) is recorded in `docs/BALANCE-BASELINE.md`; owner commits / pushes per tasks 1.15.
