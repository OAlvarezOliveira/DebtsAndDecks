# Design: Node-Level Response Channel (probe first, hook second)

All line numbers below were re-read in this checkout on 2026-08-29, not inherited from the proposal.
Proposal §6's seven owner decisions are treated as fixed. Two proposal citations needed correction
and one internal conflict was found — see **Deviations** at the end.

## Technical Approach

The channel needs one fact `NodePolicy` cannot currently see: **which enemy the next combat will
face.** `RunManager.slotIndex` is `private` (`RunManager.kt:140`) and no accessor exposes it, so the
upcoming slot must be *mirrored* in test source. The mirror rule is transcribed from
`advanceToNextCombat` (`RunManager.kt:305–327`) and, crucially, is **verified against the engine on
every combat start** rather than trusted — that assertion is what makes decision §6.5 mechanical
instead of rhetorical.

Phase one measures affordability at the mirrored `loan_shark` opportunities with a probe that owns
its drive loop and never calls a mutator. Phase two hands the same mirrored id to a defaulted
`RunPolicy` hook that `RespondingPolicy` alone overrides.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| D1 | The next combat's enemy is mirrored by a new test-source `RunSlotCursor` (`app/src/test/.../simulation/RunSlotCursor.kt`), shared by the probe (phase 1) and `RunSimulator` (phase 2) | Expose `slotIndex` on `RunManager`; derive from `nodeIndex` | An accessor is an `app/src/main` change, forbidden by §8 in **both** phases. `nodeIndex` arithmetic is forbidden by decision §6.5 — and it is genuinely wrong: a BREAK rematch adds a node without advancing a slot (`RunManager.kt:311–322`). |
| D2 | The mirror is **asserted against the engine**: at every combat start the probe checks `engine.getState().enemies.first().defId == expected`, all 200 seeds | Trusting the transcription; asserting only at the three `loan_shark` nodes | The whole lever keys off this id. A silent off-by-one would target the wrong node and the numbers would look plausible. ~1400 free assertions. |
| D3 | Probe owns a **transcription of `RunSimulator.simulate`'s loop** (`RunSimulator.kt:55–113`), plus a per-seed trajectory-equality assertion against `RunSimulator(policy = RespondingPolicy).simulate(seed)` | A defaulted `onNode` observer callback added to `RunSimulator` in phase 1 | The observer is cleaner code but makes phase one non-additive, breaking §10's "rollback is `git rm` of that file". Drift is the observer's only advantage and D3's equality assertion removes it mechanically. House precedent for a private loop: `RunObservationTest.runTrace` (`:64–115`), `DebtPressureTest:94`. |
| D4 | Affordability is computed by **reading** `run.gold`, `run.debt`, `run.nodeIndex` at the node — `repayViaNode()` is never called by the probe | Cloning `RunManager`; dry-run flag on `repayViaNode` | The three fields are already `public ... private set`, and `repayViaNode`'s guard (`RunManager.kt:228–231`) is a pure function of them. `RunManager` has no copy constructor and cloning it would need main-source change. |
| D5 | "Room beyond the existing rung" (decision §6.3) is measured by **observing** `run.debt == 0` after `NodePolicy.act` returns, not by re-implementing the ladder guard | Re-evaluating `debt >= REPAY_BAND && gold >= debt + feeAt(run)` inside the probe | `repayViaNode` is the only node action that zeroes debt, so the observation is exact. A re-implementation would be a second source of truth that drifts silently against `NodePolicy.kt:45`. |
| D6 | Hook host: the defaulted method goes on `RunPolicy` **in `ScriptedPolicy.kt:16–19`**, per §3/§8 and the launch brief | A separate `NodeResponder` capability interface in a new file, gated by `policy is NodeResponder` | Chosen shape follows the proposal. It does, however, make exit criterion §5.3 unsatisfiable *as literally written* — see Deviations D-3, which restates the proof and keeps the `NodeResponder` variant as the one-line fallback if the owner wants §5.3 literal. |
| D7 | On a phase-one **fail**, the §6.4 bar is recorded in `docs/BALANCE-BASELINE.md` as not met and the probe still ships (decision §6.7) with a *floor* canary (`aggregate >= measured − 5pp`) instead of the bar | Ship the bar assertion red; `@Disabled`; drop the file | A red or disabled test is not a canary. §6.4 is a go/no-go **decision** bar, not a shipped quality gate; recording the miss and stopping is the deliverable. This never touches `IntentVerbsE1Test` (§7). |

## Data Flow

    NODE reached ─ sample BEFORE acting ────────────────────────────────────┐
      pendingBefore = run.pendingBreakEncounter   (armed at RunManager.kt:162–165)
      nextEnemyId   = if (pendingBefore) "collector"                        │  mirror of
                      else sequence.slots[slotIndex + 1]?.enemyId           │  :311–325
      affordable    = run.debt > 0 &&
                      run.gold >= run.debt + escalatedCost(REPAY_FEE_BASE, run.nodeIndex)
                                                                            │
    phase 2 only ─ NodePolicy.act(run, policy, NodeContext(nodeIndex, nextEnemyId))
      └─ rung 0: policy.respondToNode(run, node) → RespondingPolicy: repayViaNode()
                 if run.phase != NODE → return          (hook ended the node)
      └─ rungs 1–7 unchanged                                                │
                                                                            │
    after acting ─ mirror advance (mirrors advanceToNextCombat + takeLoan:276–279)
      loanArmedBreak = !breakSeen && !pendingBefore && run.debt > debtBefore &&
                       run.debt >= DebtConfig.BREAK_THRESHOLD
      if (pendingBefore || loanArmedBreak) breakSeen = true else slotIndex++
                                                                            │
    next combat starts ─ ASSERT engine enemy defId == nextEnemyId  ─────────┘

`loanArmedBreak` matters because a node LOAN can arm the rematch *inside* `NodePolicy.act`, after
`pendingBreakEncounter` was sampled false. `takeLoan` is the only node action that raises debt, so
the test is exact. (`RunObservationTest:93,98` samples the flag before acting but does not cover this
case; its cursor only feeds a printed defeat slot, so it is out of scope here.)

## File Changes

| File | Phase | Action | Description |
|---|---|---|---|
| `app/src/test/.../simulation/RunSlotCursor.kt` | 1 | Create | Slot mirror + `BREAK_REMATCH_ENEMY_ID = "collector"` (mirrors `RunManager.kt:316`) |
| `app/src/test/.../simulation/NodeRepayAffordabilityProbeTest.kt` | 1 | Create | The probe: drive loop, per-slot counters, report, gate |
| `docs/BALANCE-BASELINE.md` | 1 & 2 | Modify | Numbers + exact commands (the deliverable, pass or fail) |
| `app/src/test/.../simulation/NodeContext.kt` | 2 | Create | `data class NodeContext(val nodeIndex: Int, val nextEnemyId: String)` |
| `ScriptedPolicy.kt` (`RunPolicy`, `:16–19`) | 2 | Modify | One defaulted method + 2 imports; **zero lines inside `object ScriptedPolicy`** |
| `NodePolicy.kt` (`act`, `:28`) | 2 | Modify | Third defaulted param + one gated call site above rung 1 |
| `RespondingPolicy.kt` | 2 | Modify | One `override fun respondToNode` |
| `RunSimulator.kt` (`:76–93`) | 2 | Modify | Owns the `RunSlotCursor`; passes `NodeContext` at `:87` |
| `NodeRepayAffordabilityProbeTest.kt` | 2 | Modify | Its node call re-synced to `RunSimulator`'s (keeps D3's equality assertion true) |
| `LeveragePolicy.kt`, `app/src/main/**` | — | **Unchanged** | Verified: `LeveragePolicy:22` overrides only `chooseAction`/`chooseReward` |

## Interfaces / Contracts

```kotlin
// ScriptedPolicy.kt — RunPolicy, after :18
/** FV.E1 node-level response channel. Default no-op: a policy that does not override this
 *  behaves exactly as before. Side-effecting by design (decision §6.1) — it acts by calling
 *  RunManager mutators directly. Any action it takes ENDS the node (advanceToNextCombat). */
fun respondToNode(run: RunManager, node: NodeContext) = Unit

// NodeContext.kt
data class NodeContext(val nodeIndex: Int, val nextEnemyId: String)

// NodePolicy.kt — act(), signature and rung 0
fun act(run: RunManager, policy: RunPolicy, node: NodeContext? = null) {
    if (node != null) {
        policy.respondToNode(run, node)                       // rung 0, before line :29
        if (run.phase != RunManager.Phase.NODE) return        // the hook ended the node
    }
    /* :29–59 unchanged */
}

// RespondingPolicy.kt
override fun respondToNode(run: RunManager, node: NodeContext) {
    if (node.nextEnemyId != "loan_shark") return
    run.repayViaNode()          // full debt only (§6.2); silently false when unaffordable
}
```

Call-site position is load-bearing: every ladder rung ends the node, so rung 0 must precede the
`val buyCost` block at `NodePolicy.kt:29`; placing it lower makes it unreachable whenever the
upgrade rung (`:40–42`) or the existing repay rung (`:45`) fires first. The `run.phase != NODE`
guard is what keeps the ladder from acting twice — and for `ScriptedPolicy`/`LeveragePolicy` the
default no-op cannot change the phase, so `acted` is evaluated on byte-identical state.

## Testing Strategy

**Class**: `com.debtsdecks.core.simulation.NodeRepayAffordabilityProbeTest` (JUnit 5, matching
`IntentVerbsE1Test`). 200 seeds (`0L until 200L`), `policy = RespondingPolicy`.

| Test method | Kind | Assertion |
|---|---|---|
| `` `repay affordability at every loan_shark opportunity clears the go-no-go bar` `` | Gate | `affordableTotal / reachedTotal >= 0.30` **and** `max(perSlotRate) > 0.20` (§6.4) |
| same | Informational | `println` per-slot table before the asserts, so the numbers survive a fail |
| same | Health | `reachedTotal > 0`; every slot rate in `0.0..1.0` |
| `` `the probe observes without perturbing the run it measures` `` | Non-behavioral proof | Per seed: `outcome`, `peakDebt`, `endHp`, `defeatEncounterId`, node count equal `RunSimulator(registry, enemies, policy = RespondingPolicy).simulate(seed)` |
| in-loop (both tests) | Mirror proof (D2) | Every combat start: engine `defId == RunSlotCursor.expected` |

Printed report — one row per slot `2 / 4 / 5` (0-based `sequence.json` indices, lines 5/7/8; fees
`escalatedCost(3, nodeIndex)` = **4 / 10 / 15** at nodes 2/4/5):

    slot=2 reached=N affordable=A (x.x%) alreadyRepaidByLadder=R headroom=A-R
    ...
    AGGREGATE reached=ΣN affordable=ΣA (x.x%)  bar: >=30% and one slot >20% → PASS|FAIL

`reached` counts nodes whose mirrored `nextEnemyId == "loan_shark"`; runs that die early never
increment it (§6.4's denominator). `headroom` is decision §6.3's empirical check: affordable **and**
the existing `:45` rung did not already repay.

**Phase-one command** (into `docs/BALANCE-BASELINE.md` verbatim):

```
./gradlew :app:testDebugUnitTest --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i
```

**Phase-two measurement** — one invocation, all gates in the same run (§5), so E1 and E2 are
comparable; read the printed gap from stdout, never infer a pass from green:

```
./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' \
  --tests '*RunSimulationHarnessTest' --tests '*HarnessDeterminismTest' \
  --tests '*EnemyTierRegressionTest' --tests '*NodeRepayAffordabilityProbeTest' --rerun-tasks -i
```

Byte-identity proof for §5.3 (run after phase two, before writing the doc section):

```
git diff --stat -- app/src/test/java/com/debtsdecks/core/simulation/LeveragePolicy.kt   # must be empty
git diff -U0 -- app/src/test/java/com/debtsdecks/core/simulation/ScriptedPolicy.kt      # only the :16–19 interface hunk + imports
```

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. Both phases are test-source only.

## Migration / Rollout

No migration, no schema, no save format, no `app/src/main` change. Phase one: `git rm` the two new
test files. Phase two: `git revert` the single change commit restores the policy-agnostic ladder
(the defaulted param and defaulted method mean nothing else needs touching).

## Deviations from the proposal — read this

1. **`RunManager.kt:276–279` citation (decision §6.5).** Those lines are `takeLoan`'s BREAK arming,
   not the rematch's slot behaviour. The claim "`nodeIndex` desyncs from the real slot whenever a
   BREAK rematch is pending" is **true** but lives at **`:311–322`** (`advanceToNextCombat` skips
   `slotIndex++`). `:276–279` is separately load-bearing — it is why the mirror needs
   `loanArmedBreak`. Both are cited above. Decision unchanged.
2. **Affected-files table (§8) is short by two test files.** `RunSimulator.kt` must change in
   phase two (it is the only component that owns the slot cursor, since `slotIndex` is private and
   main source is frozen), and phase one adds `RunSlotCursor.kt` next to the probe. Both are test
   source, so §8's "`app/src/main/**` unchanged" and the §11 budget forecast still hold. No owner
   decision is affected.
3. **Genuine internal conflict in the proposal — §5.3 vs §8.** §8 says `ScriptedPolicy.kt` is
   modified (the `RunPolicy` interface lives there, `:16–19`); §5.3 requires
   `git diff --stat` to name **zero** of `ScriptedPolicy.kt`/`LeveragePolicy.kt`. Both cannot hold:
   the interface is in that file. D6 keeps the proposal's shape and restates the proof as *"`git diff`
   of `ScriptedPolicy.kt` touches only the interface declaration and its imports, with zero lines
   inside `object ScriptedPolicy`; `LeveragePolicy.kt` is not named at all"* — same guarantee, and
   `LeveragePolicy.kt` still satisfies §5.3 literally. If the owner wants §5.3 literal for both
   files, the fallback is a one-line switch: declare the hook on a new `NodeResponder` interface in
   its own file and gate the call site with `if (policy is NodeResponder)`. Everything else in this
   design is unchanged by that switch. **This needs an owner ruling before phase two applies; it
   does not block phase one.**

## Open Questions

- [ ] §5.3 proof mechanism (Deviation 3) — owner ruling needed before phase two, not before phase one.
- [ ] If phase one passes but `headroom` (D5) is ~0 — affordable everywhere, but the `:45` rung
  already repays every time — the hook has no room and phase two is dead on arrival even though the
  §6.4 bar is met. Record it and stop; it is a fail of the direction, not a reason to change the bar.
