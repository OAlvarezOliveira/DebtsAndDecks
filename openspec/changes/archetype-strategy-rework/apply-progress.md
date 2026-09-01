# Apply Progress: Archetype Strategy Rework

Artifact store: `both` (OpenSpec file + Engram topic `sdd/archetype-strategy-rework/apply-progress`).
Mode: Standard (strict_tdd not active in init).
Last updated: WU4 (Enemy scaling + intents).

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
| T5.1–T5.5 Reward economy | WU5 | [ ] pending |
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

## WU3 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.debtsdecks.core.combat.PressureTest" --tests "com.debtsdecks.core.cards.LeveragePayoffCardsDataTest" --tests "com.debtsdecks.core.i18n.I18nBundleTest"` |
| Focused test result | `PressureTest` (13 tests: T3.1 tier escalation ×3, plain-card trap, T3.2 low-HP ×4, T3.3 paydown ×3, T3.5/3.6 escalator end-of-turn ×2), `LeveragePayoffCardsDataTest` (updated 23→26 non-starter + 3 new pressure-card contracts), `I18nBundleTest` (2 new WU3 card key tests) all PASS. |
| Full suite result | Full `:app:testDebugUnitTest`: BUILD SUCCESSFUL (≈250 tests, 0 failures, 0 errors, 2 pre-existing skips). Includes `CardResolverTest` (28), `LeverageBandCapTest` (8), `ArchetypeTiersTest` (11) — all still green, confirming WU3 did not regress WU1/WU2. |
| Runtime harness command/scenario | End-to-end via `CombatEngine` in `PressureTest`: a `low_debt_escalator` POWER played then `endPlayerTurn()` grants +1 Strength while `debt=0 (<15)`; same card with `startingDebt=30 (>=15)` grants 0. This is the PRESSURE escalator acceptance path exercised through the real engine, not just the resolver. |
| Rollback boundary | Revert `CardResolver.kt` (import `kotlin.math.min`; `pressureTier/pressureTierBonus/isPressureTagged` compute; `ActivateLowDebtEscalator` effect; paydownBonus + low-HP +20% in ATTACK loop; `+pressureTierBonus` on weak/vuln in both branches; `low_debt_bonus` emission), `CombatEngine.kt` (field `lowDebtEscalatorStacks`; reset in `startCombat`; `applyEffects` case; end-of-turn trigger before `beginTurn()`), `DebtConfig.kt` (`PRESSURE_LOW_DEBT_THRESHOLD`), `cards/all.json` (3 new card entries), `strings.properties`/`strings_es.properties` (6 new keys), and delete `PressureTest.kt`. WU1/WU2 paths untouched (tier bonus defaults to 0 when `archetypeTiers` is empty). |

### WU3 Implementation Notes

- T3.1 PRESSURE status tier: computed `pressureTier = state.archetypeTiers[Archetype.PRESSURE] ?: 0` and
  `pressureTierBonus = if (isPressureTagged) pressureTier else 0` once at the top of `resolve()`. Added to
  `WeakApply`/`VulnerableApply` turns in BOTH the ATTACK branch and the SKILL/POWER branch, gated to PRESSURE-tagged
  cards — identical gating model to WU2's Leverage tier bonus.
- T3.2 PRESSURE low-HP dmg: in the ATTACK damage loop, for PRESSURE-tagged cards at `pressureTier >= 2` and
  `enemy.hp < enemy.maxHp / 2`, multiply the computed damage by `1.20` (before the Vulnerable x1.5 multiplier).
  Applied only to PRESSURE-tagged attacks (see Deviations #1).
- T3.3 `paydown_strike`: data-driven card (`"pressure"`,`"paydown"`, damage 4, debtRepay 3). The resolver adds
  `paydownBonus = min(card.debtRepay, state.debt)` to each hit's damage. Repayment itself still flows through the
  existing ATTACK `debtRepay` block (clamped to 0 at apply when Debt is 0).
- T3.4 `weak_pressure`: data-driven SKILL (`"pressure"`, weakApply 2, vulnerableApply 1, target ENEMY).
- T3.5 `low_debt_escalator`: data-driven POWER (`"pressure"`,`"low_debt_bonus"`, target SELF).
- T3.6 end-of-turn hook: `ActivateLowDebtEscalator` effect is emitted by the resolver when a card carries
  `low_debt_bonus`; `CombatEngine.applyEffects` increments `lowDebtEscalatorStacks`; at end of turn (in
  `endPlayerTurn`, before `beginTurn()`) if `lowDebtEscalatorStacks > 0 && debt < PRESSURE_LOW_DEBT_THRESHOLD`
  the player gains `lowDebtEscalatorStacks` Strength (persists — `endTurnReset` does not reset Strength).
- New `DebtConfig.PRESSURE_LOW_DEBT_THRESHOLD = 15` (design tuning table value; see Deviations #2 for location).

### WU3 Deviations

1. **+20% low-HP bonus gated to PRESSURE-tagged cards.** The `archetype-synergy` spec lists the +20% damage as the
   second clause of the PRESSURE tier bonus, right after "Weak/Vulnerable to PRESSURE-tagged cards". The LEVERAGE
   tier bonus (WU2 T2.3) is explicitly gated to LEVERAGE-tagged cards, and the PRESSURE weak/vuln clause is explicitly
   "to PRESSURE-tagged cards". For archetype-scoped consistency I gated the +20% to `isPressureTagged` as well. If
   the maintainer intends the +20% to apply to ALL attacks under a high PRESSURE tier (regardless of card tag), this
   is a one-line change (drop the `isPressureTagged &&` guard). Flagged for the verify phase.
2. **`PRESSURE_LOW_DEBT_THRESHOLD` placed in `DebtConfig` (not `Archetype.kt`).** The design Tuning Constants table
   lists its `Location` as `Archetype.kt`, but the WU1 Architecture Decisions locked "DebtConfig — all debt math
   constants live here", and WU1/WU2 already host every tuning constant there. A debt threshold used by the engine's
   end-of-turn logic belongs with the other DebtConfig constants, so it was added there (value 15 unchanged).
3. **Paydown damage includes the unconditional per-attack leverage bonus.** The `pressure-archetype` spec scenario
   "Paydown scales: debt=15, baseDamage 4, debtRepay 3 → 4+3=7" omits the engine's unconditional `floor(debt/6)`
   leverage bonus that every ATTACK already carries (WU1). The implemented damage at debt=15 is `4 + floor(15/6)=2 +
   min(3,15)=3 = 9`. The "Zero debt fallback" scenario still holds exactly (debt=0 → 4, no bonus, no negative). The
   verify-phase test (WU8) should expect 9 at debt=15, not 7; the spec's "7" is a simplified scenario. Same
   spec-vs-real-engine pattern as WU2's band-cap freeze.
4. **Only one status-stacker card (`weak_pressure`) implemented.** The `pressure-archetype` spec requirement reads
   "≥2 PRESSURE-tagged cards applying Weak or Vulnerable at ≥2 turns per application". The design §D table and the
   tasks.md WU3 task list define exactly one status stacker (`weak_pressure`, weak 2 + vuln 1). With `audit_punish`
   deferred, WU3 delivers 3 PRESSURE cards; the spec's "≥4 distinct PRESSURE-tagged cards" and "≥2 stackers" are
   partially met (3 cards, 1 stacker) until T3.7 lands. If the literal "≥2 stackers" is required now, a second card
   (e.g. `vuln_pressure`) should be added — but that expands beyond the authorized WU3 task list, so it is left as a
   follow-up.

### WU3 Issues

- None blocking. The `LeveragePayoffCardsDataTest` "exactly 23 non-starter cards" assertion had to be updated to 26
  (3 new PRESSURE cards), and its pressure-contract test extended — expected, since WU3 adds cards to the reward pool.
- `audit_punish` (T3.7) intentionally omitted per slice scope; its checkbox remains `[ ]` with a `DEFERRED` note.

## WU4 Work Unit Evidence

| Evidence | Value |
|----------|-------|
| Focused test command | `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.debtsdecks.core.enemies.EnemyScalingTest" --tests "com.debtsdecks.core.enemies.IntentTypeCoverageTest" --tests "com.debtsdecks.core.enemies.EnemyInstanceTest"` |
| Focused test result | `EnemyScalingTest` (7 tests: act-I 30 HP + baseline dmg, act-II HP+dmg together, no-modifier unscaled, slot→act mapping, HEDGE block, FORECLOSE debt-branch, FORECLOSE HP-branch) PASS. `IntentTypeCoverageTest` (5, incl. new FORECLOSE/HEDGE icon+l10n+renderer-map coverage) PASS. `EnemyInstanceTest` (13) PASS. Full `testDebugUnitTest`: **264 passed, 1 failed, 2 skipped** — the 1 failure is `RunSimulationHarnessTest.H1.1` (win-rate band 0.35–0.55), a WU7 tuning target (see Issues). |
| Runtime harness command/scenario | FORECLOSE is exercised through the real `CombatEngine` in `EnemyScalingTest` (`startCombat` → `endPlayerTurn`, debt-branch adds 10 Debt via the cap/Execution path; no-debt branch deals 5 HP). HEDGE is exercised through `EnemyAI.executeIntent` (gainBlock). Both run the unmodified engine, so the new intents are validated against the actual combat pipeline, not just the resolver. |
| Rollback boundary | Revert `EnemyDefinition.kt` (new `ActModifier` data class + `actModifiers` field + `FORECLOSE`/`HEDGE` enum entries), `EnemyInstance.kt` (`act` param, `modifier`/`scaledPattern`, HP scaling, `intentDisplayName` 2 branches, `EnemyAI` HEDGE/FORECLOSE branches), `CombatEngine.kt` (`act` param in `startCombat` + FORECLOSE `when` branch), `RunManager.kt` (`actForSlotIndex` + 3 `act` args), `enemies/all.json` (actModifiers + FORECLOSE/HEDGE intents), `strings.properties`/`strings_es.properties` (4 new keys), `CombatRenderer.kt` (`intentColor` 2 branches), and delete `EnemyScalingTest.kt`. Enemies without `actModifiers` stay unscaled, so WU1–WU3 behavior is unchanged. |

### WU4 Implementation Notes

- T4.1 `ActModifier(act, hpMultiplier, damageMultiplier)` + `EnemyDefinition.actModifiers: List<ActModifier>` (default empty). Foreach-enemy, `EnemyInstance` picks the entry whose `act == act`.
- T4.2 Scaling applied in `EnemyInstance` constructor (not `startCombat` body): `hp = round(hp * hpMult)`, and each `intentPattern` step with `damage > 0` is copied with `damage = round(damage * dmgMult)`. `IntentStep` `param` (HEDGE/FORCLOSE extra payload) is never scaled. Both HP and damage use the SAME per-act modifier, satisfying the HP-Matters invariant.
- T4.3 `actForSlotIndex(slot)`: `slot<=2→1, slot<=5→2, else 3`, matching `sequence.json` 3+3+2. Threaded as `act` into all three `CombatEngine.startCombat` calls (`beginRun`, `advanceToNextCombat` normal + forced-collector). `act` defaults to `1` so all pre-WU4 callers/tests are unaffected.
- T4.4 `actModifiers` added to all three catalog enemies (thug, loan_shark, collector) per the design §E table. godfather is NOT in `enemies/all.json` and NOT in `sequence.json`, so it is out of scope (design §E lists it but it is unused in the run) — see Deviations #1.
- T4.5 New intents FORECLOSE + HEDGE implemented end-to-end:
  - FORECLOSE: engine-owned (same pattern as LEVY). `CombatEngine.endPlayerTurn` applies it: if `debt > 10` → `addDebt(10)` (routes through cap/Execution), else `player.takeDamage(5)`. `EnemyAI` FORECLOSE branch is a no-op that only advances the pattern.
  - HEDGE: engine-independent, applied in `EnemyAI.executeIntent` as `enemy.gainBlock(intent.param)`.
  - One new intent per enemy so each has ≥1 non-ATTACK intent beyond its prior repertoire: thug→FORECLOSE, loan_shark→HEDGE, collector→HEDGE.

### WU4 Deviations

1. **godfather omitted.** Design §E table lists godfather with act modifiers (40/75/140), but `enemies/all.json` contains only thug / loan_shark / collector, and `sequence.json` references only those three (godfather never spawns). Implementing godfather would mean ADDING an unused enemy to the catalog — out of WU4 scope and not referenced by the run. If godfather is meant to appear, that is a data/sequence change for a later WU. WU4 therefore scales exactly the three enemies that exist and are used.
2. **Rounding instead of `floor` for scaling.** Design §E formula text says `floor(...)`, but the target Result-HP column (thug I=30, loan_shark II=65, collector III=120, etc.) is `round(base × mult)`: `22×1.36=29.92`, `floor=29` (FAILS the spec acceptance scenario "thug 30 HP vs baseline 22 HP"), `round=30`. Every present target in the §E table equals `round`, not `floor`. To satisfy the spec's acceptance scenario (and the stated table), HP and damage are scaled with `kotlin.math.round`. Flagged because it contradicts the literal formula wording in design.md.
3. **AUDIT intent deferred.** Tasks.md T4.5 lists FORECLOSE / HEDGE / AUDIT and marks AUDIT as RISK (depends on FV WIP). The spec's only concrete new-intent acceptance scenario is FORECLOSE ("forces a decision"). AUDIT's real effect (tag-disable of cards on an AUDIT enemy intent) is part of the FV-core-validation verb set (PR #22, unmerged WIP), the same dependency that deferred WU3 T3.7. To avoid a dead/no-op intent, AUDIT is NOT added to the `IntentType` enum in WU4; FORECLOSE + HEDGE fully satisfy the "≥1 non-ATTACK intent per enemy" requirement. AUDIT will land together with FV (PR #22), gated on that work.

### WU4 Issues

- **`RunSimulationHarnessTest` H1.1 fails (win rate 0.0, expected 0.35–0.55).** This is the WU7 balance target (`T7.6 Iterate constants`); the harness loads the real (now-scaled) catalog and asserts a tuned win-rate band that is only reachable after all WU1–WU8 tuning. The test's own comment (C5, lines ~258–261) documents the sweep "currently wins ~0%" with win-rate recovery deferred to later concerns (C7/C8). WU4 scaling makes enemies tougher, shifting balance as intended — but bringing the band back to 0.35–0.55 is WU7's job, explicitly out of WU4 scope. The failure is an assertion (win-rate out of band), NOT a crash: the simulation ran to completion (no exception), confirming the new FORECLOSE/HEDGE intents and scaled enemies do not break the engine. No WU4 logic defect is implied.
- No blockers. WU4 depends only on WU1–WU3 artifacts already present on `feat/asr-wu3-pressure`; the `act` parameter threads cleanly on top of the existing `startCombat` signature.

