# FV.E1 — Close E1 through a node-level response channel, measured by a probe before anything is wired

**Type:** short proposal with explicit exit criteria for **two phases**. **No spec, no design, no
tasks** — same house pattern as its four siblings: if the measurement comes back bad, the next lever
gets re-scoped before it gets written. This proposal's phase one exists *specifically* so the bad
news can arrive before any code is written.

**Capabilities:** none new, none modified at spec level. Phase one is a throwaway measurement probe;
phase two touches **test source only** (`app/src/test/.../simulation/`). No `app/src/main` change is
proposed in either phase.

**Status:** proposed, unverified. **Date:** 2026-08-29. **Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22).
**Continues:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E1.
**Follows:** `fv-e1-wipe-debt-response` (policy lever, spent, 13 variants, best **+2.5pp**),
`fv-e1-card-pool-expansion` (card pool 27→31, spent, best **+4.0pp**, non-monotonic),
`fv-e1-foreclose-hedge-tuning` (static threshold sweep, spent, **-4.0 / -5.0pp**, broke E2),
`fv-e1-leverage-temporal-deadline` (temporal window, **measured FAIL today**: E1 gap
-4.5 / -2.5 / -1.0pp at cancelThreshold 27 / 33 / 45, E2 win rate **95–99%**, **zero seizures at every
candidate**; reverted on this branch as `5fc257b`). All four are the ledger below; **none of them is
re-proposed here.**
**Depends on:** PR #22 landing or staying alive (`RespondingPolicy.kt`, `IntentVerbsE1Test`,
`ForecloseControlMeasureTest` do not exist on `develop`). **Blocks:** FV E1, and nothing else.

---

## 1. The reframe — this is the whole point of the change

Two structural facts, both read from this checkout today, that **no sibling proposal states**:

**(a) E2 never constrained the responding policy.** `RunSimulationHarnessTest` — the test that
defines and enforces E2 — sweeps exactly two policies:
`runSweepWith(ScriptedPolicy)` and `runSweepWith(LeveragePolicy)`
(`app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt:209–210`). Every band
assertion downstream reads `greedy` and `leverage` only (`:247–256` peak-debt band, `:255–256` the
70% ceiling, `:277–278` the `[0.35, 0.55]` win band). **`RespondingPolicy` appears in zero E2
assertions.** A lever that lifts *only the responder* is therefore arithmetically legal: greedy stays
pinned in `[0.35, 0.55]`, and the responder is free to climb. E1 is not unreachable *inside* E2 — it
is unreachable via **symmetric** levers.

**(b) The real ceiling is structural, not tuning.** A FORECLOSE seizure is
`player.takeDamage(player.hp)` — outright death — at
`app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt:289`, inside the snapshot branch
`if (debt >= intent.param)` at `:287` (the branch restored by the `5fc257b` revert). So **at most one
seizure is avoidable per run.** At threshold 27 the measured seizure differential was 11 runs in 200
= a **~5.5pp ceiling** on seizure-avoidance alone, of which +2.5pp was actually realised. Ten points
is not reachable from that differential at any conversion rate.

**(c) And avoiding the seizure makes the responder weaker.** `docs/BALANCE-BASELINE.md`'s threshold
sweep shows that at threshold 30 the responder takes 11 fewer seizures and **wins 4pp less**: its
defeats simply migrate from `loan_shark` to `collector` (36 vs 18; same shape at 33, 37 vs 20). It
spent resources dodging and arrives at the final boss weaker.

**Therefore:** any direction that can close E1 must move **two** factors — dodge the seizure **and**
arrive at the boss no weaker. Every one of the four spent levers only ever tuned **combat-time**
behaviour or numbers. **None of them touched the run economy that happens before the fight**, where
the responder can out-resource the ignoring policy *without racing it on the same shared `debt`
scalar E2 already covers.*

## 2. Root cause of the four dead levers — verified against source

| Lever | Where it acted | Ceiling it hit |
| --- | --- | --- |
| `RespondingPolicy` behaviour, 13 variants | combat turn (`chooseAction`) | +2.5pp; one variant **-7.5pp** |
| Card pool 27→31 | draft (`chooseReward`) | +4.0pp, **non-monotonic** |
| FORECLOSE threshold sweep | `CombatEngine.kt:287` param | **-4.0 / -5.0pp**, broke E2 |
| Temporal deadline window | `CombatEngine.kt` FORECLOSE branch | **-4.5…-1.0pp**, E2 at 95–99%, **0 seizures** |

Two shared, verified reasons they converge:

- **The draft channel is nearly dead by construction.** `app/src/main/assets/run/sequence.json` sets
  `cardChoices: 1` at **6 of 7** nodes (only the casino `loan_shark` slot offers 2), and
  `RunManager.enterNode()` fills `rewardChoices` with a uniform `shuffled(rng).take(freePickCount)`
  (`app/src/main/java/com/debtsdecks/core/combat/RunManager.kt:203–206`). `chooseReward` therefore
  receives a **one-element list** almost everywhere. That is the complete mechanical explanation for
  why 13 policy variants and 3 card-pool passes all landed in +2.0…+4.0pp.
- **The whole run-level axis is identical for both policies.** `RunPolicy` exposes only
  `chooseAction` and `chooseReward` (`ScriptedPolicy.kt:16–19`), and `NodePolicy.act(run, policy)` is
  documented policy-agnostic except for `chooseReward` (`NodePolicy.kt:22–28`). Repay, loan, shop,
  upgrade and thin are the **same ladder for every policy**. There is no channel through which a
  responding policy could differ before the fight even starts.

## 3. The direction — D1 from the exploration, owner-approved

Open a **node-level response channel** so the responder can settle debt *before* a `loan_shark`
encounter, using the **existing** `RunManager.repayViaNode()` (`RunManager.kt:227–236`), pre-empting
the seizure rather than reacting to it inside combat.

- Extend `RunPolicy` with a **defaulted** node-level hook (working shape:
  `respondToNode(run, node)` — **the exact signature is design's job, not this proposal's**).
- Give `NodePolicy.act(run, policy)` a call site for that hook, gated so it is a **no-op for policies
  that do not override it**.
- `RespondingPolicy` overrides it to spend gold through `repayViaNode()` ahead of a `loan_shark`
  slot. `loan_shark` carries FORECLOSE at `intentPattern` slot 0 and `EnemyInstance.patternIndex`
  starts at 0, so FORECLOSE resolves on the **first enemy turn** of every `loan_shark` combat —
  three slots per run (`sequence.json` slots 2, 4, 5).
- `ScriptedPolicy` and `LeveragePolicy` inherit the no-op default and are **byte-for-byte
  unchanged** (see §5).

Both `RunPolicy` and `NodePolicy` live under `app/src/test/`. This is a **test-source** change.

## 4. Phase one — the fail-fast probe, and why it comes first

**`repayViaNode()` has already been measured dormant once.** `docs/BALANCE-BASELINE.md` §7 (F5,
2026-08-28) reordered the `NodePolicy` ladder to repay hot debt **before any shop** — and the sweep
came back **byte-identical**. Zero delta. The repay rule is documented as dormant.

Two verified mechanisms explain why it might stay dormant here too:

- **Cost.** `repayViaNode()` charges `debt + escalatedCost(REPAY_FEE_BASE, nodeIndex)` with
  `REPAY_FEE_BASE = 3` and `escalatedCost(base, n) = base × 1.5^(n-1)`
  (`NodeConfig.kt:28`, `:52`). At the three pre-`loan_shark` nodes the fee alone runs ~4 / ~10 / ~15
  gold **on top of** clearing a debt that sits in `[25, 45)`. It **silently returns `false`** when
  unaffordable (`RunManager.kt:231`).
- **Garnishment.** `RunManager` already redirects up to `MAX_GARNISH_RATE = 0.6` of every combat's
  gold reward toward debt, ramping to that cap at `BREAK_THRESHOLD = 30`
  (`RunManager.kt:185–189`; `DebtConfig.kt:27,39,94–99`). The responder's gold pile is being taxed by
  exactly the resource it would need to spend.
- And `NodePolicy` rung 2 **already** calls `repayViaNode()` for *every* policy when
  `run.debt >= REPAY_BAND && run.gold >= run.debt + feeAt(run)` (`NodePolicy.kt:45`). If that
  guard already fires, the new hook may have nothing left to do.

**So, before any hook is wired:** run a **cheap, non-behavioural instrumentation probe** that counts,
across the standard 200 seeds under `RespondingPolicy`-as-currently-shipped, **how often
`repayViaNode()` would actually be affordable at each of the three `loan_shark` encounter
opportunities** — reporting the raw count, the per-slot breakdown, and the rate over *reached*
opportunities (runs that end early never reach slot 4 or 5).

**Phase-one exit criterion.**

- **Pass:** affordability clears the go/no-go bar of §6.4, recorded with per-slot counts, the
  denominator of reached opportunities, and the exact gradle command, in `docs/BALANCE-BASELINE.md`.
  Only then does phase two start.
- **Fail:** affordability is near zero → **this whole direction dies here, cheaply, before the hook
  exists.** Write the number down and stop. That is a complete, valid outcome and the reason this
  phase is separate — it is a **gate**, not an implementation detail to be folded into phase two.

## 5. Phase two — wire the hook and measure the E1/E2 pair

Only if phase one passes. One pass, all gates in the same run, so numbers are comparable:
`IntentVerbsE1Test` (200 seeds, responding vs ignoring, unchanged methodology — read the printed gap
from stdout, never infer a pass from the suite going green), `ForecloseControlMeasureTest`,
`RunSimulationHarnessTest`, `HarnessDeterminismTest`, `EnemyTierRegressionTest`.

**Phase-two exit criterion — all four, or it is not a pass:**

1. Response gap **≥ 10pp over 200 seeds**;
2. E2 green **in the same run** — `greedy.winRate in [0.35, 0.55]`, both `greedy` and `leverage`
   `avgPeakDebt in [25, 45)`, neither ≥ 70%;
3. **`LeveragePolicy.kt` byte-identical to its pre-change content, proven by `git diff --stat` naming
   zero of that file. `ScriptedPolicy.kt` may change only in the `RunPolicy` interface declaration it
   hosts** (the new defaulted `respondToNode` method plus any required import) — **zero lines inside
   `object ScriptedPolicy` itself**, proven by a diff scoped to that block showing no hunks. Closed
   2026-08-29: `RunPolicy` lives in `ScriptedPolicy.kt` by construction, so a literal whole-file
   byte-identical claim for that one file is unsatisfiable; the object's behavior, not the file's
   bytes, is what must stay unchanged. This is still a *mechanical* proof, not an argument — so E2's
   existing green measurements of both policies are provably untouched;
4. the numbers in `docs/BALANCE-BASELINE.md` with the exact command that produced them.

**Fail — a real, expected, complete outcome.** Under 10pp: **write the number down and stop.** Do not
weaken `IntentVerbsE1Test` to manufacture green. **Also a fail:** E1 passes but E2 leaves its band.
Per `fv-core-validation` §4, a new band arrives with its own proposal and its own sim output, never
on paper.

## 6. Design decisions — closed by the owner, 2026-08-29

All seven decisions below were open when this proposal was first drafted and are now closed. Design
picks up from here; none of the following is design's to re-open without a new owner conversation.

1. **Hook shape — closed: `respondToNode(run, node)`, no return value.** Side-effecting: it acts by
   calling into `RunManager` (e.g. `repayViaNode()`) directly, the same way the mutation already lives
   in `RunManager` rather than in the policy. No new response-descriptor type.
2. **Repayment strategy — closed: full debt only, via `repayViaNode()` as it exists today.** No
   partial/threshold-aware repayment. This keeps phase two entirely in test source — no new
   `app/src/main` affordance.
3. **Relationship to the existing repay rung (`NodePolicy.kt:45`) — closed: they coexist.** The
   existing rung repays at shop time for every policy; the new hook fires specifically ahead of a
   `loan_shark` node, pre-empting FORECLOSE. They are different moments, not competing
   implementations. Phase one's probe must still confirm empirically that the new hook has room to
   act beyond what the existing rung already covers — that confirmation is *why* phase one exists,
   not a reason to reopen this decision.
4. **Phase-one go/no-go bar — closed: ≥ 30% of *reached* pre-`loan_shark` opportunities affordable,
   with at least one of the three slots above 20%.** Denominator is reached opportunities (runs that
   end early do not count against the bar).
5. **Node targeting — closed: key off the upcoming slot's `enemyId`, never `nodeIndex` arithmetic.**
   `nodeIndex` desyncs from the real slot whenever a BREAK rematch is pending
   (`RunManager.kt:276–279`); targeting must resolve against the actual next combat's enemy, not a
   node count.
6. **Gold accumulation — closed: out of scope.** Phase two touches only the spend side (the hook).
   Garnishment (`MAX_GARNISH_RATE` up to 0.6) is not adjusted in this change. If phase two fails for
   lack of affordable gold, accumulation becomes its own, separate proposal — not folded into this one
   after the fact.
7. **Probe lifespan — closed: the phase-one probe ships as a permanent regression test**, not a
   throwaway. It stays cheap to re-run and acts as a canary if future garnish/fee changes make
   `repayViaNode()` dormant again.

## 7. Non-negotiables inherited from every sibling proposal

- **No enemy HP or damage changes, ever.** `thug` 24 / `loan_shark` 40 / `collector` 57 and every
  `ATTACK`/`MULTI_ATTACK`/`BUFF`/`DEBUFF`/`LEVY` value stay exactly as they are.
  `EnemyTierRegressionTest` is the mechanical proof.
- **No weakening of `IntentVerbsE1Test`.** The `responseGap >= -5.0` floor (`:71–74`) and the
  `weightResponding >= 20.0 && weightIgnoring >= 15.0` difficulty-weight floors (`:86–92`) stay
  exactly as they are. No manufactured E1 pass.
- **`HarnessBands` ratios and `DebtConfig.EXECUTION_THRESHOLD` (50) are not moved to fit.**
  Re-anchoring the band is re-baselining by stealth.
- **`ScriptedPolicy` / `LeveragePolicy` behaviour and every existing E2 assertion provably
  unchanged** — verified by exit criterion §5.3 (`LeveragePolicy.kt` byte-identical; `ScriptedPolicy.kt`
  changed only in the `RunPolicy` interface hunk it hosts, zero lines inside `object
  ScriptedPolicy`), not by argument.

## 8. Affected files

| Area | Phase | Impact | What changes |
| --- | --- | --- | --- |
| new probe test under `app/src/test/.../simulation/` | 1 | New | affordability instrumentation only; no behaviour |
| `docs/BALANCE-BASELINE.md` | 1 & 2 | Modified | the numbers + exact commands (the deliverable, pass or fail) |
| `ScriptedPolicy.kt` (`RunPolicy` interface, `:16–19`) | 2 | Modified | **one defaulted method added to the interface**; the `ScriptedPolicy` object itself unchanged |
| `NodePolicy.kt` (`act`, `:28`) | 2 | Modified | one gated call site for the hook |
| `RespondingPolicy.kt` | 2 | Modified | one override |
| `app/src/main/**` | — | **Unchanged** | no production code change is proposed in either phase |

## 9. Non-goals

- **No re-implementation of any of the four dead levers.** No further `RespondingPolicy` *combat*
  behaviour tuning, no `cards/all.json` pool changes, no FORECLOSE `param` / HEDGE divisor moves, no
  temporal window. Their numbers are the ledger in §2, not a menu.
- **No `CombatEngine.kt` change.** The FORECLOSE branch stays the post-revert snapshot
  (`debt >= param`, fee 9).
- **D2 — draft agency** (raising `cardChoices` in `app/src/main/assets/run/sequence.json` and/or
  weighting the free pick) is a **separate, lower-ranked direction from the same exploration** and is
  **OUT of scope here**. Named so the family stays legible; not folded in. Its own risk is that it
  lifts **both** policies and moves E2.
- **D3 — implementing AUDIT** (the third `fv-core-validation` verb, still entirely unimplemented) is
  likewise a **separate, lower-ranked direction** and **OUT of scope here**. Its own risk is that
  without a real draft channel a forced tag-disable is symmetric: both policies just play other cards.
- Not a balance pass, not a roster or content phase, not an E2 re-baselining.

## 10. Rollback

- **Phase one:** one new test file, zero `app/src/main` changes, zero behaviour changes. Rollback is
  `git rm` of that file. The `docs/BALANCE-BASELINE.md` section stays either way — for this lever the
  number is the deliverable even when it fails.
- **Phase two:** one defaulted interface method, one gated call site, one override — all test source.
  `git revert` of the single change commit restores the current policy-agnostic ladder. **No
  production code, no schema, no save-format, no migration.** This is the simplest rollback shape of
  any FV.E1 lever so far; the temporal-deadline sibling had to revert engine code.

## 11. Review workload forecast

- **Phase one (probe):** ~60–120 lines, one new test file plus one results-doc section.
  `Decision needed before apply: No` — §6.4 (the go/no-go number) and §6.5 (node targeting) are
  closed. `Chained PRs recommended: No`. `400-line budget risk: Low`.
- **Phase two (hook + measurement):** ~40–100 lines across three test-source files plus one
  results-doc section. `Decision needed before apply: No` — §6.1–6.3 and §6.6 are closed.
  `Chained PRs recommended: No` — but phase one and phase two are **separate PRs**, because phase
  one's whole value is being mergeable and stoppable on its own. `400-line budget risk: Low`.
  Phase two remains gated on phase one's **measured pass**, not on any remaining design ambiguity.
