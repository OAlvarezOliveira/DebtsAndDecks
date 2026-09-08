# Apply Progress: Archetype Strategy Rework

Artifact store: `both` (OpenSpec file + Engram topic `sdd/archetype-strategy-rework/apply-progress`).
Mode: Standard (strict_tdd not active in init).
Last updated: WU7 (Tuning + sim validation).

## Cumulative Task State (all WUs)

| Task | WU | Status |
|------|----|--------|
| T1.1 `archetypeTiers()` | WU1 | [x] complete |
| T1.2 DebtConfig constants | WU1 | [x] complete |
| T1.3 CombatState carries tiers | WU1 | [x] complete |
| T1.4 CombatEngine populates tiers | WU1 | [x] complete |
| T2.2 Divisor unification (`/10` → `DEBT_STRENGTH_DIVISOR`) | WU2 (pulled into WU1 scope by orchestrator) | [x] complete |
| T2.1 Band-cap payoff formula | WU2 | [x] complete |
| T2.3 Leverage tier damage | WU2 | [x] complete |
| T3.1 PRESSURE status tier (weak/vuln escalation) | WU3 | [x] complete |
| T3.2 PRESSURE low-HP dmg (+20% at T2+) | WU3 | [x] complete |
| T3.3 `paydown_strike` card + paydown damage bonus | WU3 | [x] complete |
| T3.4 `weak_pressure` card | WU3 | [x] complete |
| T3.5 `low_debt_escalator` card | WU3 | [x] complete |
| T3.6 End-of-turn POWER hook (`low_debt_bonus`) | WU3 | [x] complete |
| T3.7 `audit_punish` card + resolver tag-disable | WU3 | [ ] **DEFERRED** — depends on PR #22 AUDIT verb (unmerged WIP). Left unchecked by design. |
| T4.1–T4.5 Enemy scaling + intents | WU4 | [x] complete |
| T5.1 Raise cap (`MAX_UPGRADES_PER_RUN` 2→4) | WU5 | [x] complete |
| T5.2 Upgrade cadence (wins counter; `wins%4==0`; T5.2 caveat) | WU5 | [x] complete |
| T5.3 Biased free pick (reuse biased sampler) | WU5 | [x] complete |
| T5.4 Sequence edits (non-boss=3, boss=1/0) | WU5 | [x] complete |
| T5.5 Reuse offer fn (`archetypeBiasedOffer` serves `rewardChoices`) | WU5 | [x] complete |
| T6.1 Expose `dominantArchetype` | WU6 | [x] complete |
| T6.2 Debt band bar (zones + ticks) | WU6 | [x] complete |
| T6.3 Archetype label + risk counter | WU6 | [x] complete |
| T6.4 Read-only proof (review) | WU6 | [x] complete (no mutation paths) |
| T7.1–T7.6 Tuning + sim validation | WU7 | [x] complete |
| T8.1–T8.7 Tests | WU8 | [ ] pending |

> Note: the orchestrator's resolved WU1 scope explicitly included the `CardResolver` `/10`
> divisor unification (tasks.md T2.2), so it is marked complete here even though it sits under WU2
> in the task breakdown. WU2 implementer should skip T2.2.

> WU3 DEFERRED TASK: T3.7 `audit_punish` is intentionally NOT implemented in this slice. It depends
> on the FV-core-validation `audit` verb mechanism (PR #22, unmerged WIP). The card is left absent
> from `cards/all.json` and no resolver tag-disable hook was added. Per the slice instructions, the
> task checkbox stays `[ ]` with a `DEFERRED` note. Consequently the pressure-archetype spec's
> "≥4 distinct PRESSURE-tagged cards" expectation is met by 3 cards (paydown_strike, weak_pressure,
> low_debt_escalator) until audit_punish lands in a later PR; the acceptance "AUDIT-Punish" scenario
> is out of scope for this slice.

---

## WU5 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.debtsdecks.core.combat.RewardEconomyTest"` |
| Focused test result | `RewardEconomyTest` (5 tests): biased-offer skew ≥0.6, no-starter-in-offer, upgrade-only-at-win-4 + never-after-final-boss, cadence-every-4-wins + cap-4 walk, convergence skew — all PASS. |
| Full suite result | Full `:app:testDebugUnitTest`: **271 completed, 1 failed, 2 skipped**. The 1 failure is `RunSimulationHarnessTest.leverage policy comparison sweep` → H1.1 (greedy win-rate in [0.35,0.55]). This is the **pre-existing WU7 balance-target failure** already recorded in the WU4 apply-progress (win-rate recovery deferred to WU7 T7.x); it is NOT a WU5 regression. Two `NodePolicyTest` failures caused by WU5 (cap 2→4 + cadence gate) were fixed by updating those sim-policy tests to the new economy. |
| Runtime harness command/scenario | N/A — WU5 is pure reward-economy flow + data (sequence.json) + a single `RunManager` constant; no new runtime boundary. The biased-offer + cadence behavior is exercised through `RunManager` in the unit tests above, including a full 8-slot win-to-VICTORY walk and a 21-slot cadence+cap walk. |
| Rollback boundary | Revert `RunManager.kt` (3 edits: `MAX_UPGRADES_PER_RUN` 4, `wins` counter + `wins++` in `refresh()` + reset in `beginRun()`, `enterNode` biased `rewardChoices` + cadence-gated `nodeUpgradeChoices`, `archetypeBiasedOffer(offerSize)` internal + `upgradeCard` `wins%4` guard), `assets/run/sequence.json` (non-boss `cardChoices=3`, boss 1/0), the 3 changed tests in `RunManagerTest.kt`, the 2 changed tests in `NodePolicyTest.kt`, and delete `RewardEconomyTest.kt`. No other WU1–WU4 code is touched. |

### WU5 Implementation Notes

- **T5.1 `MAX_UPGRADES_PER_RUN` 2 → 4** in `RunManager` companion. Pure constant swap.
- **T5.2 upgrade cadence + T5.2 caveat**: Added `private var wins = 0`, incremented in `refresh()`'s win branch (once per defeated enemy), reset in `beginRun()`. `enterNode` populates `nodeUpgradeChoices` ONLY when `wins % 4 == 0`. `upgradeCard` additionally rejects with `if (wins % 4 != 0) return false` so the "every 4 wins only" rule cannot be bypassed by a direct call (the reward-economy false-positive trap). In an 8-slot run ending in a boss, the only cadence node is the one after win 4; the final boss (win 8) goes straight to `VICTORY` and never opens a node, so no upgrade can appear after the boss. (See Deviations #1.)
- **T5.3 / T5.5 biased free pick**: `rewardChoices` now comes from `archetypeBiasedOffer(freePickCount)` (the same archetype-weighted sampler the shop already used; weights 3/2/1). `archetypeBiasedOffer` was generalized to take an `offerSize` (default 3 for the shop) and made `internal` for testability. Excludes `starter`-tagged cards (unchanged `REWARD_EXCLUDED_TAGS`).
- **T5.4 sequence.json**: non-boss slots (0,1,3,4,6) → `cardChoices=3`; boss slots 2,5 → `cardChoices=1`; final boss slot 7 → `cardChoices=0`. 8 slots preserved (Run Length Unchanged). `RunSequenceTest` updated: pick-sum assertion 8 → 17, and slot-6 expectation 1 → 3 (slot 6 is a STREET/non-boss node under the new economy).

### WU5 Deviations

1. **Cap (4) is higher than the cadence delivers in an 8-slot run.** With `wins % 4 == 0` gating, an 8-slot run has exactly ONE upgrade node (after win 4); the win-8 node is the final boss → `VICTORY`, no node. So a normal run yields at most 1 upgrade despite the cap of 4. The cap of 4 is kept per T5.1/T5.2 (design table) as the hard ceiling for longer runs; the focused cap test verifies it by walking a 21-slot sequence (upgrades at wins 4/8/12/16, 5th at win 20 rejected by cap). This matches the T5.2 caveat ("only the node AFTER win 4 qualifies … there is no node after the final boss").
2. **Boss reward nodes (slots 2, 5) offer 1 biased choice, not 3.** T5.4 says "boss stays 0 or 1" while the spec's general requirement says "3 card choices at each free-pick node". The WU5 instruction "every NON-BOSS node offers 3 choices" is the governing statement, so non-boss → 3 and boss → 1 (JSON), with the final boss → 0 (also enforced by `VICTORY` routing). If the literal "3 at every free-pick node" is later required for boss nodes, set their `cardChoices` to 3 in sequence.json — no code change needed.
3. **`NodePolicyTest` (sim measurement floor) and `RunManagerTest` upgrade tests updated** to the cadence + cap-4 economy. The sim `NodePolicy.act` itself needed no change: its top-priority upgrade attempt simply no-ops off-cadence now (the `upgradeCard` guard closes the loop), and it falls through to repay/shop/loan/free-pick as designed.

### WU5 Issues

- **Pre-existing WU7 harness failure remains:** `RunSimulationHarnessTest` H1.1 (greedy win-rate ∈ [0.35, 0.55]) still fails — documented as a WU7 balance-tuning target since WU4 (the test's own comment notes the sweep "currently wins ~0%"). WU5's sequence.json (3-choose-1 picks) shifts the economy but does not recover the band; that is WU7's job (T7.6). Not a WU5 defect.
- No blockers. WU5 depends only on WU1–WU4 artifacts already present on `feat/asr-wu4-enemy-scaling`.

---

## WU6 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.debtsdecks.gdx.render.DebtHudModelTest"` |
| Focused test result | `DebtHudModelTest` (9 tests): reads debt + band/threshold constants from `CombatState`; zone transitions at SAFE/DANGER/PROXIMITY/EXECUTION boundaries (15/25/45/50/60); stale-value trap (debt 30 shows 30 after tick); risk distance-to-execution (35→15, 45→5); per-turn bleed = interest delta (35→6, 15→3, 0→0); archetype+tier passthrough — **all PASS**. |
| Compile result | `:app:compileDebugKotlin` BUILD SUCCESSFUL; `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL (whole test source set compiles). |
| Runtime harness command/scenario | **N/A — HUD is visual**. The on-screen band bar / archetype label / risk counter are covered by **manual device review** (see Issues). The headless assertion is that the renderer's pure `DebtHudModel` reads the correct immutable `CombatState` fields; that is the `DebtHudModelTest` above. Stripping the HUD must not change combat outcomes (T6.4 read-only proof). |
| Rollback boundary | Revert `DebtHudModel.kt` (new file), the `drawPlayer` signature change + band-bar/label/risk additions in `CombatRenderer.kt`, the `dominantArchetype` property in `RunManager.kt`, the `DEBT_BLEED_FLOOR` constant in `DebtConfig.kt`, the 3 new bundle keys (`hud.archetype`, `hud.risk_execution`, `hud.debt_bleed`) in `strings.properties` + `strings_es.properties`, and delete `DebtHudModelTest.kt`. All HUD changes are render-only; no combat logic, resolver, or data files are touched, so the rollback cannot affect any WU1–WU5 behavior. |

### WU6 Implementation Notes

- **T6.1 `dominantArchetype`**: added a read-only property on `RunManager` (`val dominantArchetype: Archetype get() = playerArchetype(deck, cardRegistry)`). Pure getter — no state read or written, so the HUD consuming it stays strictly read-only.
- **T6.2 Debt band bar**: new `DebtHudModel` (pure, no GDX) computes the view-model; `CombatRenderer.drawDebtBandBar` paints a thin bar from 0→`EXECUTION_THRESHOLD`(50) with a static 4-zone background (green/amber/orange/red), a bright fill up to the current debt, and tick marks at `DEBT_BLEED_FLOOR`(22, white), `BREAK_THRESHOLD`(30, white), `LEVERAGE_PAYOFF_BAND_CAP`(40, brass — the band-cap marker), and an execution-line border at 50. Matches design.md §G zones (0–21 safe, 22–29 danger, 30–49 proximity, 50+ execution).
- **T6.3 Archetype label + risk counter**: `drawPlayer` now calls `DebtHudModel.compute(state, run.dominantArchetype, state.archetypeTiers)` and draws `bundle.format("hud.archetype", archetype.name, tier)` (e.g. "Archetype: LEVERAGE T2" — enum name left untranslated, matching the turn-phase strip convention) and, when `debt >= DEBT_BLEED_FLOOR`, `bundle.format("hud.risk_execution", distanceToExecution)` (e.g. "15 to execution") plus a per-turn bleed line ("Bleed +6/turn" from `DebtConfig.applyInterest(debt) - debt`).
- **T6.4 Read-only**: `drawPlayer` only reads `state` and `run.dominantArchetype` (a pure getter); `DebtHudModel.compute` is side-effect-free. No render path mutates `CombatState`/`RunManager`. HUD removal is therefore safe by construction.

### WU6 Deviations

1. **`DebtHudModel` is a new pure file, not inline in `CombatRenderer`.** The design.md §G implies the additions live in `drawPlayer()`. To satisfy the focused-test requirement (assert the HUD reads the correct `CombatState` fields without a LibGDX context) the data extraction is factored into a GDX-free `DebtHudModel` object + `DebtHudData`/`DebtZone` types; `CombatRenderer` only paints pixels from that model. This is a structural split, not a behavior change — the read source is still the immutable `CombatState` snapshot, exactly as the architecture requires.
2. **`DEBT_BLEED_FLOOR = 22` added to `DebtConfig`** as a named constant (the spec references it but it was previously absent). The band-bar's first danger marker and the risk-counter's "show when actionable" threshold both read it, so the "named constant, not magic number" rule holds.
3. **Risk counter shows both bleed and distance-to-execution.** Spec "Requirement: Risk Counter" asks for "distance to bleed floor (22) and execution line (50)". The band bar already visualizes the 22→50 scale with ticks; the text counter shows the execution distance (the actionable number from the spec's own scenarios) plus the per-turn bleed value, which is the explicit WU6 "What to implement" #3 requirement. No separate "N under bleed floor" text was added to avoid clutter; the bar conveys it.

### WU6 Issues

- **Manual HUD review REQUIRED**: because the HUD is pixel output, the headless `DebtHudModelTest` proves the *data* is correct but not the *visual* layout (bar position within the player panel, label/risk text legibility, color contrast). A device/emo pass is needed to confirm the band bar + archetype label + risk counter render without overlap and readable. This is the WU6 runtime-harness/review equivalent; it is tracked as a manual check, not a code defect.
- No blockers. WU6 depends only on WU1–WU5 artifacts already present on `feat/asr-wu5-reward-economy` (CombatState.archetypeTiers, DebtConfig thresholds, RunManager.playerArchetype).

---

## WU7 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `./gradlew :app:testDebugUnitTest --tests "com.debtsdecks.core.simulation.RunSimulationHarnessTest"` |
| Focused test result | `RunSimulationHarnessTest` (15 tests): all GREEN. T7.2 greedy win **44.5%** ∈ [0.35,0.55]; T7.6 leverage **45.0%** (0.5pp above greedy, in band, <0.70); T7.4 PRESSURE **39.0%** vs LEVERAGE 45.0% = **6.0pp ≤ 10pp**; T7.5 hits-to-kill avg **8.75** ≥ 4.0 (per-combat [5,5,6,10,11,11,11,11]). |
| Full suite result | `:app:testDebugUnitTest`: **281 completed, 0 failed, 2 skipped** (the 2 skipped are the deliberate `@Disabled` F2 gates in `DebtPressureTest`, left untouched per instruction). |
| Runtime harness command/scenario | Headless 200-seed sweep via `RunSimulationHarnessTest` (`leverage policy comparison sweep`, `T7-4 pressure archetype win rate within 10pp of leverage`, `T7-5 avg hits-to-kill`). Deterministic (fixed seeds 0..199, seeded RNG). |
| Rollback boundary | Revert `DebtConfig.kt` (`PRESSURE_LOW_DEBT_THRESHOLD` 30, new `PRESSURE_DEBT_SCALING_DIVISOR=2` + doc comment), `CardResolver.kt` (the `pressureDebtScale` term in the PRESSURE attack branch), `strings.properties`/`strings_es.properties` (`low_debt_escalator.description` "below 30"), `I18nBundleTest.kt` (the two `below 30` assertions), `PressureTest.kt` (paydown `debt 15`→16, `debt 2`→7 assertions), `LeveragePayoffCardsDataTest.kt` (`leverage_strike.damage` 5→8 stale assertion). The uncommitted WU7 edits (DEBT_PAYOFF_DIVISOR=1, EXECUTION_DAMAGE_DIVISOR note, ScriptedPolicy power-playing) and all WU1–WU6 code are untouched by this rollback. |

### WU7 Implementation Notes

- **Diagnosis**: the pre-existing uncommitted WU7 tuning edits (DEBT_PAYOFF_DIVISOR 2→1, PRESSURE_LOW_DEBT_THRESHOLD 15→22, ScriptedPolicy power-playing) already closed the leverage-vs-greedy gap (leverage 45.0% vs greedy 44.5%), but over-corrected: PRESSURE sat 13.5pp below LEVERAGE (29.0% vs 42.5%) because PRESSURE has **no `debt_payoff` card**, so its end-of-turn low-debt escalator (threshold 22) never fired on PRESSURE's operating debt (~31.6) and PRESSURE died to the collector (139/200 defeats) before any escalator Strength compounded. The escalator threshold 22→30, 30→40 all measured ~0 win-rate movement, confirming it is a dead lever for PRESSURE's early-game deficit.
- **T7.6 fix (leverage-specific / pressure-interaction re-derivation, per the deferred-commit constraint)**: added a PRESSURE-only debt-scaled attack component `PRESSURE_DEBT_SCALING_DIVISOR=2` (`pressureDebtScale = floor(debt/2)` on `pressure`-tagged attacks in `CardResolver`). Gated to the `pressure` tag, so LEVERAGE and the greedy baseline are untouched, and the global `LEVERAGE_DIVISOR` was **NOT** bumped (the constraint's forbidden shortcut). The flat `+tier` was deliberately NOT added (that would alter the WU3 T3.2 accepted tier behavior); only the debt curve is new.
- **Band-cap / threshold**: `PRESSURE_LOW_DEBT_THRESHOLD` retuned 22→30 (aligns the low-debt escalator with PRESSURE's actual operating debt and keeps the i18n in sync). The exploit guard `LEVERAGE_PAYOFF_BAND_CAP` hard-freeze at 40 is UNCHANGED, so T7.3 parking trap intact.
- **Test updates (required by the re-derivation, not masked regressions)**: `PressureTest` paydown `debt 15`→16 and `debt 2`→7 (paydown_strike now debt-scales as a pressure card); `LeveragePayoffCardsDataTest` `leverage_strike.damage` 5→8 (pre-existing stale assertion vs committed JSON — fixed so the suite is green, out of T7.6 scope but necessary for a clean run).

### WU7 Deviations

1. **PRESSURE damage identity is now debt-scaled (design change from WU3).** WU3 specified PRESSURE's damage identity as weak/vuln escalation + T2+ low-HP multiplier; T7.6's parity requirement forced adding a `floor(debt/2)` component to PRESSURE attacks. This is authorized by the T7.6 constraint ("re-derive … pressure interaction / tier-damage") and keeps PRESSURE within 10pp of LEVERAGE, but it means PRESSURE is now also a debt-scaling archetype, not purely a status archetype.
2. **`LeveragePayoffCardsDataTest` assertion fixed (5→8)** to match the committed `leverage_strike.damage=8` in `cards/all.json`. This was a pre-existing stale assertion (failing before T7.6), not introduced by this change; fixed only to deliver a green full suite.

### WU7 Issues

- None blocking. Harness GREEN, full suite 281/0/2. PRESSURE parity margin is 6.0pp (comfortable; deterministic sweep). Greedy also rose slightly (44.5%) because the greedy policy occasionally drafts `paydown_strike` (pressure-tagged) and now benefits from the same debt-scale — benign, still within band and within 5pp of leverage.

---

## WU1–WU4 Evidence (carried forward; full detail in prior WU4 apply-progress)

- **WU1**: `archetypeTiers()` (tag-count tier, 2/4/6 → T1/2/3, PRESSURE counts only `"pressure"`), DebtConfig constants (`ARCHETYPE_TIER_*`, `LEVERAGE_PAYOFF_BAND_CAP=40`, `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5`, `DEBT_STRENGTH_DIVISOR=10`), CombatState + CombatEngine threading. Deviation: new `DEBT_STRENGTH_DIVISOR` (value 10) rather than reusing `DEBT_SCALING_ATTACK_DIVISOR` (kept at 8) to avoid changing gameplay numbers; band-cap constant named `LEVERAGE_PAYOFF_BAND_CAP` per design.
- **WU2**: band-cap payoff (`min(debt,40)/2` freeze), `/10`→`DEBT_STRENGTH_DIVISOR`, leverage tier flat +dmg. Deviation: implemented the orchestrator's hard-freeze formula (debt=49 == debt=40) rather than design's diminishing curve (debt=49→21); `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR` retained unused as tuning surface.
- **WU3**: PRESSURE tier escalation, +20% low-HP at T2+, `paydown_strike`/`weak_pressure`/`low_debt_escalator` cards + end-of-turn hook. Deviations: +20% gated to PRESSURE-tagged cards; `PRESSURE_LOW_DEBT_THRESHOLD` in DebtConfig; paydown damage includes the unconditional leverage bonus (debt=15 → 4+2+3=9, not 7); only 3 PRESSURE cards (T3.7 deferred).
- **WU4**: `ActModifier` model + per-act scaling (round, not floor), `actForSlotIndex` threading, `actModifiers` in `all.json`, FORECLOSE/HEDGE intents. Deviations: godfather omitted (not in catalog/sequence); rounding per design table; AUDIT intent deferred (FV WIP, same PR #22 dependency as T3.7). `RunSimulationHarnessTest.H1.1` win-rate failure first observed here (WU7 target).
