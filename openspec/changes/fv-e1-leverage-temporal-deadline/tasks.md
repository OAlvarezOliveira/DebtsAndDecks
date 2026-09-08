# Tasks: FORECLOSE Temporal Deadline (FV.E1 root-cause lever)

Strict TDD. Single PR (see forecast below) — this is a root-cause engine change, not a data sweep,
so splitting it would leave a broken intermediate state (a window that arms but never resolves).

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 90–160 (design estimate 40–120 Kotlin + JSON + fixture + doc table) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Window mechanic (engine + data field) green under scripted unit tests | PR 1 | `./gradlew testDebugUnitTest --tests '*CombatEngineTest*'` | N/A — scripted-debt unit tests, no full-run harness needed for the mechanic itself | `git revert` of the single commit restores snapshot FORECLOSE (proposal §10) |
| 2 | Threshold sweep + E1/E2 measurement + `BALANCE-BASELINE.md` record, same PR | PR 1 (same) | `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest'` | Full 200-seed sim harness (proposal §7) | Reverting the `all.json` field value alone is safe; reverting the whole commit is the documented fallback |

## Phase 0: Spec correction (prerequisite, before any code)

- [ ] 0.1 Correct `openspec/changes/fv-e1-leverage-temporal-deadline/specs/enemy-intent-data/spec.md`
      "Requirement: FORECLOSE Cancel-Threshold Field on Intent Data" — the phrase "which continues
      to drive the window's expiry-check axis, i.e. the level compared at window close" already
      states the two-threshold resolution correctly; add one explicit line cross-referencing
      `combat-engine-foreclose/spec.md`'s "Uncancelled Window Expiry" requirement so the two spec
      files state the same close-check axis in matching words, per design D2. Confirms the delta
      specs no longer read as contradictory before implementation starts.

## Phase 1: Data model — `IntentStep.cancelThreshold` field

- [ ] 1.1 **RED** `app/src/test/java/com/debtsdecks/core/enemies/EnemyDefinitionTest.kt` (or the
      existing intent-data test file): a new test asserting `IntentStep` exposes a
      `cancelThreshold: Int` field defaulting to `0`, independent of `param`.
- [ ] 1.2 **GREEN** add `cancelThreshold: Int = 0` to `IntentStep` in
      `app/src/main/java/com/debtsdecks/core/enemies/EnemyDefinition.kt`, and the mirrored field on
      `EnemyInstance.Intent`.
- [ ] 1.3 Add `cancelThreshold` to the `loan_shark` FORECLOSE step in
      `app/src/main/assets/enemies/all.json`, seeded at `param`'s current value (27) as the safe
      starting point for the sweep in Phase 4 — not the final tuned value.

## Phase 2: Engine — window arm/tick/resolve

- [ ] 2.1 **RED** `CombatEngineTest`: FORECLOSE announcement arms a pending window
      (`forecloseWindowTurnsLeft == 3`) and does NOT apply `player.takeDamage(player.hp)` or
      increment `forecloseSeizureCount` in that same enemy-action phase (combat-engine-foreclose
      spec, "Window opens on announcement").
- [ ] 2.2 **RED** `CombatEngineTest`: while a window is pending, `debt` dropping below
      `cancelThreshold` before turn 0 cancels the seizure (no damage, no count increment) and the
      enemy's `intentPattern` resumes normal advance next turn (spec "Player repays inside the
      window").
- [ ] 2.3 **RED** `CombatEngineTest`: window closes with `debt >= param` (today's axis, per design
      D2) still true → `forecloseSeizureCount++` and `player.takeDamage(player.hp)` fire exactly as
      today's snapshot check (spec "Window expires uncancelled").
- [ ] 2.4 **RED** `CombatEngineTest`: `intentPattern` advance is NOT frozen while a window is
      pending — the enemy still plays its other scripted steps (ATTACK/BUFF/etc.) on schedule
      (design D3, non-negotiable: no enemy damage-output change).
- [ ] 2.5 **RED** `CombatEngineTest`: a second FORECLOSE announced while a window is already open
      is ignored — no re-arm, no counter reset/extension (design D5).
- [ ] 2.6 **RED** `CombatEngineTest`: `cancelThreshold = 0` (field default) reduces exactly to
      today's snapshot behavior, only delayed by the window — regression pin for the proposal §10
      rollback claim (design Testing Strategy).
- [ ] 2.7 **RED** `CombatEngineTest`: an enemy that dies while its window is pending drops the
      window cleanly (no resolve-on-dead-enemy crash or phantom seizure).
- [ ] 2.8 **GREEN** `app/src/main/java/com/debtsdecks/core/enemies/EnemyInstance.kt`: add
      `openForecloseWindow(intent, turns)`, `tickForecloseWindow(debt): ForecloseVerdict?`,
      `forecloseWindowTurnsLeft`, `forecloseCancelThreshold` per design's Interfaces/Contracts
      block (private `turnsLeft`, sticky `escaped`, captured `Intent` — no map keyed by
      `enemy.id`/UUID, per design D1).
- [ ] 2.9 **GREEN** `app/src/main/java/com/debtsdecks/core/combat/CombatEngine.kt`: FORECLOSE
      branch (`:281–295`) stops resolving on announcement and only arms the window
      (`openForecloseWindow`); add `private const val FORECLOSE_WINDOW_TURNS = 3`; add a new
      per-enemy window tick in `endPlayerTurn()`, before the existing enemy loop, after the
      player's action resolves (design D4/Data Flow). Confirm the HEDGE branch (`:296–304`) is
      byte-for-byte untouched (non-negotiable).
- [ ] 2.10 **GREEN** `app/src/main/java/com/debtsdecks/core/model/EnemyState.kt`: add the two
      defaulted mirror fields (`forecloseWindowTurnsLeft`, `forecloseCancelThreshold`, design D9)
      so the measurement fixture can read window state without inferring from `intentType`.
- [ ] 2.11 Run `HarnessDeterminismTest` and confirm it stays green unmodified — proves the window
      state (private vars, no UUID-keyed map) introduces no new iteration-order drift (design D1
      rationale, Testing Strategy row "Determinism").

## Phase 3: Measurement fixture — `WindowRespondingPolicy`

- [ ] 3.1 **RED** a test asserting the new fixture plays a repay/wipe card whenever
      `forecloseWindowTurnsLeft > 0 && debt >= forecloseCancelThreshold` (balance-measurement spec,
      "Fixture exploits the window to cancel a seizure").
- [ ] 3.2 **GREEN** create `app/src/test/java/com/debtsdecks/core/simulation/WindowRespondingPolicy.kt`
      as a fresh `object : RunPolicy` (NOT a variant or new method on the spent
      `RespondingPolicy.kt` — design's explicit instruction; its 13-variant KDoc history stays
      untouched and unre-attributed). It may copy reward/attack logic from `RespondingPolicy` but
      must not modify that file.
- [ ] 3.3 Add `VerbControl.withForecloseCancelThreshold(enemies, value): List<EnemyDefinition>` to
      `app/src/test/java/com/debtsdecks/core/simulation/VerbControl.kt`, returning `def.copy(...)`
      over the FORECLOSE step's `cancelThreshold`, mirroring the existing `verbsOffControl` pattern
      (design D8) — this is what lets Phase 4's sweep run with zero further code/asset edits.

## Phase 4: `ForecloseControlMeasureTest` re-derivation (not loosening)

- [ ] 4.1 Re-derive the `bailiff` fixture's expected seizure turn/count from the new window
      arithmetic: FORECLOSE announces on the fixture's first turn (debt starts at 6, compounds by
      ceil(15%)/turn), the window now arms then and ticks 3 turns before resolving, instead of
      resolving on the single announcement turn. Compute the exact turn the window closes and
      whether `debt >= param` (27) holds at that point under `EndTurnOnlyPolicy` — do NOT keep
      `assertEquals(1, ...)` and turn 9 by assumption; write down the recomputed number with its
      arithmetic in a code comment, matching the file's existing documentation style (design Open
      Questions, third item — explicit "re-derive, not loosen").
- [ ] 4.2 Update `ForecloseControlMeasureTest.kt`'s assertion(s) to the re-derived expected count
      and (if changed) turn, keeping `outcome == RunOutcome.DEFEAT` as the still-true invariant that
      the seizure ends the fixture run.

## Phase 5: Threshold sweep — the exit-criterion measurement (open, not pre-decided)

- [ ] 5.1 Using `VerbControl.withForecloseCancelThreshold`, define a candidate ladder for
      `cancelThreshold` bounded below by `param`'s neighborhood (27, matching today's threshold as
      the natural floor) and above by `leverageBandHigh` (= 45, per `HarnessBands` /
      `DebtConfig.EXECUTION_THRESHOLD = 50` × `LEVERAGE_BAND_HIGH_RATIO = 0.90` — beyond this every
      run escapes and the verb goes inert per design Open Questions). Suggested ladder: 27, 30, 33,
      36, 39, 42, 45 — adjust granularity only if early results show a knee inside this range.
- [ ] 5.2 For each candidate value, run the single measurement pass —
      `./gradlew :app:testDebugUnitTest --tests '*IntentVerbsE1Test' --tests '*ForecloseControlMeasureTest' --tests '*RunSimulationHarnessTest'`
      — and record from the same run: the E1 response gap (200 seeds, `WindowRespondingPolicy` vs
      ignoring), both policies' absolute win rates, `forecloseSeizureCount` per policy, and the E2
      numbers (`greedy.winRate`, both policies' `avgPeakDebt`) — per proposal §7/§8 and
      balance-measurement spec's "Single-Pass Measurement Gate".
- [ ] 5.3 For each candidate, evaluate the exit criterion (proposal §8) as pass/fail: response gap
      ≥ 10pp AND E2 stays green in the same run (`greedy.winRate in [0.35, 0.55]`, both policies'
      `avgPeakDebt in [25, 45)`, neither policy ≥ 70%) AND the numbers + exact command are recorded.
      A candidate under 10pp is a valid, complete, recorded fail — never grounds to weaken
      `IntentVerbsE1Test`'s `responseGap >= -5.0` floor.
- [ ] 5.4 Pick the winning `cancelThreshold` value only from the recorded sweep data (mirroring how
      `param: 27` was originally reached by sweep, per proposal §4 decision 2/§8). If no candidate
      in the ladder passes, this is the change's real, complete outcome — write it down and stop
      (proposal §8 "Fail" clause); do not manufacture a pass.
- [ ] 5.5 Set the `loan_shark` FORECLOSE step's `cancelThreshold` in
      `app/src/main/assets/enemies/all.json` to the winning value (or leave the seeded value from
      1.3 with an explicit recorded rationale if the sweep found no better candidate but the seeded
      value itself passes).
- [ ] 5.6 Append the sweep table (all candidates, not only the winner), both win rates, seizure
      counts, E2 numbers, and the exact gradle command to `docs/BALANCE-BASELINE.md`, following the
      existing FV.E1 section format used by the three prior spent levers.

## Phase 6: Difficulty-floor check (open risk, must be checked — not resolved by relaxing the floor)

- [ ] 6.1 From the same Phase 5 measurement run(s), read `IntentVerbsE1Test`'s
      `weightResponding`/`weightIgnoring` output and confirm both stay at or above their existing
      floors (`>= 20.0` / `>= 15.0`). Design's Open Questions flags this as a real risk of the
      window mechanic buying the E1 gap as a side effect.
- [ ] 6.2 If a floor breach occurs at the winning (or any passing) candidate, record it as a fail
      for that candidate in the Phase 5 sweep table — do NOT relax `weightResponding`/
      `weightIgnoring` floors to accommodate it. If every passing candidate breaches a floor, the
      change has no valid winner and Phase 5.4's "no candidate passes" outcome applies.

## Phase 7: Non-negotiables verification (final gate, before delivery)

- [ ] 7.1 Run `EnemyTierRegressionTest` and confirm it is green and unmodified — mechanical proof
      that no enemy HP or damage value changed anywhere (proposal §5, design D3/D6).
- [ ] 7.2 Diff `CombatEngine.kt` lines `:296–304` (HEDGE branch) against the pre-change version and
      confirm zero bytes changed (proposal §5/§4.4, spec constraint).
- [ ] 7.3 Diff `HarnessBands.kt` and `DebtConfig.kt` against the pre-change version and confirm
      `HarnessBands` ratios and `DebtConfig.EXECUTION_THRESHOLD` are unmoved (proposal §5/§9).
- [ ] 7.4 Confirm `IntentVerbsE1Test`'s `responseGap >= -5.0` assertion is byte-identical to its
      pre-change form — the floor was not further weakened anywhere in this change (proposal §5,
      spec "Gap under 10pp is a valid, recorded outcome").
- [ ] 7.5 Confirm the winning Phase 5 measurement run reads the E1 gap and the E2 bands from the
      *same* gradle invocation's output, never a separately re-run or on-paper number (proposal §7,
      balance-measurement spec "Single-Pass Measurement Gate"). Re-run once if the recorded numbers
      came from two separate invocations.
- [ ] 7.6 Run `HarnessDeterminismTest` and `EnemyTierRegressionTest` one final time alongside the
      three measurement tests, all in the single command from 5.2, and paste that final invocation
      and its full pass/fail summary into the PR description.

## Phase 8: Deliver

- [ ] 8.1 Conventional commit(s) on `feat/fv-verbs-foreclose-hedge` (PR #22): `feat(combat):`
      for the engine/data change, `docs(balance):` for the `BALANCE-BASELINE.md` sweep record.
- [ ] 8.2 PR body carries: the sweep table (Phase 5.6), the exit-criterion verdict (pass with
      winning value, or fail with the recorded number), and the Phase 7 non-negotiables checklist
      with each item's verification command.
- [ ] 8.3 Do not self-close. Per the standing closing/verification protocol, a later pass with no
      memory of this implementation re-runs Phase 7's commands against the merged state before
      marking FV.E1 resolved.
