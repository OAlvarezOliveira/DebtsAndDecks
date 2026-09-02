# Apply Progress: Archetype Strategy Rework

Artifact store: `both` (OpenSpec file + Engram topic `sdd/archetype-strategy-rework/apply-progress`).
Mode: Standard (strict_tdd not active in init).
Last updated: WU5 (Reward economy).

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
| T6.1–T6.4 HUD | WU6 | [ ] pending |
| T7.1–T7.6 Tuning + sim validation | WU7 | [ ] pending |
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

## WU1–WU4 Evidence (carried forward; full detail in prior WU4 apply-progress)

- **WU1**: `archetypeTiers()` (tag-count tier, 2/4/6 → T1/2/3, PRESSURE counts only `"pressure"`), DebtConfig constants (`ARCHETYPE_TIER_*`, `LEVERAGE_PAYOFF_BAND_CAP=40`, `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5`, `DEBT_STRENGTH_DIVISOR=10`), CombatState + CombatEngine threading. Deviation: new `DEBT_STRENGTH_DIVISOR` (value 10) rather than reusing `DEBT_SCALING_ATTACK_DIVISOR` (kept at 8) to avoid changing gameplay numbers; band-cap constant named `LEVERAGE_PAYOFF_BAND_CAP` per design.
- **WU2**: band-cap payoff (`min(debt,40)/2` freeze), `/10`→`DEBT_STRENGTH_DIVISOR`, leverage tier flat +dmg. Deviation: implemented the orchestrator's hard-freeze formula (debt=49 == debt=40) rather than design's diminishing curve (debt=49→21); `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR` retained unused as tuning surface.
- **WU3**: PRESSURE tier escalation, +20% low-HP at T2+, `paydown_strike`/`weak_pressure`/`low_debt_escalator` cards + end-of-turn hook. Deviations: +20% gated to PRESSURE-tagged cards; `PRESSURE_LOW_DEBT_THRESHOLD` in DebtConfig; paydown damage includes the unconditional leverage bonus (debt=15 → 4+2+3=9, not 7); only 3 PRESSURE cards (T3.7 deferred).
- **WU4**: `ActModifier` model + per-act scaling (round, not floor), `actForSlotIndex` threading, `actModifiers` in `all.json`, FORECLOSE/HEDGE intents. Deviations: godfather omitted (not in catalog/sequence); rounding per design table; AUDIT intent deferred (FV WIP, same PR #22 dependency as T3.7). `RunSimulationHarnessTest.H1.1` win-rate failure first observed here (WU7 target).
