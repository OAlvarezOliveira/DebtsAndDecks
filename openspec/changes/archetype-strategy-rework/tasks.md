# Tasks: Archetype Strategy Rework

Path key: Kotlin under `app/src/main/java/com/debtsdecks/`; assets under `app/src/main/assets/`.
Locked decisions: Debt=LEVERAGE-only band-capped (cap40, M5); HUD from CombatState; HP50/8combats/3-choose-1/upgrade every 4 wins; synergy=tag-count tiers on `playerArchetype()`; starter=10. Resolved: Q1 all non-boss→3 picks; Q2 AUDIT reuses FV verb mechanism; Q3 tiers static per deck/node.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1200–1600 |
| review_budget_lines (guard) | 800 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | WU1→WU8 stacked per concern |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units
| Unit | Goal | PR | Focused test | Runtime harness | Rollback |
|------|------|----|--------------|-----------------|----------|
| WU1 | Config + tier compute | 1 | `ArchetypeTiersTest` | N/A | `Archetype.kt` revert |
| WU2 | Leverage band-cap + divisor | 2 | `BandCapTest` | harness win-rate/parking | `CardResolver.kt` revert |
| WU3 | Pressure cards + synergy | 3 | `PressureTest` | harness PRESSURE parity | `cards/all.json` + `CardResolver` revert |
| WU4 | Enemy scaling + intents | 4 | `EnemyScalingTest` | harness hits-to-kill | `enemies/all.json` swap |
| WU5 | Reward economy | 5 | `RewardEconomyTest` | harness convergence | `RunManager`/`sequence.json` revert |
| WU6 | HUD | 6 | manual review | manual HUD-off parity | `CombatRenderer` revert |
| WU7 | Tuning + sim validation | 7 | harness 200 seeds | headless harness #1405 | constant edits |
| WU8 | Unit/integration/manual tests | 8 | `testDebugUnitTest` | N/A | N/A |

## WU1: Config + Archetype Tiers (foundation)
- [x] **T1.1 Add `archetypeTiers()`.** `core/combat/Archetype.kt`. Dep: none. Add `PRESSURE_TAGS` (or count `"pressure"` tag) + `archetypeTiers(deck, registry): Map<Archetype,Int>` = `floor(tagCount/2)` cap 3; thresholds 2/4/6; PRESSURE counts only `"pressure"`-tagged. Accept: synergy "Tier at thresholds", "No tier sparse", trap "plain non-economy ≠ PRESSURE tier".
- [x] **T1.2 Add constants.** `core/combat/DebtConfig.kt`. Dep: none. `LEVERAGE_PAYOFF_BAND_CAP=40`, `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5`, `DEBT_STRENGTH_DIVISOR=10`. Accept: leverage "Named constants only".
- [x] **T1.3 Carry tiers in state.** `core/model/CombatState.kt`. Dep: T1.1. Add `val archetypeTiers: Map<Archetype,Int> = emptyMap()` (import `Archetype`). Accept: HUD/resolver read-only access.
- [x] **T1.4 Populate tiers.** `core/combat/CombatEngine.kt`. Dep: T1.1,T1.3. Compute `archetypeTiers(starterDeck, cardRegistry)` in `startCombat`; thread into `getState()`. Accept: resolver reads `state.archetypeTiers`.

## WU2: Leverage Band-Cap + Divisor
- [x] **T2.1 Band-cap payoff.** `core/combat/resolution/CardResolver.kt` (debt_payoff branch ~L114). Dep: T1.2. `if debt<=40 floor(debt/N) else floor(40/N)+floor((debt-40)/M)`. Accept: leverage "Linear below cap" (15), "Diminishing above cap" (22), trap "EXECUTION-1 parking".
- [x] **T2.2 Divisor unification.** `CardResolver.kt` L188 `state.debt/10` → `state.debt/DebtConfig.DEBT_STRENGTH_DIVISOR`. Dep: T1.2. Accept: leverage "Named constants only".
- [x] **T2.3 Leverage tier damage.** `CardResolver.kt` ATTACK branch. Dep: T1.4. `+tier` flat damage per attack (stacks `floor(debt/LEVERAGE_DIVISOR)`). Accept: synergy "Tier stacks with base leverage" (debt24,tier2→6).

## WU3: Pressure Cards + Synergy
- [x] **T3.1 PRESSURE status tier.** `CardResolver.kt` weak/vuln apply. Dep: T1.4. PRESSURE-tagged cards get `+tier` weak/vuln. Accept: synergy "Status escalation" (weak1→2 @tier1).
- [x] **T3.2 PRESSURE low-HP dmg.** `CardResolver.kt` ATTACK branch. Dep: T1.4. T2+ → +20% dmg when enemy HP<50%. Accept: synergy PRESSURE tier bonus.
- [x] **T3.3 `paydown_strike`.** `assets/cards/all.json`. Dep: T3.1. ATTACK,"pressure","paydown"; dmg=`baseDamage+debtRepaid`, repays debt. Accept: pressure "Paydown scales" (4+3=7), "Zero debt fallback".
- [x] **T3.4 `weak_pressure`.** `cards/all.json`. Dep: T3.1. SKILL,"pressure"; weakApply2,vulnerableApply1. Accept: pressure "Weak/Vulnerable Stackers".
- [x] **T3.5 `low_debt_escalator`.** `cards/all.json`. Dep: T3.6. POWER,"pressure","low_debt_bonus". Accept: pressure "Trigger at low debt"/"No trigger high debt".
- [x] **T3.6 End-of-turn POWER hook.** `CombatEngine.kt`. Dep: T1.4. Apply `low_debt_bonus` when `debt<15` at turn end. Accept: pressure escalator scenarios.
- [ ] **T3.7 `audit_punish`.** `cards/all.json` + resolver tag-disable. Dep: FV verb mechanism (PR #22 WIP — RISK). POWER,"pressure","audit"; enemy BUFF/EMPOWER→vulnerable2. Accept: pressure "AUDIT-Punish". DEFERRED: depends on PR #22 AUDIT verb (unmerged WIP).

## WU4: Enemy Scaling + Intents
- [x] **T4.1 ActModifier model.** `core/enemies/EnemyDefinition.kt`. Dep: none. Add `ActModifier(act,hpMultiplier,damageMultiplier)` + `actModifiers` field. Accept: enemy-scaling "Data-Driven Scaling".
- [x] **T4.2 Apply scaling.** `EnemyInstance.kt` + `CombatEngine.startCombat` (add `act` param). Dep: T4.1. `hp=round(hp*m)`, `intentDamage=round(d*m)`. Accept: enemy-scaling "Act I thug tanks", "HP Matters" (hits≥4).
- [x] **T4.3 Derive act.** `core/combat/RunManager.kt` `advanceToNextCombat`. Dep: T4.2. Slots 0–2→I,3–5→II,6–7→III; pass act. Accept: enemy-scaling per-act table.
- [x] **T4.4 Add actModifiers.** `assets/enemies/all.json`. Dep: T4.1. Per design table E (thug 30/55, loan_shark 65/90, collector 120; godfather omitted — not in catalog/sequence). Accept: enemy-scaling table.
- [x] **T4.5 New intents.** `EnemyDefinition.kt` `IntentType` FORECLOSE/HEDGE + icon + `EnemyAI` + engine hook (AUDIT deferred — FV WIP, per RISK note). Dep: none. Accept: enemy-scaling "Intent Variety", "FORECLOSE forces decision".

## WU5: Reward Economy
- [x] **T5.1 Raise cap.** `RunManager.kt` companion `MAX_UPGRADES_PER_RUN` 2→4. Dep: none. Accept: reward-economy "Cap enforcement".
- [x] **T5.2 Upgrade cadence.** `RunManager.kt`. Dep: T5.1. Add `wins` counter; offer upgrades only when `wins%4==0`. Accept: "Upgrade at win 4", trap "upgrade every node".
- [x] **T5.3 Biased free pick.** `RunManager.kt` `enterNode`. Dep: none. Replace random `take(cardChoices)` with biased 3-choose-1; exclude starters. Accept: "Biased offer" (≥0.6), "No starters".
- [x] **T5.4 Sequence edits.** `assets/run/sequence.json`. Dep: none. Non-boss slots `cardChoices=3`, boss 0/1. Accept: "Run Length Unchanged" (8 slots).
- [x] **T5.5 Reuse offer fn.** `RunManager.kt` `archetypeBiasedOffer`. Dep: T5.3. Serve `rewardChoices` from same biased fn. Accept: reward-economy "Biased offer".

## WU6: HUD (read-only)
- [x] **T6.1 Expose archetype.** `RunManager.kt`. Dep: none. `val dominantArchetype: Archetype get()=playerArchetype(deck,cardRegistry)`. Accept: debt-hud "Active Archetype Display".
- [x] **T6.2 Debt band bar.** `gdx/render/CombatRenderer.kt` `drawPlayer()`. Dep: none. 4 zones via `DEBT_BLEED_FLOOR`/`BREAK_THRESHOLD`/`EXECUTION_THRESHOLD`. Accept: "Band reflects current debt" (35→danger).
- [x] **T6.3 Archetype + risk counter.** `CombatRenderer.drawPlayer()`. Dep: T6.1. Label `run.dominantArchetype`; risk `EXECUTION_THRESHOLD-state.debt` when debt>22. Accept: "Risk at moderate debt" (15 to execution).
- [x] **T6.4 Read-only proof.** Review. Dep: T6.2,T6.3. No mutation paths. Accept: "HUD removal is safe".

## WU7: Tuning + Sim Validation (harness #1405)
- **T7.1 Build harness.** `app/src/test/java/.../simulation/`. Dep: WU1–6. No-op Localizer; read JSON via File; scripted policy. Accept: runs + terminates.
- **T7.2 Win-rate band.** harness. Dep: T7.1. Assert [0.35,0.55] over 200 seeds. Accept: proposal win-rate band.
- **T7.3 Parking exploit.** harness. Dep: T2.1,T7.1. EXECUTION-1 park <70% win. Accept: leverage parking trap.
- **T7.4 PRESSURE parity.** harness. Dep: T3,T7.1. PRESSURE-only within 10pp of LEVERAGE-only. Accept: proposal PRESSURE parity.
- **T7.5 Hits-to-kill.** harness. Dep: T4,T7.1. avg ≥4.0 for 6-dmg. Accept: enemy-scaling "HP Matters".
- **T7.6 Iterate constants.** Dep: T7.2–T7.5. Tune band/divisor/enemy mults/cadence until all hold. Accept: proposal success criteria.

## WU8: Tests (5 unit + 1 integration + 2 manual)
- **T8.1 Unit tiers.** Dep: T1.1. Thresholds + PRESSURE-tag distinction. Accept: synergy scenarios + traps.
- **T8.2 Unit band-cap.** Dep: T2.1. below/above cap cases. Accept: leverage scenarios.
- **T8.3 Unit no-magic.** Dep: T2.2. Assert constant used, not `/10`. Accept: leverage "Named constants only".
- **T8.4 Unit cadence.** Dep: T5.2. wins 4/8 only, cap 4. Accept: reward-economy scenarios.
- **T8.5 Integration biased.** Dep: T5.3. ≥0.6 dominant over 200 samples. Accept: reward-economy "Biased offer".
- **T8.6 Manual HUD.** Dep: T6. Device: band/archetype/risk correct. Accept: debt-hud scenarios.
- **T8.7 Manual HUD-off.** Dep: T6. Same seed, HUD off = identical outcome. Accept: debt-hud "HUD removal is safe".
