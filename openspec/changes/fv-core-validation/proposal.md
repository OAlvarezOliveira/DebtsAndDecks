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
  (`docs/GDD.md`, §Success criteria) — **has never been measured**. The only playtest on
  record is 4 runs by the owner.
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
A policy that ignores the new verbs must measurably lose to one that responds to them. This
is the sim-able half of "the fight demands a plan": you cannot simulate fun, but you can
simulate *whether ignoring a mechanic costs you*. Concretely: add a policy variant that
never reacts to FORECLOSE/AUDIT/HEDGE and require its win rate to sit **at least 10pp below**
the responding policy over 200 seeds. If the gap is noise, the verb is decoration.

**E2 — the balance gate still holds, or is re-baselined with evidence.**
After the verbs land, `RunSimulationHarnessTest` must still pass: greedy win rate in
`[0.35, 0.55]`, won-run peak Debt > 25, both policies in the debt band `[25, 45)`, neither
above 70%. If the verbs move the band, the new band is proposed **with sim output attached**,
never on paper. Note the ordering consequence: F1 normalizes these invariants to ratios, and
it must be computed from the **post-FV** baseline, not today's.

**E3 — criterion #4 is measured.**
≥ 60% of external testers start a third run unprompted. Measured, not estimated. A number
below 60% is a valid outcome that stops the program — that is what this phase is for.

**E4 — the three questions produce a consistent answer to question 1.**
If testers cannot say what Debt is for by the end of run 1, the central thesis is not
landing, and no district fixes that.

## 5. Hard dependency — this phase cannot fully run today

**E3 and E4 require putting a build in someone else's hands, and this repo cannot produce
one.** Verified 2026-08-28 in `app/build.gradle.kts`: the `release` build type declares only
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
