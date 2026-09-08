# Proposal: Archetype Strategy Rework

## Intent

The game plays flat: only block and damage, no strategic branching, no difficulty curve, and the Debt differentiator is not felt because it powers only the LEVERAGE archetype while LIQUIDITY and PRESSURE have no combat payoff. Players pick cards but never *build* — every run feels like a longer version of the same fight. This change makes archetypes real: each of the three has a legible card identity, combat-scoring tiers, and convergent rewards that pull the deck toward one axis. Debt becomes the fuel for LEVERAGE specifically (not a global scalar), enemies gain pressure by act, and the HUD finally shows the player what they are building.

## Scope

### In Scope
- **Archetype synergy system**: tag-count reward tiers on top of existing `playerArchetype()` — every N tags of one archetype escalates that archetype's card effects.
- **Three archetype card sets**: new cards for LEVERAGE (debt accelerators, cash-outs), LIQUIDITY (gold→block shield, gold-scaling attack), PRESSURE (paydown strikes, weak/vulnerable stackers, end-turn-low-debt escalator).
- **Debt-for-Leverage mechanic (band-capped)**: payoff scales with debt but diminishes above a band cap (~40), killing the EXECUTION-1 parking exploit. Divisor drift (`/10` vs `LEVERAGE_DIVISOR=6`) unified.
- **Enemy HP/damage scaling by act**: 4 enemies scaled ~1.4–2.5× across 3 acts/districts on the 22/36/52/52 baseline. Act I: 30–45 HP / 8–11 dmg; Act II: 55–80 HP / 12–16 dmg; Act III/boss: 90–140 HP / 16–22 dmg.
- **Reward economy rework**: 3-choose-1 non-basic cards biased to emerging archetype (replaces current 1-of-1 free picks); upgrade node every 4 wins; `MAX_UPGRADES_PER_RUN` raised from 2 to ~4; ~5 picks + 2 upgrades needed to express an archetype across 8 combats.
- **HUD indicator**: on-screen display of current debt level, active archetype, and risk counter (bleed/execution proximity).

### Out of Scope
- **NO global debt scalar levers**: the 4+ failed FV levers (temporal-deadline, FORECLOSE threshold sweep, card-pool accessibility, `wipe_debt` priority, arrears-lock decoupling) remain out. Debt is LEVERAGE fuel only.
- **NO invisible debt**: HUD delivers visibility.
- **NO random non-convergent rewards**: all picks biased to the emerging archetype.
- **NO new data model for archetypes**: reuse existing tags and `playerArchetype()` scoring.
- **NO change to VISION's load-bearing divisors** (D9) without explicit sign-off.
- **NO change to run length**: 8 combats, fixed.
- **NO change to player maxHp**: stays at 50.
- **NO change to starter deck size**: stays at 10 (5 strike / 3 defend / bash / survive).

## Capabilities

### New Capabilities
- `archetype-synergy`: tag-count tier system that escalates archetype card effects at scoring thresholds (e.g., every 2 LEVERAGE tags → +X scaling on LEVERAGE cards). Reuses `playerArchetype()` signal.
- `pressure-archetype`: PRESSURE card line with defined roles (paydown strikes, status stackers, low-debt escalator) — currently has zero dedicated cards.
- `reward-economy`: 3-choose-1 biased picks replacing 1-of-1 free picks; upgrade cadence every 4 wins; `MAX_UPGRADES_PER_RUN` raised.
- `enemy-scaling`: per-act HP/damage/intent scaling on existing enemies (~1.4–2.5× multipliers on 22/36/52/52 baseline).
- `hud-indicators`: on-screen debt level, active archetype, and bleed/execution risk counter.

### Modified Capabilities
- `leverage-payoff`: debt payoff band-capped (diminishing above ~40); divisor drift unified (`state.debt / 10` → consistent with `LEVERAGE_DIVISOR`).
- `between-fight-node`: free pick changed from 1-of-1 to 3-choose-1 biased; upgrade cadence changed from flat 2/run to 1 per 4 wins (cap ~4).

## Approach

### A. Synergy Tiers
On top of `playerArchetype()` (unchanged scoring), compute tag-count tiers per archetype. At each threshold (e.g., 2, 4, 6 tags of one archetype), apply a passive combat bonus specific to that archetype:
- **LEverage**: additional flat damage bonus per attack (stacking with existing `floor(debt / LEVERAGE_DIVISOR)`), or reduced debt payoff divisors.
- **LIQUIDITY**: gold multipliers on gains, extra draw at node entry, or reduced node costs.
- **PRESSURE**: bonus weak/vulnerable application, or damage multiplier when enemy is below X% HP.

No new data model — tiers computed from tag counts the registry already holds.

### B. Archetype Card Roles + Band-Capped Leverage
- **LEVERAGE**: new debt-accelerator cards (raise debt cheaply, enabling the band), enhanced cash-outs. Payoff band: `floor(debt / N)` applies normally up to debt ~40, then diminishing returns (e.g., `floor((debt - 40) / M) + base`) so parking at EXECUTION-1 yields less incremental power.
- **LIQUIDITY**: gold→block "liquidity shield" card, gold-scaling attack. Existing `debt_draw`, `refinance`, `gain_credit` cards reinforced.
- **PRESSURE**: "paydown strike" (repays debt + bonus damage scaling with debt repaid), weak/vulnerable stackers, end-turn-low-debt escalator (bonus if debt < threshold at turn end), AUDIT-style punish card.

### C. Enemy Scaling
Four enemies (keep roster, scale numbers) across 3 acts/districts:

| Enemy | Act I | Act II | Act III (boss) |
|-------|-------|--------|----------------|
| thug | 30 HP / 8–11 dmg | 55 HP / 12–14 dmg | — |
| loan_shark | — | 65 HP / 13–15 dmg | 90 HP / 16–18 dmg + LEVY |
| collector | — | — | 120 HP / 18–22 dmg + LEVY + MULTI |
| new_elite | 40 HP / 9–11 dmg | 75 HP / 14–16 dmg | 140 HP / 20–22 dmg |

Multipliers on baseline 22/36/52/52 range from ~1.4× to ~2.5×. Intent variety added beyond ATTACK superset. All numbers validated via headless sim harness (Engram #1405).

### D. Economy Numbers
- **8 combats/run** (unchanged), 7 nodes.
- **Per win**: 3-choose-1 biased to detected archetype (extend `archetypeBiasedOffer` to free picks).
- **Upgrade**: 1 upgrade every 4 wins, cap raised from 2 to ~4, flat 15g.
- **Archetype expression**: ~5 card picks + 2 upgrades across the run to commit to one axis.
- **Starter deck**: 10 cards (corrected from brief's 8).

### E. HUD Elements
Three on-screen indicators during combat:
1. **Debt level** with visual band markers (safe / danger / execution zone).
2. **Active archetype** icon (LEVERAGE / LIQUIDITY / PRESSURE) derived from `playerArchetype()`.
3. **Risk counter**: proximity to bleed floor (`DEBT_BLEED_FLOOR = 22`) and execution line (`EXECUTION_THRESHOLD = 50`).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `core/combat/Archetype.kt` | Modified | Add tier computation on top of `playerArchetype()` scoring |
| `core/combat/DebtConfig.kt` | Modified | Add band-cap constants for Leverage payoff diminishing returns |
| `core/combat/resolution/CardResolver.kt` | Modified | Apply tier bonuses; unify divisor drift (`/10` → consistent) |
| `core/combat/RunManager.kt` | Modified | 3-choose-1 biased free picks; upgrade cadence (1/4 wins, cap ~4) |
| `core/combat/NodeConfig.kt` | Modified | `MAX_UPGRADES_PER_RUN` raised |
| `assets/cards/all.json` | Modified | Add PRESSURE cards, new LEVERAGE/LIQUIDITY support cards |
| `assets/enemies/all.json` | Modified | HP/damage/intent scaling per act |
| UI render layer | New | HUD indicators: debt bar, archetype icon, risk counter |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Leverage band-cap tuning wrong → recreates parking exploit or kills archetype | High | Validate every band value against headless sim harness (Engram #1405); iterate in C8 balance pass |
| PRESSURE ends up "flat" again (no dedicated identity) | Medium | Define 3+ distinct card roles upfront; sim-verify that a PRESSURE-only deck wins at a different debt profile than LEVERAGE |
| Enemy scaling breaks win-rate band `[0.35, 0.55]` | Medium | Apply multipliers incrementally (1.4× → 2.5×); sim-validate each step; adjust HP before damage if win rate drops below band |

## Rollback Plan

1. **Revert the change branch** — each subsystem (synergy tiers, card additions, enemy scaling, economy, HUD) is data-driven or isolated enough to revert independently.
2. **Enemy scaling**: restore `enemies/all.json` to the 22/36/52/52 baseline — one file swap, zero code.
3. **Card pool**: new cards are additive in `all.json`; removing them restores the pre-change pool. No migration needed.
4. **Economy cadence**: `MAX_UPGRADES_PER_RUN` and free-pick count are constants in `RunManager`/`NodeConfig`; revert to current values (2 upgrades, 1-of-1 picks).
5. **Synergy tiers**: if tiers are implemented as a separate scoring layer, guard behind a feature flag or simply return the existing `playerArchetype()` result without tier lookup.
6. **HUD**: render-only change; safe to strip without touching combat logic.

## Dependencies

- Headless simulation harness (Engram #1405) must be operational for balance validation before merge.
- FV verb validation (PR #22) is WIP and not a hard blocker — enemy scaling and archetype tiers are orthogonal to intent verbs.

## Success Criteria

- [ ] Sim harness win rate for greedy policy stays within `[0.35, 0.55]` after all scaling applied.
- [ ] At least 2 of 3 archetypes appear as dominant in winning decks across 200 seeds (archetype concentration > 40% of wins per archetype).
- [ ] Deaths are NOT 100% at final boss — final-boss loss rate < 80% (currently implied by flat scaling).
- [ ] Leverage band-cap verified: no scripted policy achieves > 70% win rate by parking at EXECUTION-1.
- [ ] PRESSURE-only simulated deck achieves win rate within 10pp of LEVERAGE-only deck (validates PRESSURE is not "flat").
- [ ] Playtest: ≥ 60% of external testers can name the archetype they were building by run 2.
