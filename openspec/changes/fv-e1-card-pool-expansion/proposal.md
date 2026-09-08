# FV.E1 — Close E1 by widening the debt-answer card pool, not the policy

**Type:** short proposal with an explicit exit criterion. **No spec, no design, no tasks** —
same reason as its parent and its sibling: if the measurement comes back bad, the next lever
gets re-scoped before it gets written.

**Status:** proposed, unverified. **Date:** 2026-08-29. **Branch base:** `feat/fv-verbs-foreclose-hedge` (PR #22).
**Continues:** `openspec/changes/fv-core-validation/proposal.md` §4, criterion E1.
**Follows:** `openspec/changes/fv-e1-wipe-debt-response/proposal.md` — its lever is spent; this is its §4 option (b).
**Depends on:** PR #22 landing or staying alive (`RespondingPolicy.kt` and `IntentVerbsE1Test` do
not exist on `develop`). **Blocks:** FV E1, and nothing else.

---

## 1. What is actually open

E1 requires a policy that responds to FORECLOSE/HEDGE to beat one that ignores them by
**≥ 10pp win rate over 200 seeds**. The best number ever measured is **+2.5pp**.

The sibling change spent the policy lever completely: **13 behavioural variants of
`RespondingPolicy.kt` across three calibration sessions**. One of them — giving `wipe_debt`
cards top draft priority — *regressed to **-7.5pp***. Do not repeat those experiments; the data
is in `fv-e1-wipe-debt-response/proposal.md` and the FV.E1 sections of `docs/BALANCE-BASELINE.md`.

The root cause is established, not suspected: **FORECLOSE's threshold (27) sits inside the
shared leverage band both policies already operate in** — `HarnessBands` target 35,
`DebtConfig.EXECUTION_THRESHOLD` 50, band `[25, 45)`. No borrowing or drafting posture dodges a
deadline placed inside that band without giving up the Leverage mechanic itself. So the
responding policy's ceiling is set by **what it can draft**, not by how it plays.

The sibling named two remaining levers. **(a)** retuning FORECLOSE/HEDGE numbers is boxed in by
E2's band and can widen the gap the wrong way — out of scope here, its own proposal if ever.
**(b)** the card pool is what is left. This is (b).

## 2. The lever, and its precise shape

Verified in the working tree, not taken on trust:

- The pool is **27 cards** (`app/src/main/assets/cards/all.json`).
- Exactly **one** card repays Debt by amount: `partial_forgiveness` — `debtRepay: 8`, `cost: 0`,
  `COMMON`, `tags: []`.
- Exactly **two** wipe Debt entirely: `debt_forgiveness` (`cost: 2`, `RARE`) and
  `tactical_bankruptcy` (`cost: 1`, `RARE`, `selfDamage: 8`).
- `CardResolver.kt` already maps `wipe_debt` → `Effect.WipeDebt` and `debtRepay` → repay, so
  **a card reusing either needs zero engine code**.
- Every card today lives in exactly three files: `cards/all.json`,
  `i18n/strings.properties`, `i18n/strings_es.properties`.

`partial_forgiveness` is **already cost 0** — there is no "make it cheaper" move left. Three
sub-levers remain, in ascending blast radius:

| # | Sub-lever | Touches | Cost |
| --- | --- | --- | --- |
| (i) | **Accessibility**: lower `debt_forgiveness` / `tactical_bankruptcy` from `RARE` | `all.json` only | 2–4 lines, no content |
| (ii) | **Buff existing**: raise `partial_forgiveness`'s `debtRepay`, or cut `tactical_bankruptcy`'s `selfDamage` | `all.json` only | data-only |
| (iii) | **Add new** `debtRepay` / `wipe_debt` cards | `all.json` + both `.properties` (+ art backlog) | per card |

**The symmetry problem this must survive:** the pool feeds `chooseReward` for **both** policies.
Making the answer easier to draft also feeds the ignoring policy's deck. The gap widens only if
the answer is worth more to a policy that plays it on a FORECLOSE turn than to one that plays it
as filler. That is precisely what the measurement tests — and it is why "both rates rise, gap
unchanged" is a fully expected outcome, not a surprise.

## 3. Open design decisions — proposal question round for the owner

**Not decided here**, deliberately, the same way the sibling deferred its "`wipe_debt` above
`debt_payoff`, or only above `debtRepay`?" question instead of silently choosing:

1. **Which sub-lever(s)?** (i) alone first — cheapest, reversible, no content, no i18n — or
   (i) + (iii) in one measurement?
2. **If new cards: how many, and how big does the pool get?** 27 → 28 or 27 → 30. A bigger pool
   dilutes every other draft; that alone can move E2 even before the new cards are played.
3. **Costs / rarities / tags for anything new.** Reuse `wipe_debt` / `debtRepay` (no engine
   change) or introduce a new tag (engine change — out of this proposal's scope)?
4. **Touch the two existing wipe cards at all?** A rarity change alters how existing runs feel,
   not only the sim.
5. **Does new art gate the merge**, or does it ride the `docs/ART-PIPELINE.md` backlog? (Note the
   known hazard: 15/27 existing cards already carry baked rules text that contradicts `all.json`.)

If these are unanswered, this change **stops and asks**. It does not pick.

## 4. Measurement plan

One pass, both gates in the same run, so the numbers are comparable:

- **`IntentVerbsE1Test`, 200 seeds**, responding vs ignoring, **unchanged methodology** from the
  13 prior variants. Changing the method and the pool at once makes the number unattributable.
- **`RunSimulationHarnessTest` in the same pass.** E2 is currently **CLOSED**: greedy 48.5%,
  leverage 45.5%, both inside `[0.35, 0.55]`; leverage-debt band `[25, 45)` holds; neither policy
  ≥ 70%. Keeping it closed is non-negotiable.
- **`HarnessDeterminismTest`** too: a pool change alters draft order, and this harness was
  non-deterministic once before.
- Record in `docs/BALANCE-BASELINE.md`: the E1 gap, **both** policies' absolute win rates, the E2
  numbers, and the exact gradle command that produced them.

## 5. Exit criterion

**Pass:** gap **≥ 10pp over 200 seeds** *and* E2 still green in the same run *and* the numbers in
`docs/BALANCE-BASELINE.md` with their command.

**Fail — a real, expected, complete outcome.** Under 10pp: write the number down and **stop**.
Do not weaken `IntentVerbsE1Test` to manufacture green — the 2026-08-28 re-metric already did
that once and it is still on the branch as known debt. Do not chain lever (a) automatically;
that is the owner's call, on its own proposal.

**Also a fail:** E1 passes but E2 leaves its band. That is a failure of *this* change, not a
re-baselining opportunity. Per `fv-core-validation` §4, a new band arrives with its own proposal
and its own sim output attached, never on paper.

## 6. Non-goals

- **No enemy HP or damage changes.** Owner constraint: Debt, not HP, is the axis players manage.
  `enemies/all.json` is untouched. This lever gives the player more tools to manage Debt; it does
  not make enemies hit harder.
- **No FORECLOSE/HEDGE threshold, fee or divisor changes.** That is lever (a).
- **No `CombatEngine`, no `DebtConfig`, no new `IntentType`, no new `CardResolver` tag mapping.**
- **No `RespondingPolicy.kt` behaviour changes.** That lever is spent; bundling a policy tweak
  with a pool change makes the resulting number unattributable again. If the pool change makes a
  policy tweak look attractive mid-run, that is a finding to record, not a change to bundle.
- Not a balance pass, not a roster expansion, not a content phase.

## 7. Rollback

Data-only, additive or numeric. `git revert` of the single change commit restores the 27-card
pool and the prior rarities/values; no engine code and no schema is involved. The measurement
sections written to `docs/BALANCE-BASELINE.md` stay as a record either way — a reverted lever
whose number was never written down would have to be re-measured.

## 8. Review workload forecast

- Sub-lever (i) or (ii) alone: **2–10 lines** of JSON plus one results-doc section.
- With (iii) at 2–3 new cards: ~40 JSON lines + ~6 i18n keys × 2 languages + doc.
  Estimate **60–140 lines**.
- `Decision needed before apply: Yes` — §3 is unanswered.
- `Chained PRs recommended: No`.
- `400-line budget risk: Low`.
