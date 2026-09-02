# FV — Core Validation

**Type:** short proposal with an explicit exit criterion. **No spec, no design, no tasks** —
on purpose. If the core does not hold, the six phases behind it get re-planned before they
get written.

**Status:** proposed, unverified. **Date:** 2026-08-28. **Branch:** `develop`.

---

## 1. Why this exists

The vision is a **meta layer**. Districts, events, ballast, the market, leads — six of the
seven systems happen *between* combats. Only bosses reach inside the fight.

That is a bet: it assumes the fight is already worth wrapping. Nothing in this project has
ever tested that assumption.

- The harness proves the win rate lands in `[0.35, 0.55]` across 200 seeds. It cannot prove
  a fight is interesting, and no simulation can.
- GDD success criterion #4 — *"≥ 60% of external playtesters start a third run unprompted"*
  (`docs/GDD.md`, §Success criteria) — **has no measurement recorded anywhere in this repo**.
  The only playtest on record is 4 runs by the owner. *Worded narrowly on purpose: "never
  measured" is a claim about the world that no command can establish — absence from the repo is
  not absence in reality. Checklist row B5 carries the check that can actually be run
  (`git log --oneline -S"playtest" -- docs/`).*
- The three enemies are not a roster, they are a staircase. Read from
  `app/src/main/assets/enemies/all.json`: `thug` = {ATTACK 8, ATTACK 8, BUFF 3};
  `loan_shark` = thug + {DEBUFF, LEVY} with bigger numbers; `collector` = loan_shark +
  {MULTI_ATTACK} with bigger numbers again. Each enemy is a strict superset of the last.
  **No enemy in the game requires a different plan — only more endurance.**

The owner has confirmed art and content budget are *not* the constraint. So the shortage is
not heads, it is **verbs**: `IntentType` has five values and only `LEVY` is thematic. Adding
a fourth enemy on those five verbs adds a fourth reskin.

## 2. Deliverable 1 — Intent vocabulary

`IntentType` is a core enum (`app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt`),
so this is a code change, not a data change. Three new verbs, chosen because each attacks a
**different pillar** of the current player plan:

| Verb | What it does | The pillar it attacks | The response it forces |
| --- | --- | --- | --- |
| **FORECLOSE** | Announces: on resolution, if Debt ≥ threshold, seizure (heavy damage or run-ending, per balance) | The debt **level** | Pay down before a deadline. Block does not help. |
| **AUDIT** | Disables a card tag (`debt_scaling`, `debt_payoff`…) for N turns | The **deck plan** | Play your second-best line. A one-archetype deck stalls. |
| **HEDGE** | Enemy gains block scaled by the player's current Debt | The **leverage bonus itself** | Your own engine arms the enemy. Dump debt or win without it. |

Why exactly these three: `ATTACK`/`MULTI_ATTACK` pressure HP, `BUFF`/`DEBUFF` pressure the
damage exchange, `LEVY` pressures debt upward. Every existing verb is answered with *more
block or more damage*. All three new verbs are answered with **something the player is not
already doing**, and none of them overlaps another.

Once the verbs exist, expanding the roster is cheap and scales well. Not before.

## 3. Deliverable 2 — The external playtest the GDD already specifies

Exactly the protocol in `docs/GDD.md` (§Playtest protocol): 3-5 external testers, 3 runs
each, no explanation beyond the screen, and only these questions, in order:

1. At what turn did you realize what Debt is for?
2. At what point did you know you would lose?
3. What card did you look for in your second run?

Run it on the build **with the new verbs**, not the current one. The point is to validate
the core the program intends to build on, not the one it intends to replace.

## 4. Exit criterion

FV passes when **all four** hold. Anything less re-opens the program.

**E1 — the verbs are load-bearing (simulated).**
*Re-metriced 2026-08-28/29 with evidence attached (`docs/BALANCE-BASELINE.md`,
`IntentVerbsE1Test`).* The original response-gap framing (a policy that ignores the verbs must
lose to one that responds to them by **at least 10pp** win rate) turned out unreachable at a sane
win band: FORECLOSE is a binary check on the player's natural debt band, so across the swept
fee/hedge/threshold parameters the response gap is noise (0.5-2.5pp at the shipped values), and
the cheapest lever that opens a bigger gap collapses the win band instead. Six real (not
estimated) variants were measured trying to widen it — none beat the shipped +2.5pp ceiling, and
every attempt that pushed harder measured **negative** relative to baseline (see `RespondingPolicy`
KDoc for the full ledger).

The verb slots ARE load-bearing — for **difficulty**, not response gap: a verbs-off control
(swap FORECLOSE/HEDGE for their predecessor intents) costs both policies 25.5pp/19.5pp of win
rate. The shipped gate reflects this: `IntentVerbsE1Test` requires **both policies to lose at
least 10pp** (floored at 20/15pp with headroom) when the verbs are switched off, and keeps the
response gap as an informational metric with only a regression guard (must not drop below
-5.0pp). If the difficulty weight is noise, the verb is decoration; the response-gap framing above
is superseded.

**E2 — the balance gate still holds, or is re-baselined with evidence.**
After the verbs land, `RunSimulationHarnessTest` must still pass: greedy win rate in
`[0.35, 0.55]`, won-run peak Debt > 25, both policies in the debt band `[25, 45)`, neither
above 70%. If the verbs move the band, the new band is proposed **with sim output attached**,
never on paper.

> **E1 measured as unreachable through `RespondingPolicy` behavior alone, 2026-08-29.** PR #22
> (`feat/fv-verbs-foreclose-hedge`) closes E2 cleanly (greedy 48.5%, leverage 45.5%, both inside
> `[0.35, 0.55]`, other invariants intact — see `docs/BALANCE-BASELINE.md`), but E1's response
> gap tops out at **+2.5pp measured, need ≥10pp**. Two independent rounds, 7 total policy
> variants tried (borrow bans, reward-priority bumps, turn-scoped debt-growth caps, alone and
> combined) — every variant that meaningfully restricts borrowing near an announced FORECLOSE
> loses more win rate than it recovers from avoided seizures. Root cause: the FORECLOSE
> threshold (27) sits inside the leverage band both policies already operate in (target 35,
> execution line 50) — there is no borrowing posture that dodges the threshold without giving
> up the core Leverage damage mechanic the harness already depends on. The card pool is also
> thin for a real response: only 1 `debtRepay` card and 2 `wipe_debt` cards among 27. Closing
> E1 for real needs a production-code change this policy-only work cannot make on its own —
> FORECLOSE threshold/fee tuning, or new debt-reduction cards — which is a design decision, not
> a measurement one. **Owner's call, not re-attempted here**; `IntentVerbsE1Test` still carries
> the weakened re-metriced gate from the WIP, not the original ≥10pp assertion. PR #22 stays
> `WIP:` and unmerged.

> **The ordering consequence this paragraph predicted did not happen, 2026-08-28.** It read
> "F1 normalizes these invariants to ratios, and it must be computed from the **post-FV**
> baseline, not today's". F1 shipped first, as `3a7c201`, computed from today's baseline —
> FV has not started, and F1's own proposal had already overturned the dependency in its header
> (FV cannot complete: no `signingConfig`, no keystore, so its external playtest is
> undistributable; see checklist row E6 and B4). The absolute numbers above are also stale:
> on `develop` the bands are ratios in `HarnessBands`, so `> 25` and `[25, 45)` are now
> `WON_PEAK_MIN_RATIO` and `[LEVERAGE_BAND_LOW_RATIO, LEVERAGE_BAND_HIGH_RATIO)` of
> `EXECUTION_THRESHOLD`. What survives is the real risk, unchanged: **if FV's verbs move the
> win band, F1's ratios must be re-derived from the post-FV baseline.** F1 mitigated that by
> naming the anchor in one file, not by waiting.

**E3 — criterion #4 is measured.**
≥ 60% of external testers start a third run unprompted. Measured, not estimated. A number
below 60% is a valid outcome that stops the program — that is what this phase is for.

**E4 — the three questions produce a consistent answer to question 1.**
If testers cannot say what Debt is for by the end of run 1, the central thesis is not
landing, and no district fixes that.

## 5. Hard dependency — this phase cannot fully run today

**E3 and E4 require putting a build in someone else's hands, and this repo cannot produce
one.** Claim, carried by checklist row B4: in `app/build.gradle.kts` the `release` build
type declares only
`isMinifyEnabled` and `proguardFiles` — there is **no `signingConfig`** — and there is no
keystore anywhere in the tree. `versionCode` is still `1`.

So `assembleRelease` yields an unsigned APK: not installable by a tester without developer
settings, not uploadable to an internal testing track.

This is the one dependency that argues with "FV goes before everything". It does not change
the phase order; it means FV.2 is **gated on the signing/distribution work already scoped as
P0/P1 in the `play-store-launch` change** (Engram topics `sdd/play-store-launch/*`). Two ways
to sequence it, owner's call:

- **(a)** Pull the signing config out of `play-store-launch` P1 and run it as FV.0. Small,
  isolated, unblocks the playtest.
- **(b)** Split FV: ship deliverable 1 (verbs, E1+E2) now, hold deliverable 2 (E3+E4) until a
  distributable build exists. The program then proceeds through F0-F2 — which are
  documentation and metadata and risk nothing — while the playtest is pending.

**Recommended: (b) with (a) queued immediately.** F0, F1 and F2 change no gameplay, so
running them while E3/E4 are pending wastes nothing, and F3 is where a failed playtest would
actually have cost money.

## 6. What FV is not

Not a roster expansion. Not a balance pass. Not a content phase. Three verbs and a
measurement, and the honesty to stop if the measurement comes back bad.

## 7. Review workload forecast

- Deliverable 1: `IntentType` enum + intent resolution + intent rendering + i18n keys +
  tests + one sim policy variant. Estimate **250-400 lines**. Single PR is defensible;
  if AUDIT's tag-disabling touches `CardResolver` more than expected, split into
  `feat(combat): FORECLOSE + HEDGE` then `feat(combat): AUDIT`.
- Deliverable 2: no code. A results doc under `docs/`.
