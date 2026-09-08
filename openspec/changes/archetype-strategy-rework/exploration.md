## Exploration: archetype-strategy-rework

### Current State (verified against code)
- `Archetype.kt` `playerArchetype()` returns LEVERAGE/LIQUIDITY/PRESSURE from tag counts, but the ONLY consumer is `RunManager.archetypeBiasedOffer()` (shop weight 3/2/1). **ZERO combat effect today.**
- `CardResolver.kt`: debt payoff already scoped to LEVERAGE tags only (`debt_scaling`, `debt_payoff`, `execution_damage`) — confirms the resolved fork. Drift: line 188 `state.debt / 10` hardcoded while flat all-attack leverage uses `LEVERAGE_DIVISOR=6` (`DebtConfig.kt:61`).
- `cards/all.json`: 27 cards, 4 starters excluded from rewards; **PRESSURE has NO dedicated cards** (only generic non-economy cards); LIQUIDITY/LEVERAGE partially covered. Pool thin for responses (FV E1 finding).
- `enemies/all.json`: 4 enemies, FLAT HP 22/36/52/52, damage 10-13/turn, strict superset staircase (FV proposal §1). No act scaling.
- `run/sequence.json`: 8 fixed slots. Free-pick counts sum to **8** (1,1,1,1,2,1,1,0), not 7 as briefed. `STARTER_DECK` = 10 cards (5 strike/3 defend/bash/survive) — **NOT 8**.
- `NodeConfig.kt`: heal 6/node, upgrades capped 2/run flat 15g, shop 3 biased, escalation 1.5^n.

### Affected Areas
- `core/combat/Archetype.kt` — extend to return magnitude + PRESSURE definition.
- `core/combat/resolution/CardResolver.kt` — apply archetype build bonuses; fix /10 drift.
- `core/combat/DebtConfig.kt` — Leverage band cap; align divisors.
- `assets/enemies/all.json` — HP/damage/intent scaling by act.
- `assets/cards/all.json` — add PRESSURE + support cards.
- `core/combat/RunManager.kt` + `NodeConfig.kt` — reward cadence (3-choose-1, upgrade every 4 wins).

### A. Gap Map
1. **Archetypes**: signal exists but no combat meaning; PRESSURE undefined in cards; convergence weak (shop bias only).
2. **Enemies**: flat, low damage, no act scaling → HP irrelevant.
3. **Economy**: 1-of-1 picks, shallow, cap 2 upgrades, no cadence tie to progress.

### B. Archetype Synergy Options
1. Set/threshold bonuses (deck-wide passive at N tags) — Low/Med.
2. Specific combo triggers — Med/High.
3. **Tag-count reward tiers** (every 2 archetype tags escalate that archetype's card scaling) — Low. **RECOMMENDED**: reuses `playerArchetype()` scoring, deterministic, no new data model, gives each archetype real combat payoff without global debt coupling.
4. Mastery passive from magnitude — Med.

### C. Archetype Definitions + Card Roles
- **LEVERAGE (debt-fueled, fork-scoped)**: payoff = `floor(debt/N)` via existing `CardResolver` math, but **capped in a band** (debt>40 → diminishing) to kill the EXECUTION-1 parking exploit (DebtConfig history). Needed roles: debt-accelerator (raise debt on purpose), cash-out (`debt_payoff` exists), wipe (`execution_damage` exists), draw (`overdraft` exists). No global coupling — debt only changes LEVERAGE cards.
- **LIQUIDITY (gold/draw/credit)**: roles exist (`debt_draw`, `refinance`, `gain_credit`, `gold_scaled_debt`). Add: gold→block "liquidity shield", gold-scaling attack.
- **PRESSURE (tempo/aggression)**: UNDEFINED. New roles: "paydown strike" (repays debt + bonus damage scaling with debt repaid), weak/vulnerable stackers, end-turn-low-debt escalator, AUDIT-style punish-buff card (FV verb).

### D. Enemy Rebalance (model)
Keep 4 enemies but scale by act (districts = acts): Act I 30–45 HP / 8–11 dmg; Act II 55–80 HP / 12–16 dmg; Act III/boss 90–140 HP / 16–22 dmg. Add intent variety beyond ATTACK superset (FV's FORECLOSE/AUDIT/HEDGE or equivalents) so each fight needs a plan, not just endurance. Baseline 22/36/52/52 → apply ~1.4–2.5× multipliers.

### E. Reward Economy (math)
- 8 combats/run (keep), framed as 3 acts.
- Per win: **3-choose-1** non-starter cards, biased to detected emerging archetype (extend `archetypeBiasedOffer` to the FREE pick, raise to 3). Max 8 card additions.
- Upgrade cadence: **1 upgrade every 4 wins** → raise `MAX_UPGRADES_PER_RUN` to ~4 (flat 15g).
- Start deck = 10 (corrected). To express ONE archetype: ~5 copies of its tag + 2 support ≈ **5 card picks + 2 upgrades** across the run. Validate all numbers with the headless harness (engram #1405).

### F. Do-NOT List
- Global debt scalar levers (FORECLOSE sweep, temporal-deadline, wipe_debt priority, card-pool accessibility) — FV graveyard, all failed.
- Unbounded Leverage payoff (parking-at-EXECUTION-1 exploit).
- Invisible debt/archetype HUD (see G).
- Random non-convergent rewards (must bias to emerging archetype).
- Re-litigating the resolved fork.

### G. Open Questions (resolve in proposal)
1. **HUD visibility** for debt/archetype state — user previously chose "no HUD"; flag for decision (blocks player understanding of strategy).
2. Player `maxHp` (unread) — needed to calibrate enemy damage so HP matters.
3. Exact Leverage band cap to prevent parking exploit.
4. Keep 8 combats or extend?
5. Confirm PRESSURE definition above.

### Recommendation
Adopt tag-count reward tiers (B.3) on top of `playerArchetype()`, add a PRESSURE card line, scale enemies per act, and switch rewards to 3-choose-1 biased picks with 1-upgrade/4-wins. Validate every number with the headless sim harness.

### Risks
- Leverage band-cap tuning is the crux; wrong value recreates the parking exploit or kills the archetype.
- PRESSURE design is net-new and untested; risk of another "flat" archetype.
- Enemy scaling must be sim-validated or the win-rate band breaks.

### Ready for Proposal
Yes — once the 2 flagged decisions (HUD visibility, player maxHp calibration) are answered. Orchestrator should ask the user these before sdd-propose.
