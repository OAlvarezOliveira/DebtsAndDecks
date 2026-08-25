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

---

## Part 1 — Current Implementation (as of `develop`)

This section describes the game as it runs today, before the leverage pivot (Part 2) lands.

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
| **Gold** | Only sink today is 1:1 Debt repayment (`RepayMode.GOLD`). No other spend exists. |
| **Debt** | The economy axis. See below. |

### The Debt Economy (`DebtConfig.kt`)

| Constant | Value | Effect |
|---|---|---|
| `INTEREST_RATE` | 0.15 | Per-turn compounding interest, `ceil(debt * 0.15)`. |
| `INTEREST_CAP` | 200 | Hard ceiling Debt can never exceed. Not a difficulty signal — just prevents runaway numbers. |
| `BREAK_THRESHOLD` | 30 | Schedules the forced "collector" encounter; garnishment ramps to max here. |
| `MAX_GARNISH_RATE` | 0.75 | Max fraction of a Gold reward redirected to Debt repayment at/above `BREAK_THRESHOLD`. |
| `USURY_HP_RATIO` | 0.5 | Debt above half max HP burns the overflow as HP damage — **cannot drop HP below 1**, so it can never itself cause defeat. |
| `REPAY_DISCARD_VALUE` | 5 | Debt removed by discarding a card via the repay action — free, no turn cost, repeatable. |

**Known imbalance (informs the Part 2 pivot):**

- Borrowing costs 1 Debt per Credit shortfall; discarding a card repays 5 Debt for free. That
  5:1 exchange rate lets a player cycle debt to ~0 every 2-3 turns indefinitely — interest,
  usury, and garnishment never get a chance to bite.
- Interest is regressive at low Debt (`ceil` makes the first point of Debt cost 100%/turn, while
  going from 1 to 6 is free at the +1/turn floor).
- Usury cannot kill — it has no teeth as a loss condition.
- Debt and Gold are effectively one signed scalar (`gold − debt`), not two independent resources,
  since Gold's only sink is repaying Debt 1:1.

### Card Pool (19 cards)

**Starters (4):** Strike (1 cost, 6 dmg), Defend (1 cost, 5 block), Bash (2 cost, 8 dmg +
Vulnerable 1), Survive (1 cost, 8 block).

**Economy pool (15):** `compound_interest`, `subprime_loan`, `debt_forgiveness`,
`partial_forgiveness`, `tactical_bankruptcy`, `reverse_mortgage`, `foreclosure_express`,
`ghost_collector`, `golden_credit`, `mortgage_collateral`, `asset_auction`, `risky_investment`,
`bounced_check`, `zombie_debt`, `eternal_debt` — see `assets/cards/all.json` for exact numbers.

Three cards currently have no upside under the current rules — `eternal_debt` (+4 Debt, nothing
else), `zombie_debt` (+2 Debt, self-clones into the deck), `bounced_check` (strictly dominated by
`foreclosure_express`). They were authored for existing card art before the payoff mechanic
existed to justify them; Part 2 gives Debt a payoff, which is what makes these cards worth
keeping (see §Migration below).

### Enemies (3)

| Enemy | HP | Tier | Notable |
|---|---|---|---|
| Thug | 24 | Normal | 8 dmg × 2, then Strength buff. |
| Loan Shark | 40 | Elite | 10 dmg, Weak debuff, enrages below half HP. |
| Collector (boss) | 56 | Boss | 12 dmg / 9×2 multi / **LEVY +5 Debt** / buff / debuff. Debuff-resistant. |

`LEVY` is currently the only enemy→economy interaction in the game, and it fires once per fight.

---

## Part 2 — Target Design: Debt as Leverage (MVP pivot)

**Status: design confirmed, implementation pending (see SDD change sequence below). Not yet in
code as of this writing.**

### Why this pivot

The current Debt system only subtracts (interest, usury, garnishment, levy) and has a free
escape valve, so the optimal play is always "get Debt back to zero." A resource whose only
optimum is a corner can't generate the "best possible move" tension good deckbuilders/roguelikes
run on. The fix isn't more generic synergy cards (that copies Slay the Spire's toolkit on 15
cards instead of 350) — it's making Debt itself do double duty as power **and** risk, so the
optimal amount of Debt to be carrying at any moment is an interior point you have to calculate
under pressure, not a corner.

### Confirmed rules

1. **Leverage — attacks scale with current Debt.**
   All `ATTACK` cards gain **+1 damage per 5 Debt** (integer division, floor). At Debt 30, a base
   Strike (6 dmg) hits for 12. Implemented alongside the existing `debt_scaling` tag read in
   `CardResolver` (already used by `compound_interest`).

2. **Execution — Debt above `BREAK_THRESHOLD` (30) is immediate defeat.**
   Reuses the existing calibrated constant instead of introducing a new number — `BREAK_THRESHOLD`
   already marks "danger zone" via the garnishment ramp and the forced-encounter trigger. This
   replaces `usuryDamage`'s HP-burn-that-never-kills with a real loss condition: the same number
   that gives you power is the number that kills you.

3. **Liquidation — two card templates that spend accumulated Debt:**
   - **Ejecución** (cost 2): deal damage equal to current Debt, then Debt → 0. The all-in payoff.
   - **Refinanciar** (cost 1): halve current Debt, gain Block equal to the amount cancelled. The
     defensive partial cash-out.

   Two distinct payoffs (offense vs. defense) so Liquidation isn't a single obvious button.

### Companion changes (from diagnostic, not yet numbered as rules — see SDD sequence)

- Remove the free discard-to-repay valve (`RepayMode.DISCARD`) and the in-combat Gold repay
  (`RepayMode.GOLD`) — Debt repayment moves to a between-fight node instead, giving Gold an
  actual job (§ MVP Scope below).
- Fix the interest formula's low-end regressiveness.
- Fix the break-encounter bug that currently summons the boss mid-run without advancing
  `encounterIndex`.

### Card migration under Leverage

- `eternal_debt` / `zombie_debt` become viable — a leverage-focused deck wants Debt to go up
  cheaply, so "Debt that keeps coming back" is now a payoff enabler, not dead weight.
- `bounced_check` needs a real comparison point once `foreclosure_express` is re-evaluated under
  the new economy — decide during the card-table pass in the `leverage-payoff-cards` change (C4
  below), not before.

### MVP Scope (target, once the pivot is implemented)

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

| # | Change | Delivers | Owner |
|---|---|---|---|
| C0 | `gdd-rewrite-leverage-design` | **This document.** | Developer (done — this rewrite) |
| C1 | `run-simulation-harness` | Headless simulator + invariant tests | Pi (in progress — exploration done) |
| C2 | `debt-as-leverage` | The pivot itself (Leverage/Execution/Liquidation rules above) | Spec: developer · Apply: Pi |
| C3 | `remove-free-debt-valve` | Removes the free discard/gold repay in combat | Pi (must land after C2) |
| C4 | `leverage-payoff-cards` | 6-10 new cards, retire/rework the 3 dead cards | Card table: developer · Apply: Pi |
| C5 | `run-length-and-encounter-slots` | 8-encounter run structure | Sequence: developer · Refactor: Pi |
| C6 | `enemy-roster-and-economy-intents` | 5 new enemies + economy-touching intents | Table: developer · Apply: Pi |
| C7 | `between-fight-node` | Gold's job outside combat | Mixed — supervise the render/input half (no headless coverage there) |
| C8 | `balance-pass-1` | Tune constants until success criteria are met | Pi (measurable target via simulator) |
| C9 | `two-enemy-encounters` *(optional)* | Multi-enemy variety, code-cheap | Pi — skip if C8 already meets criteria |

Open risk to watch: the leverage hypothesis itself could be wrong (an obvious optimal line would
make the risk axis decorative). Re-run the simulator's greedy policy after C2 lands — if it wins
above ~70%, tighten Execution before investing further down the sequence.

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-08-11 | Initial MVP GDD (superseded — described unimplemented Debt system) | — |
| 2026-08-25 | Full rewrite: documents actual current implementation + Debt-as-Leverage pivot design and SDD sequence | Claude Code + developer |

---

*Last updated: 2026-08-25 — Keep this file in sync with code. The next required update lands
with C2 (`debt-as-leverage`), once the pivot is actually implemented.*
