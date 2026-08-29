# Tasks: Node-Level Response Channel (probe first, hook second)

Two phases, two PRs, strictly sequential. **Phase one is a measured gate**, not a warm-up: phase two
does not start until phase one's run is recorded as a PASS in `docs/BALANCE-BASELINE.md`. Proposal §6's
seven owner decisions and design's D1–D7 are closed, including the §5.3 proof restated by design D6 /
Deviation 3 (`LeveragePolicy.kt` byte-identical; `ScriptedPolicy.kt` changed only in the `RunPolicy`
interface hunk, zero lines inside `object ScriptedPolicy`). Nothing below reopens any of it.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | Phase 1: 90–150 · Phase 2: 60–110 (design estimates + doc sections) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Two independent PRs on `feat/fv-verbs-foreclose-hedge` (PR #22): phase 1, then phase 2 — not a stack; phase 2 only exists if phase 1 passes |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Probe + slot mirror measure `repayViaNode()` affordability at the three `loan_shark` opportunities; number recorded pass or fail | PR 1 | `./gradlew :app:testDebugUnitTest --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i` | Full 200-seed drive loop inside the probe itself (`RespondingPolicy`) | `git rm` of `RunSlotCursor.kt` + `NodeRepayAffordabilityProbeTest.kt`; the doc section stays either way |
| 2 | Hook wired, E1/E2 pair measured in one pass, §5.3 byte-identity proven | PR 2 | `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest' --tests '*HarnessDeterminismTest' --tests '*EnemyTierRegressionTest' --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i` | Same command — E1 and E2 read from one invocation (proposal §5) | `git revert` of the single change commit; the defaulted method and defaulted param mean nothing else needs touching |

## Phase 1: The probe — measure before wiring anything

- [x] 1.1 Create `app/src/test/java/com/debtsdecks/core/simulation/RunSlotCursor.kt` (D1): holds
      `slotIndex` (starts 0) and `breakSeen`, takes the same `RunSequence` the simulator loads
      (`TestAssetLoader.loadSequence()`), exposes `BREAK_REMATCH_ENEMY_ID = "collector"` (mirrors
      `RunManager.kt:316`) and `nextEnemyId(pendingBefore) = if (pendingBefore) BREAK_REMATCH_ENEMY_ID
      else sequence.slots.getOrNull(slotIndex + 1)?.enemyId`, transcribed from `advanceToNextCombat`
      (`RunManager.kt:305–327`). No `app/src/main` change — `slotIndex` stays private.
- [x] 1.2 In `RunSlotCursor.advance(...)` implement the BREAK-rematch edge case design found:
      `loanArmedBreak = !breakSeen && !pendingBefore && run.debt > debtBefore && run.debt >=
      DebtConfig.BREAK_THRESHOLD`; then `if (pendingBefore || loanArmedBreak) breakSeen = true else
      slotIndex++`. Rationale in a code comment: a node LOAN arms the rematch *inside* `NodePolicy.act`
      (`RunManager.kt:276–279`) after `pendingBreakEncounter` was sampled false, and `takeLoan` is the
      only node action that raises debt, so the test is exact.
- [x] 1.3 Create `app/src/test/java/com/debtsdecks/core/simulation/NodeRepayAffordabilityProbeTest.kt`
      — class `com.debtsdecks.core.simulation.NodeRepayAffordabilityProbeTest`, JUnit 5, seeds
      `0L until 200L`, `policy = RespondingPolicy` — with a private drive loop transcribed from
      `RunSimulator.simulate` (`RunSimulator.kt:55–113`), house precedent `RunObservationTest.runTrace`.
      It calls `NodePolicy.act(run, RespondingPolicy)` unchanged and owns no mutator of its own.
- [x] 1.4 Wire the mirror-vs-engine assertion (D2) in the drive loop **before** trusting any counter:
      at every combat start assert `engine.getState().enemies.first().defId == cursor.expected`, across
      all 200 seeds (~1400 assertions). A silent off-by-one would target the wrong node and still print
      plausible numbers.
- [x] 1.5 Count affordability read-only (D4): at each NODE, before `NodePolicy.act`, sample
      `pendingBefore` / `debtBefore` and compute `affordable = run.debt > 0 && run.gold >= run.debt +
      NodeConfig.escalatedCost(NodeConfig.REPAY_FEE_BASE, run.nodeIndex)`. **Never call
      `run.repayViaNode()`.** Only nodes whose mirrored `nextEnemyId == "loan_shark"` (slots 2/4/5;
      fees 4/10/15) increment `reached` — proposal §6.4's denominator.
- [x] 1.6 Record `alreadyRepaidByLadder` by observing `run.debt == 0` after `NodePolicy.act` returns
      (D5) and report `headroom = affordable − alreadyRepaidByLadder` per slot. Do not re-implement the
      `NodePolicy.kt:45` rung guard — a second source of truth would drift silently.
- [x] 1.7 `println` the per-slot table **before** the asserts so numbers survive a failing run:
      `slot=2|4|5 reached=N affordable=A (x.x%) alreadyRepaidByLadder=R headroom=A-R`, then
      `AGGREGATE reached=ΣN affordable=ΣA (x.x%)  bar: >=30% and one slot >20% → PASS|FAIL`.
- [x] 1.8 Add the non-perturbation proof as a second test method
      `` `the probe observes without perturbing the run it measures` `` (D3): per seed, assert
      `outcome`, `peakDebt`, `endHp`, `defeatEncounterId` and node count equal
      `RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(seed)`. This is what keeps
      the transcription from drifting and proves the probe measures the shipped run.
- [x] 1.9 Add the health assertions: `reachedTotal > 0` and every per-slot rate within `0.0..1.0`.
- [x] 1.10 Add the gate assertion in
      `` `repay affordability at every loan_shark opportunity clears the go-no-go bar` ``:
      `affordableTotal.toDouble() / reachedTotal >= 0.30` **and** `max(perSlotRate) > 0.20`
      (proposal §6.4, closed). Do not soften the bar to fit the measurement.
- [x] 1.11 Run exactly
      `./gradlew :app:testDebugUnitTest --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i`
      and capture the full per-slot table from stdout — never infer the numbers from green/red alone.
- [x] 1.12 Append an FV.E1 section to `docs/BALANCE-BASELINE.md` in the format used by the four spent
      levers: per-slot `reached / affordable / rate / alreadyRepaidByLadder / headroom`, the aggregate
      rate, the denominator of reached opportunities, the verbatim command from 1.11, and the explicit
      verdict `PASS` or `FAIL` against §6.4. The number is the deliverable in both directions.
- [x] 1.13 **If FAIL:** keep the probe shipped (decision §6.7) and swap the §6.4 bar for the floor
      canary `aggregate >= measured − 5pp` (D7). Do not ship it red, do not `@Disabled` it, do not
      delete it, and do not touch `IntentVerbsE1Test`. Record the miss and stop — a complete outcome.
- [x] 1.14 **If PASS but `headroom` ≈ 0 at every slot** (design Open Question 2): the `:45` rung
      already repays every time, so the hook has no room and phase two is dead on arrival. Record that
      in `docs/BALANCE-BASELINE.md` and stop. It is a fail of the direction, not a reason to move the bar.
- [x] 1.15 Deliver phase one on its own PR against `feat/fv-verbs-foreclose-hedge`: `test(sim):` for
      the probe + cursor, `docs(balance):` for the record. Phase one's whole value is being mergeable
      and stoppable alone (proposal §11).

## Phase 1 Gate — STOP HERE

- [x] 1.16 **STOP. No task numbered 2.x starts until the phase-one run from 1.11 is recorded in
      `docs/BALANCE-BASELINE.md` as an explicit PASS** — aggregate ≥ 30% of *reached* opportunities
      **and** at least one slot > 20% (§6.4) **and** non-zero `headroom` (1.14). A recorded FAIL, a
      missing/unwritten record, a partial run, or headroom ≈ 0 **ends this change here**. This is a
      real measured gate, not a formality: do not start 2.1 on an expectation, on a re-run whose
      numbers were never written down, or on a phase-one PR that has not landed its doc section.

### Gate result — recorded 2026-08-29: **FAIL**. Phase two does NOT start.

Command (1.11): `./gradlew :app:testDebugUnitTest --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i`

```
slot=2 reached=200 affordable=2 (1.0%) alreadyRepaidByLadder=0 headroom=2
slot=4 reached=155 affordable=20 (12.9%) alreadyRepaidByLadder=0 headroom=20
slot=5 reached=131 affordable=6 (4.6%) alreadyRepaidByLadder=1 headroom=5
AGGREGATE reached=486 affordable=28 (5.8%) alreadyRepaidByLadder=1 headroom=27  bar: >=30% and one slot >20% -> FAIL
```

Aggregate **5.8%** against the ≥30% bar and best slot **12.9%** against the >20% requirement — both
halves fail independently. Headroom is **27/28 (non-zero)**, so Open Question 2 is not what killed it:
the hook would have had room, there is just almost nothing affordable to act on. Recorded in
`docs/BALANCE-BASELINE.md` §"FV.E1 — node-level response channel, PHASE ONE probe".

Notes on the outcome-dependent tasks:

- **1.10 / 1.13**: the §6.4 bar was asserted first and measured FAIL (5.8% vs 30%). Per 1.13 / design
  D7 the shipped assertion was then swapped for the floor canary `aggregate >= measured − 5pp`; the
  bar itself is still computed and printed as an explicit PASS/FAIL verdict every run. The test method
  was renamed `…holds its measured floor` (from `…clears the go-no-go bar`) so a green shipped test
  does not claim a bar it does not clear. `IntentVerbsE1Test` untouched.
- **1.14**: not applicable — this is a FAIL, not a PASS-with-zero-headroom. Recorded either way.
- **1.15**: committed on `feat/fv-verbs-foreclose-hedge` as `test(sim):` + `docs(balance):` +
  `docs(openspec):`. Push and PR are deliberately left to the owner.

## Phase 2: The hook — only after 1.16 is a recorded PASS (NOT reached: 1.16 recorded FAIL)

- [ ] 2.1 Create `app/src/test/java/com/debtsdecks/core/simulation/NodeContext.kt`:
      `data class NodeContext(val nodeIndex: Int, val nextEnemyId: String)`.
- [ ] 2.2 In `ScriptedPolicy.kt`, add to `interface RunPolicy` (`:16–19`) the defaulted
      `fun respondToNode(run: RunManager, node: NodeContext) = Unit` plus the required
      `com.debtsdecks.core.combat.RunManager` import, carrying design's KDoc (default no-op;
      side-effecting per §6.1; any action it takes ENDS the node). **Zero lines inside
      `object ScriptedPolicy`.**
- [ ] 2.3 In `NodePolicy.kt`, widen `act` to
      `fun act(run: RunManager, policy: RunPolicy, node: NodeContext? = null)` and add rung 0 as the
      first statement of the body, **above the `val buyCost` line (currently `:29`)**:
      `if (node != null) { policy.respondToNode(run, node); if (run.phase != RunManager.Phase.NODE) return }`.
      Position is load-bearing — below the upgrade rung (`:40–42`) or the repay rung (`:45`) it is
      unreachable whenever either fires first.
- [ ] 2.4 In `RunSimulator.kt`, construct one `RunSlotCursor` per `simulate(seed)` from the injected
      `sequence`; in the NODE branch (currently `:76–93`) sample `pendingBefore` / `debtBefore`, pass
      `NodeContext(run.nodeIndex, cursor.nextEnemyId(pendingBefore))` into the `NodePolicy.act` call
      (currently `:87`), and advance the cursor after `act` returns. **Re-read the current line numbers
      before editing** — the phase-one probe work may have shifted them.
- [ ] 2.5 In `RespondingPolicy.kt`, add the single override:
      `override fun respondToNode(run: RunManager, node: NodeContext) { if (node.nextEnemyId != "loan_shark") return; run.repayViaNode() }`
      — full debt only (§6.2), silently `false` when unaffordable. Do not touch its `chooseAction`
      (13 spent variants; that lever is closed).
- [ ] 2.6 Re-sync `NodeRepayAffordabilityProbeTest.kt`'s own node call to `RunSimulator`'s new one
      (same `NodeContext` and cursor advance) so D3's trajectory-equality assertion in 1.8 stays true;
      re-run 1.11's command and confirm both probe methods are still green.
- [ ] 2.7 Run the phase-two measurement as **one invocation** so E1 and E2 are comparable:
      `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest' --tests '*HarnessDeterminismTest' --tests '*EnemyTierRegressionTest' --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i`
      Read the printed gap from stdout; never infer a pass from the suite going green.
- [ ] 2.8 From that run, record E1: the response gap over 200 seeds (responding vs ignoring), both
      absolute win rates, and `forecloseSeizures` per policy.
- [ ] 2.9 From the **same** run, record E2: `greedy.winRate in [0.35, 0.55]`, both `greedy` and
      `leverage` `avgPeakDebt in [25, 45)`, neither ≥ 70%. A separately re-run E2 number is not
      admissible evidence; re-run 2.7 once if the pair came from two invocations.
- [ ] 2.10 From the same run, confirm the difficulty floors hold unchanged —
      `weightResponding >= 20.0`, `weightIgnoring >= 15.0` — and that `IntentVerbsE1Test`'s
      `responseGap >= -5.0` floor (`:71–74`) is byte-identical to its pre-change form (§7).
- [ ] 2.11 Prove §5.3 mechanically (design D6 / Deviation 3, owner-closed) and paste both commands
      with their output into the record:
      `git diff --stat -- app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt` must
      print nothing, and
      `git diff -U0 -- app/src/test/java/com/debtsdecks/core/simulation/ScriptedPolicy.kt` must show
      only the `:16–19` interface hunk plus imports, with zero hunks inside `object ScriptedPolicy`.
      A prose claim is not the proof.
- [ ] 2.12 Confirm `git diff --stat` names no path under `app/src/main/**` in either phase (§8), and
      that `EnemyTierRegressionTest` is green and unmodified (§7 mechanical proof of no HP/damage move).
- [ ] 2.13 Append the phase-two section to `docs/BALANCE-BASELINE.md` with the four-part exit-criterion
      verdict spelled out item by item: (1) gap ≥ 10pp, (2) E2 green in the same run, (3) the 2.11
      byte-identity proof, (4) the numbers plus the verbatim command from 2.7 — recorded identically
      whether the verdict is pass or fail (proposal §5: fail is a real, expected, complete outcome).
- [ ] 2.14 **If FAIL** (gap under 10pp, or E1 passes while E2 leaves its band): write the number down
      and stop. Do not weaken `IntentVerbsE1Test`, do not move `HarnessBands` ratios or
      `DebtConfig.EXECUTION_THRESHOLD`, and do not re-scope into gold accumulation — §6.6 makes that a
      separate proposal, never a post-hoc fold-in.
- [ ] 2.15 Deliver phase two as its own second PR against `feat/fv-verbs-foreclose-hedge`:
      `test(sim):` for the hook + override + simulator wiring, `docs(balance):` for the record. The PR
      body carries the exit-criterion verdict, the exact 2.7 command, and the 2.11 command output.
- [ ] 2.16 Do not self-close. Per the standing closing/verification protocol, a later pass with no
      memory of this implementation re-runs 2.7 and 2.11 against the merged state before FV.E1 is
      marked resolved either way.
