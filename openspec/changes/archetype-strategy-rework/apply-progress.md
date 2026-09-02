# Apply Progress: Archetype Strategy Rework

Artifact store: `both` (OpenSpec file + Engram topic `sdd/archetype-strategy-rework/apply-progress`).
Mode: Standard (strict_tdd not active in init).
Last updated: WU1 (Config + Archetype Tiers).

## Cumulative Task State (all WUs)

| Task | WU | Status |
|------|----|--------|
| T1.1 `archetypeTiers()` | WU1 | [x] complete |
| T1.2 DebtConfig constants | WU1 | [x] complete |
| T1.3 CombatState carries tiers | WU1 | [x] complete |
| T1.4 CombatEngine populates tiers | WU1 | [x] complete |
| T2.2 Divisor unification (`/10` → `DEBT_STRENGTH_DIVISOR`) | WU2 (pulled into WU1 scope by orchestrator) | [x] complete |
| T2.1 Band-cap payoff formula | WU2 | [ ] pending |
| T2.3 Leverage tier damage | WU2 | [ ] pending |
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

## Implementation Notes (WU1)

- Tier formula: `tier = min(ARCHETYPE_TIER_MAX, floor(tagCount / ARCHETYPE_TIER_TAGS_PER_TIER))` = `min(3, tagCount/2)`.
  Thresholds fall out as 2/4/6 cards → tier 1/2/3. Result map is seeded for ALL three archetypes at 0
  (complete map, not sparse) so consumers read `tiers[archetype]` without null-guards.
- PRESSURE tier counts ONLY `"pressure"`-tagged cards. Plain non-economy cards still signal PRESSURE inside
  `playerArchetype()` (tie-break) but deliberately do NOT advance the PRESSURE tier — verified by a trap test.
- `DebtConfig` additions: `ARCHETYPE_TIER_TAGS_PER_TIER=2`, `ARCHETYPE_TIER_MAX=3`,
  `LEVERAGE_PAYOFF_BAND_CAP=40`, `LEVERAGE_PAYOFF_DIMINISHING_DIVISOR=5`, `DEBT_STRENGTH_DIVISOR=10`.

## Deviations

1. **Divisor constant name (`DEBT_STRENGTH_DIVISOR` vs spec's `DEBT_SCALING_ATTACK_DIVISOR`):**
   The existing `DEBT_SCALING_ATTACK_DIVISOR` (=8) is used only by the ATTACK `debt_scaling` path (line 135).
   The SKILL `debt_scaling` strength path (line 188) was a hardcoded `/10`. Reusing `DEBT_SCALING_ATTACK_DIVISOR`
   at line 188 would change gameplay (10→8), violating the orchestrator's hard guardrail "Do NOT change gameplay
   numbers beyond the divisor unification". So a new constant `DEBT_STRENGTH_DIVISOR=10` was added (value preserved),
   matching design.md/T1.2. The leverage-archetype spec scenario names `DEBT_SCALING_ATTACK_DIVISOR` literally; the
   verify-phase test (WU8 T8.3) may need to accept the new name or the two paths' constants should be reconciled.
2. **Band-cap constant name (`LEVERAGE_PAYOFF_BAND_CAP` vs prompt's `LEVERAGE_BAND_CAP`):** used the design.md /
   WU2-T2.1 canonical name `LEVERAGE_PAYOFF_BAND_CAP` for cross-WU consistency. Value locked at 40 per design.

## Issues

- The "working Gradle binary" cited in the prompt (gradle-8.9) is REJECTED by this project's Android Gradle Plugin
  (minimum 8.11.1). Used the locally-available `gradle-8.11.1-bin` instead, which the wrapper also targets. No code
  issue — environment note only.
- No blockers. WU1 has no dependency on WU2–WU8.
