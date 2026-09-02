# FV.E1 — Close E1 by decoupling FORECLOSE from the shared leverage band via a temporal deadline

**Type:** short proposal with an explicit exit criterion. **No spec, no design, no tasks** — same
house pattern as its three siblings: if the measurement comes back bad, the next lever gets
re-scoped before it gets written. **Capabilities:** modified — `CombatEngine.kt` FORECLOSE branch
(engine logic change); `enemies/all.json` FORECLOSE intent semantics (a window, not a single
snapshot). This proposal is the first in the chain to change engine code.

**Status:** proposed, unverified. **Date:** 2026-08-29. **Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22).
**Continues:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E1.
**Follows:** `fv-e1-wipe-debt-response` (policy lever, spent, 13 variants),
`fv-e1-card-pool-expansion` (card-pool lever, spent, non-monotonic),
`fv-e1-foreclose-hedge-tuning` (threshold-sweep lever, spent, broke E2). All three are below; this
change is their **root-cause** successor, not a fourth data knob.
**Depends on:** PR #22 landing or staying alive (`IntentVerbsE1Test`, `RespondingPolicy`,
`ForecloseControlMeasureTest` do not exist on `develop`). **Blocks:** FV E1, and nothing else.

---

## 1. What is actually open

E1 asks for a policy that responds to FORECLOSE/HEDGE to beat one that ignores them by
**≥ 10pp win rate over 200 seeds**. Best ever measured: **+2.5pp**.

Three levers are spent, each with its number written down:

| Lever | Rounds | Best | Notes |
| --- | --- | --- | --- |
| Policy behaviour (`RespondingPolicy.kt`) | 13 variants, 3 sessions | **+2.5pp** | one variant regressed to **-7.5pp** |
| Card pool (`cards/all.json` 27→31) | 3 passes | **+4.0pp** then back to +2.0pp | **non-monotonic** |
| FORECLOSE threshold sweep (27→30→33) | 1 sweep | **-4.0pp / -5.0pp** at 30 / 33 | **broke E2** at the upper points |

The detailed tables live in `fv-e1-foreclose-hedge-tuning/proposal.md` §4 (threshold sweep) and the
FV.E1 section of `docs/BALANCE-BASELINE.md`. What matters here: **all three levers moved the same
thing — the player's debt level inside E2's band — and all three ceilinged at roughly +4pp or
regressed.** That is not three independent failures; it is one failure measured three ways.

## 2. Root cause — verified against source, not taken on trust

The three spent levers share a single trapped variable. The following are read from this checkout today:

- `CombatState.debt` is **one** `Int` field — `app/src/main/java/com/debtsdecks/core/model/CombatState.kt:18`
  (`val debt: Int = 0`). It is the player's only Debt resource.
- **FORECLOSE reads it as a level threshold:** `if (debt >= intent.param)` at
  `app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt:287` (branch opens at `:281`, seizes
  at `:288-289`, standing fee at `:292`). The threshold is the `loan_shark` FORECLOSE slot
  `param: 27` (`app/src/main/assets/enemies/all.json`, lines 6–8: `FORECLOSE` / `damage: 9` / `param: 27`).
- **HEDGE also reads the same field proportionally:** `debt / (intent.param.takeIf { it > 0 } ?:
  DebtConfig.LEVERAGE_DIVISOR)` at `CombatEngine.kt:301` (HEDGE branch opens at `:296`).
- **The E2 leverage band reads the same field as a band,** derived from
  `DebtConfig.EXECUTION_THRESHOLD = 50` (`app/src/main/java/com/debtsdecks/core/combat/DebtConfig.kt`):
  `HarnessBands` ratios × 50 → leverage band **[25, 45)** and `leverageTarget` **35**
  (`app/src/test/java/com/debtsdecks/core/simulation/HarnessBands.kt`, `LEVERAGE_BAND_LOW_RATIO 0.50`,
  `LEVERAGE_BAND_HIGH_RATIO 0.90`, `LEVERAGE_TARGET_RATIO 0.70`).
- The `IntentType` declarations themselves sit at
  `app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt:40`
  (`enum class IntentType`), with `FORECLOSE` at `:46` and `HEDGE` at `:47`.

**The trap:** FORECLOSE's `debt >= 27` and the leverage band `[25, 45)` / target `35` are **the same
raw `CombatState.debt` integer read three different ways** — level threshold (FORECLOSE),
proportional read (HEDGE), and band/target (HarnessBands) — **not two separate axes.** Because both
policies must keep debt inside `[25, 45)` to stay in the Leverage game at all, any lever that merely
relocates debt *within or below that band* perturbs the responding and ignoring policies **almost
equally**: the responding policy cannot occupy a debt *level* the ignoring policy does not also
occupy, so its advantage is bounded by the band width — which is exactly the **~+4pp** ceiling the
measurements hit. Moving debt *above* the band (raise threshold past 35) is what broke E2 in the sweep.

So E1 is not under-tuned; it is **mechanically ceilinged** by the axis choice. No in-band knob
(policy, pool, or threshold) can exceed the cap. This is why three levers, each aimed at a different
surface, all returned the same ~+4pp wall.

## 3. The lever — FORECLOSE becomes a temporal deadline, not a level threshold

**Direction 2** from the read-only exploration that accompanied this brief, recommended over the
three alternatives it considered:

- **(alt) New orthogonal "arrears" axis** — a separate resource the player manages. High effort, high
  risk: teaches a second resource. **Rejected.**
- **(alt) Interest-rate coupling during announcement** — FORECLOSE raises debt interest while
  telegraphed. Low effort but **weak decoupling**: still a debt-*level* effect, so it stays trapped
  in the same band. **Rejected.**
- **(alt) Momentum/streak-based response scaling** — reward consecutive repay turns. High effort,
  **unproven**, and still keys off debt level. **Rejected.**
- **(chosen) Temporal deadline** — **recommended.**

### What Direction 2 does

Keep `CombatState.debt` as the single resource players already understand (no new resource to
teach). Reuse the existing **telegraphed-intent** pattern (the enemy already announces FORECLOSE one
turn ahead). But change the *trigger axis*:

- Today FORECLOSE fires a single `debt >= intent.param` **snapshot** at resolution (`CombatEngine.kt:287`).
- Under Direction 2, when FORECLOSE is announced, it opens an **N-turn window** (length a new
  tunable). During that window the seizure is *pending*, not fired. If the player repays debt
  **below an independent cancel threshold** at any point in the window, the seizure is **cancelled** —
  without requiring the player to sit at a different *level* than the ignoring policy.
- The reward is for **TIMING / behaviour** (playing a repay card inside the window) rather than for
  occupying a debt *level* that lies outside the shared band. The ignoring policy, by definition,
  does not repay inside the window, so the responding policy earns a real, band-independent advantage.

This moves the FORECLOSE trigger off the shared numeric band that trapped all three prior levers,
while leaving the leverage band and the Debt axis itself untouched.

## 4. Open design decisions — proposal question round for the owner

**Not decided here**, deliberately, the same way every sibling deferred instead of choosing:

1. **Window length N.** How many turns after announcement does the seizure stay pending? Too short and
   only the *announced* turn's repay matters (collapses toward today's snapshot); too long and the
   verb becomes a soft suggestion.
2. **Cancel threshold and its independence.** Is the "repay below X to cancel" threshold the existing
   `param: 27`, a new independent constant (recommended — it is the whole point of decoupling), or
   debt-relative? Must be tunable **independently of `HarnessBands`** so it cannot silently re-trap
   the band.
3. **Uncancelled outcome.** If the window closes with debt still above the cancel threshold, does
   FORECLOSE still run-end (`player.takeDamage(player.hp)`, `CombatEngine.kt:289`) as today, or
   downgrade to the standing fee (`CombatEngine.kt:292`)? Keep run-ending to preserve verb weight,
   but owner call.
4. **HEDGE scope.** Does Direction 2 touch the HEDGE branch at all? The brief's decoupling target is
   the *level* axis; HEDGE's `debt / param` read (`:301`) is a different consumer of the same field.
   Proposal default: **HEDGE unchanged** unless the owner wants the proportional read re-examined.
5. **Announcement lead.** Confirm the harness already presents FORECLOSE one turn ahead (telegraphed
   intent) so the window has a turn to act in; if not, that is a prerequisite change to `EnemyAI` /
   intent scheduling, not just `CombatEngine`.
6. **Measurement policy.** The 13-variant policy lever is spent *against the level-threshold*
   FORECLOSE. A temporal-deadline FORECLOSE needs a *new* minimal responding policy that repays
   inside the window to measure the gap. That is a **test fixture**, not a re-tread of the spent
   lever — but the owner should acknowledge the E1 number will require writing one.
7. **Where the new constants live.** `DebtConfig` (clean revert, mirrors the `LEVERAGE_*` family) or
   `enemies/all.json` intent data (as today's `param`). Affects rollback shape (see §10).

If these are unanswered, this change **stops and asks**. It does not pick.

### Owner decisions (2026-08-29)

1. **Window length N = 3 turns.**
2. **Cancel threshold: independent new constant** (not `param: 27`, not debt-relative to
   `HarnessBands`) — confirmed as recommended.
3. **Uncancelled outcome: run-ending**, kept as-is (proposal default confirmed).
4. **HEDGE scope: unchanged.** Only the FORECLOSE branch is touched by this change.
5. **Announcement lead: still to confirm in spec** — not resolved by this round, carries into
   `sdd-spec` as a prerequisite check, not a design choice.
6. **Measurement policy: acknowledged.** A new minimal window-exploiting responding-policy test
   fixture is in scope for this change (`sdd-tasks` item), not optional.
7. **Where constants live: `enemies/all.json`**, per-intent data (mirrors `param`/`damage` today).
   The cancel threshold from decision 2 is a *new* field on the FORECLOSE intent, not a top-level
   `DebtConfig` constant. Rollback stays single-commit (§10) — no schema/save-format change, just
   one more JSON field per FORECLOSE intent.

Numeric value of the cancel threshold itself is **not fixed here** — it is a tuning parameter
measured in `sdd-design`/`sdd-tasks` against the exit criterion in §8, the same way the shipped
`param: 27` was reached by sweep, not picked in advance.

## 5. Non-negotiables inherited from every sibling proposal

- **No enemy HP or damage changes, ever.** Standing owner constraint: **Debt, not HP, is the axis
  players manage; HP/damage is secondary.** `loan_shark` (40) / `collector` (57) / `thug` (24) and
  every `ATTACK`/`MULTI_ATTACK`/`BUFF`/`DEBUFF`/`LEVY` value stay exactly as they are.
  `EnemyTierRegressionTest` (boss HP 57) is the mechanical proof; if a tuning point can only be
  rescued by more enemy HP, that point is discarded.
- **E2 (`RunSimulationHarnessTest` band assertions) must either stay green or this proposal must
  explicitly justify and attach sim output for any band change.** Never asserted on paper. **No
  manufactured E1 pass by weakening the `IntentVerbsE1Test` assertion** (currently
  `responseGap >= -5.0`). The 2026-08-28 re-metric that weakened it is known debt on the branch and
  must not be repeated.
- **`HarnessBands` ratios and `DebtConfig.EXECUTION_THRESHOLD` are not moved to fit.** The decoupling
  must come from the trigger axis, not from re-anchoring the band.

## 6. Affected files / tests requiring re-verification

Named explicitly because Direction 2 changes the FORECLOSE trigger and the verb's timing semantics:

- `app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt` — FORECLOSE branch (`:281–295`) and,
  if §4.4 says so, HEDGE branch (`:296` / `:301`).
- `app/src/test/java/com/debtsdecks/core/enemies/EnemyTierRegressionTest.kt` — boss HP 57 must stay
  untouched (mechanical proof of the §5 non-negotiable).
- `app/src/test/java/com/debtsdecks/core/simulation/ForecloseControlMeasureTest.kt` — instruments
  `forecloseSeizureCount`; the seizure *timing* changes under a window, so its expected counts move.
- `app/src/test/java/com/debtsdecks/core/simulation/HarnessDeterminismTest.kt` — the harness was
  non-deterministic once; a timing-sensitive verb must not reintroduce drift.
- `app/src/test/java/com/debtsdecks/core/simulation/RunSimulationHarnessTest.kt` — E2 band assertions
  at `:247–256` (peak-debt band `[25, 45)`) and `:277–278` (win rate `[0.35, 0.55]`).
- (Measurement gate, from siblings) `IntentVerbsE1Test` — the 200-seed responding-vs-ignoring gap;
  the E1 number is read from its stdout, the current test does not gate it.

## 7. Measurement plan

One pass, all gates in the same run, so numbers are comparable:

- **`IntentVerbsE1Test`, 200 seeds**, responding (new window-exploiting policy per §4.6) vs ignoring,
  **unchanged methodology** from the 13 prior variants. Read the printed gap from stdout; do not infer
  a pass from the suite going green.
- **`ForecloseControlMeasureTest`** in the same pass — `forecloseSeizures` per policy; under a window
  the *timing* of seizures changes, so this is the control that proves the verb still bites.
- **`RunSimulationHarnessTest`** in the same pass. Keeping E2 closed is non-negotiable (§5).
- **`HarnessDeterminismTest`** and **`EnemyTierRegressionTest`** in the same pass.
- Record in `docs/BALANCE-BASELINE.md`: the E1 gap, **both** policies' absolute win rates, seizure
  counts, the E2 numbers, and the **exact gradle command**, e.g.
  `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest'`.

## 8. Exit criterion

**Pass — all three, or it is not a pass:**

1. Response gap **≥ 10pp over 200 seeds**;
2. E2 still green **in the same run** — `greedy.winRate in [0.35, 0.55]`, both policies'
   `avgPeakDebt in [25, 45)`, neither policy ≥ 70%;
3. the numbers in `docs/BALANCE-BASELINE.md` with the exact command that produced them.

**Fail — a real, expected, complete outcome.** Under 10pp: **write the number down and stop.** Do not
weaken `IntentVerbsE1Test` to manufacture green. E1 being unreachable even with the trigger axis
moved off the band is exactly the finding FV was built to produce — and it would now be a genuinely
new result, not a re-measurement of the same wall.

**Also a fail:** E1 passes but E2 leaves its band, in either gate. That is a failure of *this* change,
not a re-baselining opportunity. Per `fv-core-validation` §4, a new band arrives with its own
proposal and its own sim output, never on paper.

## 9. Non-goals

- **No enemy HP / damage / intentPattern restructuring.** Same verb slots, same enemies; only the
  FORECLOSE *trigger semantics* change.
- **No new Debt axis or resource.** `CombatState.debt` stays the single Debt field. Direction 2's
  whole value is decoupling *without* a second resource.
- **No change to `HarnessBands` ratios or `DebtConfig.EXECUTION_THRESHOLD`.** Re-anchoring the band
  is re-baselining by stealth (§5).
- **No `cards/all.json` changes.** That lever is spent, and non-monotonically.
- **No `RespondingPolicy.kt` *behaviour* tuning as a balance lever** — only the minimal
  window-exploiting fixture needed to *measure* the new mechanic (§4.6), kept separate from the
  earlier 13 variants.
- Not a balance pass, not a roster/content phase.

## 10. Rollback

More involved than the data-only siblings, because this changes engine code:

- If the new constants live in `DebtConfig` (§4.7 option), revert is `+N` constants plus the
  `CombatEngine.kt` FORECLOSE branch → ~1 commit, no schema.
- If they live in `enemies/all.json` intent data, revert is the FORECLOSE slot semantics + the
  `CombatEngine.kt` branch.
- Either way `git revert` of the single change commit restores `debt >= intent.param` snapshot
  FORECLOSE (27 / fee 9); no save-format or migration. The `docs/BALANCE-BASELINE.md` sections stay
  as a record either way — for this lever the number is the deliverable even when it fails.

## 11. Out-of-scope aside — AUDIT (noted, not folded in)

**AUDIT** is the third verb in the original `fv-core-validation` proposal
(`openspec/changes/fv-core-validation/proposal.md`, verb table line 46): *"Disables a card tag
(`debt_scaling`, `debt_payoff`…) for N turns"* — the **deck-plan / card-tag pillar**, distinct from
the Leverage axis this proposal attacks. It was **never implemented**. It is a **separate,
unexplored lever** the owner may want considered later; it is **deliberately not folded into this
proposal**, which is strictly about decoupling the existing FORECLOSE/HEDGE Leverage mechanic from
the shared debt band.

## 12. Review workload forecast

- Engine branch change (FORECLOSE window) + intent-data semantics + constants: estimate **40–120
  lines** of Kotlin plus one `enemies/all.json` slot change and one results-doc section.
- New minimal measurement policy fixture (§4.6): separate small addition, kept out of the spent
  `RespondingPolicy` variants.
- `Decision needed before apply: Yes` — §4 is unanswered, and §4.3 / §4.7 materially change the engine.
- `Chained PRs recommended: No`.
- `400-line budget risk: Low`.
