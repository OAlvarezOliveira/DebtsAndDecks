# Apply Progress: Archetype Strategy Rework

Artifact store: `both` (OpenSpec file + Engram topic `sdd/archetype-strategy-rework/apply-progress`).
Mode: Standard (strict_tdd not active in init).
Last updated: WU2 (Leverage Band-Cap + Divisor).

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
| T3.1–T3.7 Pressure cards + synergy | WU3 | [ ] pending |
| T4.1–T4.5 Enemy scaling + intents | WU4 | [ ] pending |
| T5.1–T5.5 Reward economy | WU5 | [ ] pending |
| T6.1–T6.4 HUD | WU6 | [ ] pending |
| T7.1–T7.6 Tuning + sim validation | WU7 | [ ] pending |
| T8.1–T8.7 Tests | WU8 | [ ] pending |

> Note: the orchestrator's resolved WU1 scope explicitly included the `CardResolver` `/10`
> divisor unification (tasks.md T2.2), so it is marked complete here even though it sits under WU2
> in the task breakdown. WU2 implementer should skip T2.2.

## WU1 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle testDebugUnitTest --tests "com.debtsdecks.core.combat.ArchetypeTiersTest" --tests "com.debtsdecks.core.combat.resolution.CardResolverTest"` |
| Focused test result | 11 `ArchetypeTiersTest` + 28 `CardResolverTest` tests PASS. Full `testDebugUnitTest`: 235 tests, 0 failures, 0 errors, 2 pre-existing skips. |
| Runtime harness command/scenario | N/A — WU1 is pure config + a pure `archetypeTiers()` compute + a 1-line constant swap; no runtime boundary exists. Acceptance is unit-level (spec scenarios + trap). A `CombatEngine.startCombat` glue test confirms tiers are threaded into `CombatState` with no runtime dependency. |
| Rollback boundary | Revert `Archetype.kt` (new `archetypeTiers()` fn + `import kotlin.math.min`), `DebtConfig.kt` (5 new constants), `CombatState.kt` (1 field w/ default), `CombatEngine.kt` (1 field + 2 lines), `CardResolver.kt` (1-line `/10` → constant), and delete `ArchetypeTiersTest.kt`. No behavior changes beyond the named-constant swap; `CombatState` field defaults to `emptyMap()` so all prior snapshot construction still compiles. |

### WU1 Implementation Notes

- Tier formula: `tier = min(ARCHETYPE_TIER_MAX, floor(tagCount / ARCHETYPE_TIER_TAGS_PER_TIER))` = `min(3, tagCount/2)`.
  Thresholds fall out as 2/4/6 cards → tier 1/2/3. Result map is seeded for ALL three archetypes at 0
  (complete map, not sparse) so consumers read `tiers[archetype]` without null-guards.
- PRESSURE tier counts ONLY `"pressure"`-tagged cards. Plain non-economy cards still signal PRESSURE inside
  `playerArchetype()` (tie-break) but deliberately do NOT advance the PRESSURE tier — verified by a trap test.
- `DebtConfig` additions: `ARCHETYPE_TIER_TAGS_PER_TIER=2`, `ARCHETYPE_TIER_MAX=3`,
  `LEVERAGE_PAYOFF_BAND_CAP=40`, `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5`, `DEBT_STRENGTH_DIVISOR=10`.

### WU1 Deviations

1. **Divisor constant name (`DEBT_STRENGTH_DIVISOR` vs spec's `DEBT_SCALING_ATTACK_DIVISOR`):**
   The existing `DEBT_SCALING_ATTACK_DIVISOR` (=8) is used only by the ATTACK `debt_scaling` path (line 135).
   The SKILL `debt_scaling` strength path (line 188) was a hardcoded `/10`. Reusing `DEBT_SCALING_ATTACK_DIVISOR`
   at line 188 would change gameplay (10→8), violating the orchestrator's hard guardrail "Do NOT change gameplay
   numbers beyond the divisor unification". So a new constant `DEBT_STRENGTH_DIVISOR=10` was added (value preserved),
   matching design.md/T1.2. The leverage-archetype spec scenario names `DEBT_SCALING_ATTACK_DIVISOR` literally; the
   verify-phase test (WU8 T8.3) may need to accept the new name or the two paths' constants should be reconciled.
2. **Band-cap constant name (`LEVERAGE_PAYOFF_BAND_CAP` vs prompt's `LEVERAGE_BAND_CAP`):** used the design.md /
   WU2-T2.1 canonical name `LEVERAGE_PAYOFF_BAND_CAP` for cross-WU consistency. Value locked at 40 per design.

### WU1 Issues

- The "working Gradle binary" cited in the prompt (gradle-8.9) is REJECTED by this project's Android Gradle Plugin
  (minimum 8.11.1). Used the locally-available `gradle-8.11.1-bin` instead, which the wrapper also targets. No code
  issue — environment note only.
- No blockers. WU1 has no dependency on WU2–WU8.

## WU2 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.debtsdecks.core.combat.LeverageBandCapTest" --tests "com.debtsdecks.core.combat.resolution.CardResolverTest" --tests "com.debtsdecks.core.combat.ArchetypeTiersTest"` |
| Focused test result | `LeverageBandCapTest` (8 tests), `CardResolverTest` (28), `ArchetypeTiersTest` (11) all PASS. |
| Full suite result | Full `:app:testDebugUnitTest`: BUILD SUCCESSFUL (235 tests, 0 failures, 0 errors, 2 pre-existing skips). |
| Runtime harness command/scenario | N/A — WU2 is a pure payoff-formula band-cap + a pure tier-bonus addition on existing `CardResolver` paths; no runtime boundary exists. The band-cap guard (debt=49 == debt=40) is proven at unit level per the orchestrator's explicit test; the spec's harness trap ("EXECUTION-1 parking <70% win") belongs to WU7 (T7.3) which consumes this band-cap. |
| Rollback boundary | Revert `CardResolver.kt` (4 edits: imports, `+leverageTierBonus` in ATTACK branch, band-cap in both `debt_payoff` branches, `+leverageTierBonus` in `baseDamage`), `DebtConfig.kt` (one `leveragePayoffBandCapped()` fn), `Archetype.kt` (one `isLeverageTagged()` fn), and delete `LeverageBandCapTest.kt`. All other paths unchanged; `archetypeTiers` default `emptyMap()` keeps tier bonus at 0, so non-WU2 code is unaffected. |

### WU2 Implementation Notes

- T2.1 band-cap: added `DebtConfig.leveragePayoffBandCapped(debt) = min(debt, LEVERAGE_PAYOFF_BAND_CAP) / DEBT_PAYOFF_DIVISOR`.
  Wired into BOTH `debt_payoff` branches in `CardResolver` (ATTACK payoff and SKILL block). Below cap: `floor(debt/2)`
  (matches spec "Linear below cap", debt=30→15). Above cap: frozen at `floor(40/2)=20`.
- T2.3 tier damage: in the ATTACK branch, computed `leverageTier = state.archetypeTiers[Archetype.LEVERAGE] ?: 0` and
  `leverageTierBonus = if (isLeverageTagged(card.definition.tags)) leverageTier else 0`. The bonus is added to
  `execution_damage` damage, to the `debt_payoff` ATTACK `withLeverage`, and into `baseDamage` for the main attack
  path. Gated so NON-Leverage cards (no leverage tag) receive no tier bonus. Flat unconditional leverage
  `floor(debt/LEVERAGE_DIVISOR)` is unchanged and stacks with the tier (per design A: "+n flat damage per attack
  (stacks with existing floor(debt / LEVERAGE_DIVISOR))").
- Added `isLeverageTagged(tags)` to `Archetype.kt` so `CardResolver` detects Leverage cards without duplicating the
  private `LEVERAGE_TAGS` set (debt_scaling / debt_payoff / execution_damage).

### WU2 Deviations

1. **Band-cap shape: hard freeze vs design.md's diminishing curve.** `design.md` §B and the spec scenario
   "Diminishing above cap" describe `floor(40/N) + floor((debt-40)/M)` with the excess divisor `M=5`, which yields
   debt=49→21 and debt=50→22. The orchestrator's explicit instruction for WU2 was `bonus = floor(min(debt, BAND_CAP) /
   DIVISOR)` and a concrete test proving `debt=49 == debt=40` (no extra reward for over-leveraging). The diminishing
   curve FAILS that equality test (21 ≠ 20), so I implemented the orchestrator's hard-freeze formula. The
   `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5` constant is retained in `DebtConfig` as the tuning surface for a softer
   curve but is not used by the freeze. **The verify phase / WU7 harness (T7.3 parking trap) must use the freeze
   semantics, not the spec's 22@50 value.** Acceptance "Linear below cap" (debt=30→15) still holds exactly.
2. Tier damage applied to `execution_damage` and `debt_payoff` ATTACK branches (not only the main `debt_scaling` path),
   since all three are Leverage-tagged attacks and the design says "per attack". This is broader than the minimal
   reading but matches "Leverage-tagged attack cards" literally. Non-attack / non-Leverage cards are untouched.

### WU2 Issues

- None. WU2 depends only on WU1 artifacts (constants + `archetypeTiers` threading), which are present on the base
  branch `feat/asr-wu1-config-tiers`. No blockers.
