# sdd-init — fv-e1-node-response-channel (phase one: the probe gate)

## Context initialized
- Change dir: `openspec/changes/fv-e1-node-response-channel/` (proposal type: short, two-phase; no spec/design
  change at spec level — phase one is a measurement probe, phase two is test-source only).
- `openspec/config.yaml` already exists → artifact store is **BOTH** git tree (`openspec/`) and Engram
  (`sdd/<change>/<artifact>`, project `debtsanddecks`). No config creation required.
- Chain: `sdd-verify` (init → apply → verify → sync → archive) over an already-planned change.

## Pre-existing parent artifacts (read, not regenerated)
- `proposal.md`, `design.md`, `tasks.md` all present. `tasks.md` shows 1.1–1.16 checked; gate 1.16 recorded **FAIL**.
- Probe code committed in `c4ae993` (`RunSlotCursor.kt` + `NodeRepayAffordabilityProbeTest.kt`). Its commit
  message says "tasks 1.1-1.5", but the file is the **full** probe — verified: `git diff HEAD` on the two
  test files is empty and the class implements 1.6–1.16.
- `docs/BALANCE-BASELINE.md` carries the FAIL section (working tree, **uncommitted**); reproducible by the
  suite below.

## Scope for this chain
- Phase one only. Phase two (2.x — the `respondToNode` hook) is gated on a recorded **PASS** (gate 1.16) and
  must NOT start. This chain does not touch 2.x.
