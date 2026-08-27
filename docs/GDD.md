# Game Design Document — Debts & Decks

> Single source of truth for game mechanics. Update when mechanics change.
>
> **2026-08-25 rewrite**: the previous version of this document (dated 2025-08-11) had drifted a
> year out of sync with the code — it described a reward pool (Heavy Blade, Iron Wave, Pommel
> Strike...) that no longer exists and marked Debt as "MVP: not implemented" when it is in fact
> the core system. This rewrite documents (1) the game as actually built on `develop`, and
> (2) the target design for the "Debt as Leverage" pivot, which is the current MVP direction.
> See Engram `sdd/run-simulation-harness/explore` and topic key search "DIAGNÓSTICO MVP" project
> `debtsanddecks` for the full diagnostic this rewrite is based on.
>
> **2026-08-27 resync**: corrected against `develop` after the Debt-as-Leverage pivot shipped
> (C2 `0fb163b`, C4 `57b11c2`). Constant table, card counts, Execution rule and change-sequence
> status are now measured rather than assumed.

---

## Business decision BD-1 — v1 ships free

**Version 1 of Debts & Decks ships free on Google Play: no in-app purchases, no subscriptions,
no ads, no billing library, no analytics, and no third-party SDKs of any kind.** Monetization is
deferred whole to conditional backlog item **B1**; the project may legitimately end its life as a
portfolio piece. No phase may design an entitlement, product ID, paywall, ads integration or
analytics hook. A session that finds itself doing so has left scope — stop and report.

Two permanent consequences:

1. **Play "Data safety" stays "no data collected, no data shared"** for exactly as long as no
   analytics, ads or billing SDK is added. Adding one reopens BD-1 and rewrites the store
   declaration; it is not a quiet implementation detail.
2. **Publication uses a personal Google Play developer account.** A personal account must run a
   closed test with **at least 12 testers opted in continuously for at least 14 days** before it
   can even apply for production access. Tester recruitment is therefore on the critical path
   long before launch. *(Policy verified 2026-08-27 — re-check the Play Console requirement page
   before committing to any release date.)*

Also on record: once published free, this `applicationId` can never become a paid app on Play,
so any future monetization must be **additive content**, never a claw-back of what shipped free.

---

## Part 1 — Current Implementation (as of `develop`)

This section describes the shared foundations of the game as it runs today. The leverage pivot in
Part 2 has **already landed** on `develop`; this section has been corrected to match it.

### Core Loop

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   DRAW 5    │────▶│  PLAY CARDS │────▶│  RESOLVE    │────▶│  END TURN   │
│   CARDS     │     │  (Attack/   │     │  (Damage,   │     │  (Discard,  │
│             │     │   Skill,    │     │   Block,    │     │   Draw 5)   │
│             │     │   Effects)  │     │   Effects)  │     │             │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
       ▲                                                                   │
       └───────────────────────────────────────────────────────────────────┘
                              ▼
                     ENEMY TURN (auto) → CHECK WIN/LOSE
```

A **run** today is exactly 3 combats (Thug → Loan Shark → Collector) with 2 card-reward picks
total. No map, no rest, no persistence between runs.

### Resources

| Resource | Notes |
|----------|-------|
| **HP** | Player HP, does not regenerate between combats. 0 = defeat. |
| **Energy (Credit)** | 3/turn baseline, resets each turn. |
| **Block** | Temporary HP, resets at end of turn. |
| **Gold** | **Has no sink at all today.** The in-combat 1:1 Gold repay action was removed along with the rest of the free Debt valve (verified 2026-08-27: `RepayMode` no longer exists anywhere under `app/src/`). Gold now only accumulates and is only ever reduced by garnishment. *(Open balance question — giving Gold a job is the between-fight node, change C7 below. Recorded here, not fixed here.)* |
| **Debt** | The economy axis. See below. |

### The Debt Economy (`DebtConfig.kt`)

| Constant | Value | Effect |
|---|---|---|
| `INTEREST_RATE` | 0.15 | Per-turn compounding interest, `ceil(debt * 0.15)`. |
| `INTEREST_CAP` | 200 | Hard ceiling Debt can never exceed. Not a difficulty signal — just prevents runaway numbers. |
| `BREAK_THRESHOLD` | 30 | Schedules the forced "collector" encounter; garnishment ramps to max here. |
| `EXECUTION_THRESHOLD` | 50 | Death line. Debt above this makes any debt-increasing action immediate defeat. Deliberately **above** `BREAK_THRESHOLD` — see Part 2, confirmed rule 2. |
| `MAX_GARNISH_RATE` | 0.75 | Max fraction of a Gold reward redirected to Debt repayment at/above `BREAK_THRESHOLD`. |
| `DEBT_SCALING_ATTACK_DIVISOR` | 10 | Extra damage per hit for `debt_scaling` ATTACK cards: `floor(debt / 10)`, on top of the unconditional flat Leverage bonus every attack already gets. |
| `DEBT_PAYOFF_DIVISOR` | 2 | `debt_payoff` cards deal damage / gain Block equal to `floor(debt / 2)`. No wipe, no repayment — the "keep the band" sibling of the all-in execution wipe. |
| `DEBT_DRAW_DIVISOR` | 10 | `debt_draw` cards draw `DEBT_DRAW_BASE + floor(debt / 10)`. |
| `DEBT_DRAW_BASE` | 1 | Base draw for `debt_draw` cards at zero/low Debt. |
| `CHAPTER_11_HP_COST` | 15 | HP cost of the Chapter 11 card's full Debt wipe. |

> This table is the complete set of `const val` declared in `core/combat/DebtConfig.kt`,
> verified 2026-08-27. `USURY_HP_RATIO` and `REPAY_DISCARD_VALUE`, documented in earlier
> versions of this file, **no longer exist in the codebase** — the usury mechanic and the free
> discard-to-repay valve were both removed during the Part 2 pivot.

**Known imbalance — historical, resolved by C2/C4 (kept for context, no longer true):**

- ~~Borrowing costs 1 Debt per Credit shortfall; discarding a card repays 5 Debt for free. That
  5:1 exchange rate lets a player cycle debt to ~0 every 2-3 turns indefinitely.~~ The free
  discard-to-repay valve no longer exists.
- ~~Usury cannot kill — it has no teeth as a loss condition.~~ The usury HP burn was replaced by
  the Execution death line at `EXECUTION_THRESHOLD`.
- ~~Debt and Gold are effectively one signed scalar (`gold − debt`).~~ They are now fully
  independent, because Gold's 1:1 Debt sink was removed — which is its own open problem (see the
  Gold row above and change C7).

**Still open:**

- Interest is regressive at low Debt (`ceil` makes the first point of Debt cost 100%/turn, while
  going from 1 to 6 is free at the +1/turn floor). Not yet addressed.

### Card Pool (27 cards)

Measured 2026-08-27 from `app/src/main/assets/cards/all.json`: **27 card IDs total = 4 starters
+ a 23-card reward pool** (`starter`-tagged cards are never offered as rewards).

**Starters (4):** Strike (1 cost, 6 dmg), Defend (1 cost, 5 block), Bash (2 cost, 8 dmg +
Vulnerable 1), Survive (1 cost, 8 block).

**Reward pool, pre-pivot economy cards (15):** `compound_interest`, `subprime_loan`,
`debt_forgiveness`, `partial_forgiveness`, `tactical_bankruptcy`, `reverse_mortgage`,
`foreclosure_express`, `ghost_collector`, `golden_credit`, `mortgage_collateral`,
`asset_auction`, `risky_investment`, `bounced_check`, `zombie_debt`, `eternal_debt`.

**Reward pool, added by the pivot (8):** `ejecucion`, `refinanciar` (C2 Liquidation);
`leverage_strike`, `asset_bubble`, `overdraft`, `collateral_hold`, `repo_expert`,
`emergency_fund` (C4 leverage-payoff table).

See `assets/cards/all.json` for exact numbers.

**The three "dead" cards after the C4 rework** (verified against `all.json`, 2026-08-27):

- `eternal_debt` — SKILL, cost 1, `+3 Debt`, tags `add_debt` / `recursive` / `debt_scaling`.
  Now a Leverage enabler: it buys Debt cheaply *and* scales with it.
- `zombie_debt` — SKILL, cost 0, `+2 Debt`, `+1 Credit`, tags `add_debt` / `recursive` /
  `gain_credit`. Free Debt plus tempo; a payoff enabler rather than dead weight.
- `bounced_check` — ATTACK, cost 1, 5 dmg, `+4 Debt`, tag `add_debt`.
  Under Leverage the added Debt is no longer pure downside, but it still sits close to
  `foreclosure_express` (ATTACK, cost 1, 6 dmg, `+4 Gold`, no Debt). **Whether `bounced_check`
  earns its slot is an open balance question for the C8 balance pass** — it is not asserted here
  either way.

### Enemies (3)

| Enemy | HP | Tier | Notable |
|---|---|---|---|
| Thug | 24 | Normal | 8 dmg × 2, then Strength buff. |
| Loan Shark | 40 | Elite | 10 dmg, Weak debuff, enrages below half HP. |
| Collector (boss) | 56 | Boss | 12 dmg / 9×2 multi / **LEVY +5 Debt** / buff / debuff. Debuff-resistant. |

`LEVY` is currently the only enemy→economy interaction in the game, and it fires once per fight.

---

## Part 2 — Debt as Leverage (MVP pivot — shipped)

**Status: SHIPPED on `develop`.** The pivot is implemented, not planned. C2 `debt-as-leverage`
landed as commit `0fb163b`, C4 `leverage-payoff-cards` as commit `57b11c2` (both hashes
re-verified 2026-08-27). This section therefore documents **shipped behaviour**, not a target —
where a rule below differs from what the code does, the code wins and this document is the bug.

### Why this pivot

*(Historical rationale, written before the pivot shipped — kept because it explains the numbers.)*

The pre-pivot Debt system only subtracted (interest, usury, garnishment, levy) and had a free
escape valve, so the optimal play was always "get Debt back to zero." A resource whose only
optimum is a corner can't generate the "best possible move" tension good deckbuilders/roguelikes
run on. The fix wasn't more generic synergy cards (that copies Slay the Spire's toolkit on 15
cards instead of 350) — it was making Debt itself do double duty as power **and** risk, so the
optimal amount of Debt to be carrying at any moment is an interior point you have to calculate
under pressure, not a corner.

### Confirmed rules

1. **Leverage — attacks scale with current Debt.**
   All `ATTACK` cards gain **+1 damage per 5 Debt** (integer division, floor). At Debt 30, a base
   Strike (6 dmg) hits for 12. Implemented alongside the existing `debt_scaling` tag read in
   `CardResolver` (already used by `compound_interest`).

2. **Execution — Debt above `EXECUTION_THRESHOLD` (50) is immediate defeat.**
   As shipped, the death line is its **own** constant, `EXECUTION_THRESHOLD = 50`, deliberately
   set **above** `BREAK_THRESHOLD = 30`. The two numbers do different jobs and must not be
   re-unified:

   | Constant | Value | Job |
   |---|---|---|
   | `BREAK_THRESHOLD` | 30 | Schedules the forced collector encounter; garnishment ramp ceiling. |
   | `EXECUTION_THRESHOLD` | 50 | Death line — any debt-increasing action above it is defeat. |

   **Why they differ (do not "simplify" this away):** the forced collector must arrive *before*
   death, so the Leverage band `5..49` is actually playable. The original design reused
   `BREAK_THRESHOLD` for both, and with Execution == Break == 30 an interest tick alone crossed
   the line almost every turn — the mechanic was unplayable. The split is the fix. See the KDoc
   on `EXECUTION_THRESHOLD` in `core/combat/DebtConfig.kt` and C2 apply-progress decision A.

   Execution replaces `usuryDamage`'s HP-burn-that-never-kills with a real loss condition: the
   same resource that gives you power is the resource that kills you.

3. **Liquidation — two card templates that spend accumulated Debt:**
   - **Ejecución** (cost 2): deal damage equal to current Debt, then Debt → 0. The all-in payoff.
   - **Refinanciar** (cost 1): halve current Debt, gain Block equal to the amount cancelled. The
     defensive partial cash-out.

   Two distinct payoffs (offense vs. defense) so Liquidation isn't a single obvious button.

### Companion changes (from diagnostic, not numbered as rules — see SDD sequence)

- **DONE / superseded — remove the free discard-to-repay valve and the in-combat Gold repay.**
  Both are already gone from `develop`: a search for `RepayMode`, `repayDebt` and
  `REPAY_DISCARD_VALUE` across `app/src/` returns zero matches (verified 2026-08-27). The only
  surviving repayment surface is the *card* effect `CardResolver.Effect.RepayDebt`, which costs
  a card play and Credit and is therefore not a free valve.
  **Still open, carried forward:** Debt repayment was supposed to move to a between-fight node so
  Gold would have a job. That node does not exist yet, so Gold currently has **no sink at all** —
  see change **C7 `between-fight-node`**.
- Fix the interest formula's low-end regressiveness. *(Still open.)*
- Fix the break-encounter bug that currently summons the boss mid-run without advancing
  `encounterIndex`. *(Still open — not re-verified by the P0 docs pass.)*

### Card migration under Leverage

Delivered by C4 (`57b11c2`) — see the reworked definitions in the Card Pool section above.

- `eternal_debt` / `zombie_debt` **are** viable now: a leverage-focused deck wants Debt to go up
  cheaply, so "Debt that keeps coming back" is a payoff enabler, not dead weight.
- `bounced_check` was **not** resolved by C4. It still sits close to `foreclosure_express`, and
  the comparison is deferred to the C8 balance pass.

### MVP Scope (target — the pivot has shipped, this run structure has not)

| Element | Target | Why |
|---|---|---|
| Encounters per run | **8** (6 normal/elite + 1 mid-boss + 1 final boss) | Enough turns (~35-45) for the compound-interest curve to actually differentiate "leverage early" from "leverage late." |
| Card picks per run | **6-8** | Deck grows 10 → 16-18 cards — the minimum where the deck has an identity instead of starter deck + 2 patches. |
| Reward pool | **24-28 cards in 3 legible archetypes**: Leverage (Debt-high payoffs), Liquidity (Debt control/tempo/draw), Pressure (baseline damage/block) | Lets two runs feel different from each other. |
| Enemies | **6-8**, each with at least one economy-touching intent (levy, gold garnish, interest-rate spike) | Today the enemy only touches the economy once per run. |
| Between-fight node | 1 screen, 3 choices: repay Debt / buy a card / remove a card | Gives Gold a job once its in-combat use is removed. |
| Instrumentation | Headless run simulator (see `run-simulation-harness` SDD change) | Balance by measurement, not by eyeballing APK playtests. |

**Explicitly out of scope:** branching map, relics, potions, card upgrades, save/persistence,
multi-enemy encounters (optional/last, code already supports it but shifts focus away from the
Debt axis), more art/SFX/languages, tutorial/settings/accessibility.

### Success criteria (measurable via the simulator + playtests)

1. Win rate for a competent/greedy scripted policy: **35-55%** (must stay under 70%, or the
   optimal line is too obvious and the risk axis isn't real).
2. Average peak Debt in **won** runs **> 25** — if players win without leveraging, the hook isn't
   landing.
3. ≥ 2 distinct archetypes show up across different playtesters' winning decks.
4. ≥ 60% of external playtesters start a third run unprompted.
5. Run length 12-18 minutes.

### Playtest protocol

3-5 external testers, 3 runs each, no explanation beyond the screen. Ask only, in order: *at
what turn did you realize what Debt is for?*, *at what point did you know you'd lose?*, *what
card did you look for in your second run?* (the third question is what actually measures build
diversity).

---

## SDD Change Sequence (C0–C9)

Each row is one PR / one `tasks.md`, revertible on its own, game stays playable after each.
"Owner" marks what needs the developer's design judgment before it can even be specified, versus
what's mechanical enough to hand to Pi (the project's cheaper fallback coding agent) as-is.

Status column verified 2026-08-27 by command, not from memory. Every `DONE` row names a commit
that resolves on `develop`.

| # | Change | Delivers | Owner | Status |
|---|---|---|---|---|
| C0 | `gdd-rewrite-leverage-design` | **This document.** | Developer | **DONE** — this file exists (`9c94a97`) |
| C1 | `run-simulation-harness` | Headless simulator + invariant tests | Pi | **DONE** — `dc65f08`; simulator present at `app/src/test/java/com/debtsdecks/core/simulation/` (`RunSimulator.kt`, `LeveragePolicy.kt`, `SimulationReport.kt`) |
| C2 | `debt-as-leverage` | The pivot itself (Leverage/Execution/Liquidation rules above) | Spec: developer · Apply: Pi | **DONE** — `0fb163b` |
| C3 | `remove-free-debt-valve` | Removes the free discard/gold repay in combat | Pi | **NEEDS RE-VERIFICATION** [^c3] |
| C4 | `leverage-payoff-cards` | 6-10 new cards, retire/rework the 3 dead cards | Card table: developer · Apply: Pi | **DONE** — `57b11c2` |
| C5 | `run-length-and-encounter-slots` | 8-encounter run structure | Sequence: developer · Refactor: Pi | **DONE** — `5534524` |
| C6 | `enemy-roster-and-economy-intents` | 5 new enemies + economy-touching intents | Table: developer · Apply: Pi | PENDING |
| C7 | `between-fight-node` | Gold's job outside combat | Mixed — supervise the render/input half (no headless coverage there) | PENDING — **now urgent**: Gold has no sink at all on `develop` |
| C8 | `balance-pass-1` | Tune constants until success criteria are met | Pi (measurable target via simulator) | PENDING |
| C9 | `two-enemy-encounters` *(optional)* | Multi-enemy variety, code-cheap | Pi — skip if C8 already meets criteria | PENDING (optional) |

[^c3]: **C3 status is deliberately unresolved.** A search for `RepayMode`, `repayDebt` and
`REPAY_DISCARD_VALUE` across `app/src/` returns **zero matches** (2026-08-27), and commit
`9afd532 feat(combat): remove free in-combat Debt repay valve (GOLD/DISCARD)` is on `develop`.
So the valve C3 was scoped to remove is already gone. What is *not* established is whether C3's
scope was only the valve removal — in which case it is complete — or whether it also owned
Debt repayment's move to a between-fight node, which has **not** happened. Marking this row
`DONE` or `PENDING` would send a downstream session to skip or rewrite code on a false premise.
Re-scoping C3 is the job of the change that picks it up (P2), not of this documentation pass.

Open risk to watch: the leverage hypothesis itself could be wrong (an obvious optimal line would
make the risk axis decorative). Re-run the simulator's greedy policy after C2 lands — if it wins
above ~70%, tighten Execution before investing further down the sequence.

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-08-11 | Initial MVP GDD (superseded — described unimplemented Debt system) | — |
| 2026-08-25 | Full rewrite: documents actual current implementation + Debt-as-Leverage pivot design and SDD sequence | Claude Code + developer |
| 2026-08-27 | `play-store-launch` P0 resync against `develop`: Debt constant table corrected to the 10 constants that exist (`USURY_HP_RATIO` and `REPAY_DISCARD_VALUE` removed); Gold's sink corrected to "none"; Part 2 marked shipped (C2 `0fb163b`, C4 `57b11c2`); Execution rule corrected to `EXECUTION_THRESHOLD = 50` with its rationale; card pool corrected to 27 (4 + 23); C0–C9 table gained a verified Status column with C3 flagged `NEEDS RE-VERIFICATION`; BD-1 recorded | Claude Code + developer |

---

*Last updated: 2026-08-27 — Keep this file in sync with code. C2, C4 and C5 have landed; the next
required update lands with whichever change resolves C3's scope, or with C6/C7/C8, whichever
comes first.*
