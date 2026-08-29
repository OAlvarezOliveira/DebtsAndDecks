# sdd-apply — progress (tasks already implemented)

The apply step found tasks 1.1–1.16 already implemented and (mostly) committed; nothing to redo.

## Strict TDD evidence (verified by running the suite — see verify-report.md)
- Both `@Test` methods present and passing: `tests="2" skipped="0" failures="0" errors="0"`, `BUILD SUCCESSFUL`.
- The shipped assertion is the design-D7 floor canary (`aggregate >= MEASURED_AGGREGATE - 0.05`); the §6.4 bar
  is computed and printed every run but is no longer the asserted guard (it measured FAIL).

## Task-by-task status (read from code, matches tasks.md)
- 1.1 `RunSlotCursor.kt` (`BREAK_REMATCH_ENEMY_ID`, `nextEnemyId`) — present.
- 1.2 `advance()` `loanArmedBreak` edge case — present (load-bearing: removing the term breaks seed 15).
- 1.3 Probe class, 200 seeds, `policy = RespondingPolicy` — present.
- 1.4 In-loop mirror assertion (`engine.defId == cursor.expected`) — present (1356 assertions).
- 1.5 Read-only affordability (never calls `repayViaNode()`) — present.
- 1.6 `alreadyRepaidByLadder` via `run.debt == 0` after `act` (D5) + `headroom` — present.
- 1.7 `println` per-slot table **before** the asserts — present.
- 1.8 Non-perturbation equality test vs `RunSimulator(policy = RespondingPolicy).simulate(seed)` — present.
- 1.9 Health: `reachedTotal > 0`, rates in `0.0..1.0` — present.
- 1.10 / 1.13 Gate: §6.4 bar computed + printed; shipped assertion = D7 floor canary — present.
- 1.11 Real suite run — executed in verify-report.md, reproducible.
- 1.12 FAIL recorded in `docs/BALANCE-BASELINE.md` — present (uncommitted working tree).
- 1.13 Floor canary ships green; not red, not `@Disabled`, not deleted; `IntentVerbsE1Test` untouched — confirmed.
- 1.14 Not applicable (this is a FAIL, not PASS-with-zero-headroom) — noted.
- 1.16 STOP gate: recorded FAIL → phase two does not start — enforced.

## Outstanding (not this chain's to commit)
- `docs/BALANCE-BASELINE.md` FAIL section and this `openspec/changes/fv-e1-node-response-channel/` dir are
  uncommitted working tree / untracked. Push + PR left to the owner per tasks 1.15.
