# Design: Archetype Strategy Rework

## Technical Approach

Extend the existing `playerArchetype()` scoring with tag-count tiers, introduce band-capped debt payoff for LEVERAGE, add PRESSURE cards, scale enemies per act, rework reward economy to biased 3-choose-1 picks, and surface debt/archetype/risk in the HUD. All core changes stay in pure Kotlin; all rendering stays in `gdx/`.

## Architecture Decisions

| Decision | Option A | Option B | Decision |
|----------|----------|----------|----------|
| Synergy tier storage | Inline in `Archetype.kt` | New `ArchetypeTiers.kt` file | Inline — only 3 thresholds + 3 bonus formulas, overkill to split |
| Band-cap constants | New `DebtConfig` constants | Hardcoded in CardResolver | `DebtConfig` — all debt math constants live here |
| Enemy scaling | Per-act entries in `all.json` | New `EnemyScaling.kt` config | `all.json` — data-driven, rollback is one file swap |
| HUD debt read source | `CombatState.debt` | `RunManager.debt` | `CombatState.debt` — immutable snapshot contract; renderer already receives it |
| Divisor unification | Replace `/10` with `DebtConfig.DEBT_STRENGTH_DIVISOR` | Leave as-is | Named constant — spec requires no magic numbers |

## A. Archetype Synergy — Tag-Count Tiers

**Location**: `Archetype.kt` — new function `archetypeTiers(deck, registry): Map<Archetype, Int>`.

**Algorithm**:
1. Count economy tags per archetype from the current deck (reuse `LEVERAGE_TAGS`, `LIQUIDITY_TAGS` from `Archetype.kt`).
2. For PRESSURE: count only explicitly PRESSURE-tagged cards (new `"pressure"` tag on PRESSURE cards). Plain non-economy cards signal PRESSURE for `playerArchetype()` tie-breaking but do NOT count toward tier thresholds.
3. `tier = floor(tagCount / 2)`, capped at 3. Thresholds: 2→T1, 4→T2, 6→T3.

**Tier bonuses**:
- **LEVERAGE Tn**: +n flat damage per attack (stacks with existing `floor(debt / LEVERAGE_DIVISOR)`).
- **LIQUIDITY Tn**: +n extra draw at combat start (first turn only), +10n% multiplier on gold gains.
- **PRESSURE Tn**: +n to weak/vulnerable applications on PRESSURE-tagged cards; T2+ grants +20% damage when enemy HP < 50%.

**Computation point**: Once per node entry (free pick changes deck) and at combat start. Stored in `CombatState` as `archetypeTiers: Map<Archetype, Int>` for HUD + resolver access.

## B. Leverage — Band-Capped Debt Payoff

**New constants in `DebtConfig.kt`**:
- `LEVERAGE_PAYOFF_BAND_CAP = 40` — debt level where diminishing returns begin.
- `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR = 5` — divisor M applied to excess above cap.

**Formula** in `CardResolver.kt` for `debt_payoff` role:
```
if debt <= 40: floor(debt / DEBT_PAYOFF_DIVISOR)
if debt > 40:  floor(40 / DEBT_PAYOFF_DIVISOR) + floor((debt - 40) / LEVERAGE_PAYOFF_DIMINISHING_DIVISOR)
```

**Why it kills parking**: At debt=49, uncapped payoff = floor(49/2) = 24. Capped = floor(40/2) + floor(9/5) = 20 + 1 = 21. The 12.5% loss above the band makes sitting at EXECUTION-1 suboptimal vs. playing the payoff in-band. Combined with interest ticks, the player must commit, not coast.

**Divisor drift fix**: Line 188 in `CardResolver.kt` (`state.debt / 10`) → `state.debt / DebtConfig.DEBT_STRENGTH_DIVISOR` (new constant, value=10, preserving current behavior).

## C. Liquidity — Reuse + Gold Roles

No new mechanics. Two new card entries in `all.json`:
1. **Liquidity Shield** (SKILL, `"liquidity"` tag): converts all Gold to Block at 1:2 rate. No debt side-effect.
2. **Gold Strike** (ATTACK, `"liquidity"` tag): `baseDamage + floor(gold / 5)`. Does not consume gold.

Existing LIQUIDITY cards (`debt_draw`, `refinance`, `add_debt`, `gain_credit`, `gold_scaled_debt`, `hand_exhaust`) remain unchanged. Tier bonuses apply via the synergy system (A).

## D. Pressure — New Card Line

**Four new PRESSURE-tagged cards in `all.json`**:

| Card ID | Type | Role | Tags | Effect |
|---------|------|------|------|--------|
| `paydown_strike` | ATTACK | Paydown | `"pressure"` | `baseDamage + debtRepaid` damage |
| `weak_pressure` | SKILL | Status stacker | `"pressure"` | `weakApply: 2`, `vulnerableApply: 1` |
| `low_debt_escalator` | SKILL/POWER | Low-debt bonus | `"pressure"`, `"low_debt_bonus"` | End-of-turn +1 damage if debt < 15 |
| `audit_punish` | POWER | AUDIT-punish | `"pressure"`, `"audit"` | When enemy BUFF/EMPOWER: apply vulnerable: 2 to enemy |

**Note on AUDIT**: The `"audit"` tag signals that this card is disabled when the enemy plays an AUDIT intent (from the FV-core-validation verb set). This is a tag-level disable, not a new mechanic in CardResolver — the FV verb system already handles tag disabling.

## E. Enemy Scaling — Per-Act Data

**Location**: `enemies/all.json` — add `actModifiers` array per enemy. Each entry has `{act: Int, hpMultiplier: Double, damageMultiplier: Double}`.

**Act derivation** from `sequence.json`:
- Act I (slots 0–2, district `slaughterhouse`)
- Act II (slots 3–5, district `casino`)
- Act III (slots 6–7, district `boardroom`)

**Scaling applied** in `EnemyInstance` constructor or `CombatEngine.startCombat`:
- `hp = floor(definition.hp * actModifier.hpMultiplier)`
- `intentPattern[].damage = floor(originalDamage * actModifier.damageMultiplier)`

| Enemy | Act | HP Mult | Dmg Mult | Result HP | Result Dmg |
|-------|-----|---------|----------|-----------|------------|
| thug | I | 1.36 | 1.0 | 30 | 10–10 |
| thug | II | 2.5 | 1.2 | 55 | 12–14 |
| loan_shark | II | 1.8 | 1.15 | 65 | 13–15 |
| loan_shark | III | 2.5 | 1.35 | 90 | 16–18 |
| collector | III | 2.3 | 1.5 | 120 | 18–22 |
| godfather | I | 1.8 | 1.0 | 40 | 9–13 |
| godfather | II | 3.4 | 1.2 | 75 | 14–16 |
| godfather | III | 6.4 | 1.5 | 140 | 20–22 |

Both HP and damage scale together per the HP-Matters invariant.

## F. Reward Economy — Biased Free Picks + Upgrade Cadence

**`RunManager.enterNode()` changes**:
1. **Free pick count**: Use `currentSlot.rewards.cardChoices` from `sequence.json` but always present as 3-choose-1 biased. Update `sequence.json` cardChoices to 3 for all non-boss slots (boss stays 0 or 1).
2. **`archetypeBiasedOffer()` reuse**: Extend the existing private method (weights 3/1/2) to generate free-pick offers instead of only shop offers.
3. **Upgrade cadence**: Replace `MAX_UPGRADES_PER_RUN = 2` with `MAX_UPGRADES_PER_RUN = 4`. Gate upgrades by `wins % 4 == 0` (track wins as a new field in `RunManager`). Only the node after win 4 and win 8 offers upgrades.

**Convergence**: 8 slots → ~7 free picks (boss gives 0) + up to 4 upgrades. With 3-choose-1 biased picks, the expected value is ~5 cards of the dominant archetype per run, sufficient to reach tier 2.

## G. Debt HUD — Read-Only Render from CombatState

**Current state**: `CombatRenderer.drawPlayer()` already shows debt with 3-color coding (ink100/amber/red). It reads `state.debt` from `CombatState` — the immutable snapshot.

**Additions to `drawPlayer()`**:
1. **Debt band bar**: A thin bar below the current debt text, divided into 4 zones:
   - Green: 0–21 (safe)
   - Amber: 22–29 (danger / bleed floor)
   - Orange: 30–49 (execution proximity)
   - Red: 50+ (execution line)
2. **Active archetype label**: Small text showing `playerArchetype(deck, registry).name` — read from `RunManager` (which has the deck) or computed from `CombatState` + a deck reference passed to render.
3. **Risk counter**: Text like `"15 to execution"` (= `EXECUTION_THRESHOLD - state.debt`), shown when debt > 22.

**Contract**: HUD reads only — no state mutation. `CombatState` already carries `debt`, `gold`, and `player`. The archetype computation needs the deck (list of card IDs), which `RunManager` owns. Pass the deck as an additional render argument, or compute archetype in `RunManager` and expose it as a property.

**Data flow**:
```
CombatEngine.getState() → CombatState(debt, player) ──→ GameScreen.render()
RunManager (deck, phase) ──────────────────────────────┘
                                                        ↓
                                              CombatRenderer.render(state, run, batch)
                                                        ↓
                                           read state.debt, run.deck → archetype
                                           draw debt band + archetype label + risk counter
```

## Tuning Constants

All constants below MUST be validated in the headless sim harness (Engram #1405).

| Constant | Location | Starting Value | Win-Rate Target | Notes |
|----------|----------|----------------|-----------------|-------|
| `LEVERAGE_PAYOFF_BAND_CAP` | `DebtConfig` | 40 | — | Debt level for diminishing returns |
| `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR` | `DebtConfig` | 5 | — | M divisor above band |
| `DEBT_STRENGTH_DIVISOR` | `DebtConfig` | 10 | — | Named constant replacing `/10` |
| Synergy tier thresholds | `Archetype.kt` | 2, 4, 6 | — | Every 2 tags = +1 tier |
| LIQUIDITY gold multiplier per tier | `Archetype.kt` | 10% | — | Additive per tier |
| PRESSURE low-debt threshold | `Archetype.kt` | debt < 15 | — | End-of-turn escalator trigger |
| Enemy HP/damage multipliers | `all.json` | see table E | [0.35, 0.55] | Act I→III scaling |
| `MAX_UPGRADES_PER_RUN` | `RunManager` | 4 | — | Up from 2 |
| Upgrade cadence | `RunManager` | every 4 wins | — | New field: `wins` counter |
| Free-pick offer size | `sequence.json` | 3 | — | Biased 3-choose-1 |
| Player maxHp | `PlayerState` | 50 | — | Unchanged |
| Starter deck size | `CombatEngine` | 10 | — | Unchanged |

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| **Unit** | `archetypeTiers()` returns correct tier for given deck | Pure function test with known tag counts |
| **Unit** | Band-capped payoff formula: below/above cap cases | Verify floor(30/2)=15, floor(40/2)+floor(10/5)=22 |
| **Unit** | Divisor unification: no magic `/10` in CardResolver | Code assertion or test that uses named constant |
| **Unit** | PRESSURE tag vs non-economy distinction | Verify non-economy cards do NOT count toward tier |
| **Unit** | Upgrade cadence: only at wins 4, 8; cap at 4 | RunManager state tests |
| **Integration** | Biased 3-choose-1: ≥0.6 probability of dominant archetype | Statistical test over 200+ samples |
| **Harness** | Win rate in [0.35, 0.55] with full scaling | Headless sim (Engram #1405), 200 seeds |
| **Harness** | EXECUTION-1 parking < 70% win rate | Scripted greedy policy sim |
| **Harness** | PRESSURE-only deck within 10pp of LEVERAGE-only | Two policy sims compared |
| **Harness** | Avg hits-to-kill ≥ 4.0 for 6-dmg attack | Sim metric over scaled enemies |
| **Manual** | HUD renders correct debt band, archetype, risk counter | Visual review on device |
| **Manual** | HUD removal does not change combat outcome | Run with HUD disabled, compare seed results |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. This change is pure game logic + data + render-only HUD.

## Migration / Rollout

No migration required. All changes are additive or replace constants:
- New cards in `all.json` are additive.
- Enemy scaling in `all.json` can be reverted by restoring the original file.
- `MAX_UPGRADES_PER_RUN` and band-cap constants are single-value changes.
- HUD is render-only; safe to strip without touching combat logic.

## Open Questions

- [ ] Confirm `sequence.json` cardChoices values: should all non-boss slots go to 3, or keep some at 1–2 for pacing?
- [ ] AUDIT intent handling: should the FV verb system's tag-disabling be extended, or is a simple CardResolver check sufficient?
- [ ] Should `archetypeTiers` be computed every turn (dynamic) or only at node entry (static per combat)? Static is simpler but less responsive.
