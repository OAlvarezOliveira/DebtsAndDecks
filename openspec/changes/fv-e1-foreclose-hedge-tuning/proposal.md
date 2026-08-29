# FV.E1 — Close E1 by retuning FORECLOSE/HEDGE, the last lever, inside E2's band

**Type:** short proposal with an explicit exit criterion. **No spec, no design, no tasks** —
same reason as its parent and both siblings: if the measurement comes back bad, the next lever
gets re-scoped before it gets written. **Capabilities:** new — none; modified — none.

**Status:** proposed, unverified, **blocked on §3 and §6**. **Date:** 2026-08-29.
**Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22).
**Continues:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E1.
**Follows:** `fv-e1-wipe-debt-response/proposal.md` (policy lever, spent) and
`fv-e1-card-pool-expansion/proposal.md` (card-pool lever, spent). This is their **lever (a)**,
deferred by both as *"boxed in by E2's band, can widen the gap the wrong way, its own proposal
if ever."*
**Depends on:** PR #22 landing or staying alive, **and** an owner answer to §3.
**Blocks:** FV E1, and nothing else.

---

## 1. What is actually open

E1 asks for a policy that responds to FORECLOSE/HEDGE to beat one that ignores them by
**≥ 10pp over 200 seeds**. Best ever measured: **+2.5pp**.

Two levers are spent, with their data written down:

| Lever | Rounds | Best | Notes |
| --- | --- | --- | --- |
| Policy behaviour | 13 variants, 3 sessions | +2.5pp | one variant regressed to **-7.5pp** |
| Card pool | 3 passes | +4.0pp then back to +2.0pp | **non-monotonic**; the second addition moved backward |

The card-pool proposal's own §2 "symmetry problem" — *a richer pool feeds the ignoring policy's
`chooseReward` almost as much as the responding one* — is now **empirically confirmed, not a
risk**: that is what a non-monotonic pool sweep looks like.

This is the third and last lever named by `fv-core-validation`. Nothing follows it.

## 2. The lever's actual shape — live values, read from source

Read today from `/home/oscardev/DebtsAndDecks-baseline`
(`app/src/main/assets/enemies/all.json`, `.../core/combat/CombatEngine.kt`,
`.../core/combat/DebtConfig.kt`). **The PR-body numbers are partly stale; these are not.**

| Parameter | Live value | Where it lives | Engine site |
| --- | --- | --- | --- |
| FORECLOSE threshold | **27** | `loan_shark` intent slot 1, `param` | `CombatEngine`: `if (debt >= intent.param)` → seizure = `player.takeDamage(player.hp)`, run-ending |
| FORECLOSE fee | **9** | `loan_shark` intent slot 1, **`damage`** | `else` branch: `player.takeDamage(intent.damage)` |
| HEDGE divisor | **4** | `collector` intent slot 2, `param` | `enemy.gainBlock(debt / intent.param)`; falls back to `DebtConfig.LEVERAGE_DIVISOR` (6) only when `param <= 0` |

Three facts this changes:

- **No FORECLOSE/HEDGE constant exists in `DebtConfig`.** All three numbers are enemy data. The
  PR body's "HEDGE ÷4" is right; its fee sweep 9/14/35 and threshold sweep 18→27 describe
  *explored* values — **9 and 27 are what shipped**.
- FORECLOSE appears on **one enemy, one slot**; HEDGE on **one enemy, one slot**.
- The band context is confirmed live: `HarnessBands` ratios × `EXECUTION_THRESHOLD` 50 give
  leverage band **[25, 45)**, `leverageTarget` **35**. So **`LEVERAGE_TARGET` (35) sits ABOVE the
  FORECLOSE threshold (27)**: both policies steer debt to a level where the deadline is already
  breached. FORECLOSE is not a deadline today, it is a standing condition.

**Verification caveat, stated rather than hidden:** that worktree's `docs/BALANCE-BASELINE.md`
stops at 2026-08-28 and its `RespondingPolicy.kt` predates the 2026-08-29 rounds. Those rounds
touched `RespondingPolicy.kt` and `cards/all.json`, **not** `enemies/all.json`, so 27/9/4 should
still be live — confirm with `git show origin/feat/fv-verbs-foreclose-hedge:app/src/main/assets/enemies/all.json`
before editing anything.

## 3. The constraint conflict this proposal cannot resolve by itself

The standing owner constraint is *"never inflate enemy HP or damage as a lever; do not touch
`enemies/all.json` or any enemy damage value."* Per §2, **all three candidate parameters live in
`enemies/all.json`, and the FORECLOSE fee is literally a `damage` field.**

Read literally, **lever (a) is unimplementable and E1 is closed as unreachable.** Read as intent
(*no HP inflation, no ATTACK/MULTI_ATTACK damage inflation — Debt stays the axis*), the
FORECLOSE threshold is exactly a Debt-axis knob and is in scope.

This change **does not pick**. See §6 question 1.

## 4. What the sweep already measured — and why it is stale

`docs/BALANCE-BASELINE.md` §"FV criterion E1" (2026-08-28, at `5afb99f`) already swept this
lever. Do not re-run it blind:

| Threshold | Seizures / 200 | Result |
| --- | --- | --- |
| ≥ 33 | 3 | game at **89.5%** — E2 dead (≥70% gate) |
| **27 (shipped)** | 85 responding / 96 ignoring | gap **0.5pp**, then **2.5pp** post-HP-calibration |
| 20 | 151 | responding 23% / ignoring 14.5% — gap **8.5pp**, band **collapsed** (<35%) |

It also records: *"the fee and the HEDGE divisor do not discriminate at all (both policies suffer
them equally; raising the fee or hardening the hedge moves the gap **against** the responding
policy)."*

**The stale part, and the only reason this proposal is not already closed:** that threshold sweep
was measured at **HP ×1.00**, where the baseline win rate was 71%. The HP ×1.10 calibration
landed afterward and its own section says it **supersedes the win-rate table above it**. At
×1.10 the shark survives longer and FORECLOSE bites far more (3/200 → 85–96/200). So the
"threshold ≥33 barely bites" finding is measured in a world that no longer exists, and the usable
window may have shifted upward — possibly across `LEVERAGE_TARGET` 35, where FORECLOSE would
become a real deadline instead of a standing condition.

One more unexplained datum to attack: at threshold 27 the responding policy already dodges
**11 more seizures per 200 runs** (85 vs 96) and converts that into only **2.5pp**. Seizure
avoidance is not turning into wins. Any tuning that does not move that ratio will not move E1.

## 5. The safe numeric room, scoped

E2 gates, all live in `RunSimulationHarnessTest`: `greedy.winRate in 0.35..0.55`; both policies'
`avgPeakDebt` in `[25, 45)`; won-run peak debt > 25; neither policy ≥ 0.70; leverage within 5pp
of greedy. Last measured (card-pool round): **greedy 50.0% / leverage 47.5%**, peak ≈ 30.

| Direction | Effect | Binding gate | Room |
| --- | --- | --- | --- |
| Raise threshold | fewer seizures → win rates **rise** | `greedy ≤ 0.55` | **≈ 5pp** |
| Lower threshold | more seizures → win rates **fall** | `greedy ≥ 0.35` | ≈ 15pp… |
| Lower threshold | …but policies repay/avoid → peak debt **falls** | `avgPeakDebt ≥ 25` | **≈ 5 points** — this binds first |

So the room is roughly symmetric and **narrow in both directions**, and downward the binding gate
is the **debt band floor**, not the win floor. Raising the HEDGE divisor or the fee spends the
same 5pp of upward win-rate room while, per §4, moving the gap the wrong way.

## 6. Open design decisions — proposal question round for the owner

**Not decided here**, deliberately, the same way both siblings deferred instead of choosing:

1. **§3 constraint scope.** Does the "no `enemies/all.json`, no damage value" constraint (a) mean
   no HP / no ATTACK-damage inflation, leaving FORECLOSE `param`, FORECLOSE `damage` and HEDGE
   `param` editable in place; (b) hold literally, so the parameters must first be promoted to
   `DebtConfig` — an engine change this proposal currently lists as a non-goal; or (c) hold
   literally and end lever (a), closing E1 as unreachable? **Nothing starts until this is
   answered.**
2. **Which parameter(s)?** §4 says fee and HEDGE divisor do not discriminate and move the gap
   *against* responding. Sweep the **threshold only**, or re-probe fee/divisor because their
   "no discrimination" finding is also pre-HP-calibration?
3. **How many sweep points, and which?** e.g. threshold ∈ {24, 30, 33, 36} (4 points × 2 policies
   × 200 seeds), or a 2-point probe (30, 36) first to see whether the window moved at all?
4. **Which gate does the owner prefer to spend** — the ~5pp of upward win-rate room, or the ~5
   points of downward debt-band room? They are not interchangeable: the first weakens the verb,
   the second weakens the Debt axis the constraint exists to protect.
5. **Is "re-measured, lever (a) confirmed dead at post-calibration HP" an acceptable terminal
   deliverable** that closes E1 as unreachable with evidence?
6. **On a pass, does `IntentVerbsE1Test` get its original 10pp gate restored?** It currently
   gates only `responseGap >= -5.0` and prints the gap informationally (the 2026-08-28 re-metric).
   Restoring the real gate is a test-strengthening change — in this change, or a follow-up?

If these are unanswered, this change **stops and asks**. It does not pick.

## 7. Measurement plan

One pass, all gates in the same run, so the numbers are comparable:

- **`IntentVerbsE1Test`, 200 seeds**, unchanged methodology. The E1 number is the printed
  `Response gap (responding - ignoring, informational)` line — **the current test does not gate
  it**. Read it from stdout; do not infer a pass from the suite going green.
- **`ForecloseControlMeasureTest`** in the same pass: it prints `forecloseSeizures` per policy.
  Per §4 the seizure-differential-to-win-rate conversion is the thing being tuned; without it the
  gap number is unattributable.
- **`RunSimulationHarnessTest`** in the same pass. Keeping E2 closed is non-negotiable.
- **`HarnessDeterminismTest`** and **`EnemyTierRegressionTest`** too — the latter tracks boss HP
  57 and must stay untouched, which is also the mechanical proof of the §9 non-goal.
- Record in `docs/BALANCE-BASELINE.md`: every swept point, the E1 gap, **both** policies'
  absolute win rates, seizure counts, the E2 numbers, and the **exact gradle command**, e.g.
  `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest'`.

## 8. Exit criterion

**Pass — all three, or it is not a pass:**

1. Response gap **≥ 10pp over 200 seeds**;
2. E2 still green **in the same run** — `greedy.winRate in [0.35, 0.55]`, both policies'
   `avgPeakDebt` in `[25, 45)`, neither policy ≥ 70%;
3. the numbers in `docs/BALANCE-BASELINE.md` with the exact command that produced them.

**Fail — a real, expected, complete outcome.** Under 10pp: **write the number down and stop.**
Do not weaken `IntentVerbsE1Test` to manufacture green — the 2026-08-28 re-metric already did
that once and it is still on the branch as known debt, and this is the last lever, so there is
nothing left to chain. E1 being unreachable at any FORECLOSE/HEDGE position that keeps E2 in
band is exactly the kind of finding FV was built to produce.

**Also a fail:** E1 passes but E2 leaves its band, in either gate. That is a failure of *this*
change, not a re-baselining opportunity. Per `fv-core-validation` §4, a new band arrives with its
own proposal and its own sim output attached, never on paper.

## 9. Non-goals

- **No enemy HP or damage inflation, ever.** Standing owner constraint: **Debt, not HP, is the
  axis players manage; HP/damage is secondary.** `thug` 24 / `loan_shark` 40 / `collector` 57
  stay exactly as they are, as do every `ATTACK`, `MULTI_ATTACK`, `BUFF`, `DEBUFF` and `LEVY`
  value. `EnemyTierRegressionTest` (boss HP 57) is the mechanical proof. If a sweep point can
  only be rescued by more enemy HP, that point is discarded, not rescued.
- **No enemy `intentPattern` restructuring** — no extra FORECLOSE/HEDGE slots, no reordering, no
  moving a verb onto a second enemy. That is a difficulty change wearing a tuning costume.
- **No `RespondingPolicy.kt` / `LeveragePolicy.kt` behaviour changes.** That lever is spent;
  bundling it makes the number unattributable, again.
- **No `cards/all.json` changes.** That lever is spent too, and non-monotonically.
- **No `CombatEngine` logic change, no new `IntentType`, no new `CardResolver` tag mapping.**
  (Promoting the three parameters into `DebtConfig` is a §6-question-1 outcome, not a default.)
- **No changes to `HarnessBands` ratios or `DebtConfig.EXECUTION_THRESHOLD`.** Moving the anchor
  to make the band fit is re-baselining by stealth.
- Not a balance pass, not a roster expansion, not a content phase.

## 10. Rollback

Data-only and numeric: at most three integers in one JSON file (or, under §6 question 1 option
(b), three constants plus their read sites). `git revert` of the single change commit restores
threshold 27 / fee 9 / divisor 4; no schema, no save-format, no migration. The
`docs/BALANCE-BASELINE.md` sections stay as a record either way — a reverted lever whose number
was never written down would have to be re-measured, and this is the last lever, so its number is
the deliverable even when it fails.

## 11. Review workload forecast

- Threshold-only sweep landing one value: **1–3 lines** of JSON plus one results-doc section.
- With fee and divisor in the same landing: still under **10 lines** of JSON plus the doc.
- Under §6 question 1 option (b) (promote to `DebtConfig`): +3 constants, 2 `CombatEngine` read
  sites, ~2 tests. Estimate **40–80 lines**.
- `Decision needed before apply: Yes` — §3 and §6 are unanswered, and §3 can end the change.
- `Chained PRs recommended: No`.
- `400-line budget risk: Low`.
