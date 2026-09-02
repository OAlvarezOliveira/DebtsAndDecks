# sdd-verify — verification report

## Command (verbatim from tasks.md 1.11 / design Testing Strategy)
```
./gradlew :app:testDebugUnitTest --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i
```

## Result
- `BUILD SUCCESSFUL in 8s` (warm Gradle daemon + compile cache; `--rerun-tasks` forced re-execution).
- JUnit: `tests="2" skipped="0" failures="0" errors="0"`. Both methods pass:
  - `the probe observes without perturbing the run it measures` (0.395s)
  - `repay affordability at every loan_shark opportunity holds its measured floor` (0.110s)

## Reproduced measurement (printed STANDARD_OUT — exact match to `docs/BALANCE-BASELINE.md`)
```
slot=2 reached=200 affordable=2 (1.0%) alreadyRepaidByLadder=0 headroom=2
slot=4 reached=155 affordable=20 (12.9%) alreadyRepaidByLadder=0 headroom=20
slot=5 reached=131 affordable=6 (4.6%) alreadyRepaidByLadder=1 headroom=5
AGGREGATE reached=486 affordable=28 (5.8%) alreadyRepaidByLadder=1 headroom=27  bar: >=30% and one slot >20% -> FAIL
mirror assertions=1356; loan-armed BREAK rematches observed=7
```

## Verdict against proposal §6.4 (owner-closed bar)
- Aggregate **5.8%** vs **≥30%** → FAIL.
- Best single slot **12.9%** (slot 4) vs **>20%** → FAIL.
- Both halves fail independently — no re-run / seed extension / slot re-selection changes the answer.
- **Headroom 27/28 non-zero** → design Open Question 2 (hook has no room because `NodePolicy.kt:45`
  already repays) does **not** apply. The hook would have had room; there is almost nothing affordable to act on.

## Mechanical proofs (not asserted claims)
- **Mirror vs engine (D2):** 1356 combat-start assertions `engine.getState().enemies.first().defId ==
  RunSlotCursor.expected` across all 200 seeds. 7 runs armed the BREAK rematch from a node LOAN *inside*
  `NodePolicy.act` (after the flag was sampled false) — the `loanArmedBreak` term's load-bearing case
  (deleting it breaks seed 15: `expected loan_shark, was collector`).
- **Non-perturbation (D3):** per seed, the probe drive loop equals `RunSimulator(policy = RespondingPolicy)
  .simulate(seed)` on `outcome` / `peakDebt` / `endHp` / `defeatEncounterId` / node count / per-combat turn
  counts. The probe never calls `repayViaNode()`; affordability is read from `gold` / `debt` / `nodeIndex`
  (which is `repayViaNode`'s own guard).

## Review / judgment blockers
- **None for phase one:** zero `app/src/main` changes, zero behaviour changes, `IntentVerbsE1Test` untouched
  (§7 non-negotiable preserved), no enemy HP / damage / `HarnessBands` ratio / `DebtConfig.EXECUTION_THRESHOLD`
  moved.
- **Gate 1.16 blocks phase two:** a recorded FAIL means tasks 2.x must not start. The direction stops here
  (proposal §4, tasks 1.13 / 1.16).

## Why it fails (two verified mechanisms, now with numbers)
1. **Cost.** `repayViaNode()` charges `debt + escalatedCost(3, nodeIndex)`; the debt band policies live in is
   `[25,45)`. At node 2 that is ~29–39 gold in one payment against a garnished gold pile → only 2 affordable
   of 200. Slot 4 is best (12.9%) because it sits latest on the gold curve while its fee (10) is still below
   slot 5's (15).
2. **Garnishment.** `MAX_GARNISH_RATE = 0.6` taxes up to 60% of every combat's gold reward toward debt,
   ramping to the cap at `BREAK_THRESHOLD = 30`. Gold accumulation is explicitly out of scope (§6.6) → its own
   proposal if ever revisited.

Independent corroboration: `alreadyRepaidByLadder = 1 in 486` — the existing `NodePolicy.kt:45` repay rung
fires essentially never at these nodes, reproducing §7's F5 "repay rule is dormant" finding from a different
measurement.
