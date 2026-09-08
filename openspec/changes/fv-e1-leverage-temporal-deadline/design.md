# Design: FORECLOSE Temporal Deadline

## Technical Approach

The pending window is **per-`EnemyInstance` mutable state**, not a map and not a `CombatState` field.
FORECLOSE's `when` branch stops resolving and only *arms*; resolution moves to a new per-turn tick
that runs for every live enemy in `endPlayerTurn()`, independent of the current intent. `IntentStep`
gains one field (`cancelThreshold`), captured into the window at announcement because the pattern has
advanced by the time the window closes. `EnemyState` re-exports the window so the measurement fixture
can see it.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| D1 | Window state = 3 private vars on `EnemyInstance` (`turnsLeft`, `escaped`, captured `Intent`) | `Map<enemyId, Window>` on `CombatEngine`; a `CombatState` field | `EnemyInstance` is built per combat in `startCombat`, so scope and teardown are free. `enemy.id` is a random **UUID** — any map keyed by it risks the exact iteration-order nondeterminism `HarnessDeterminismTest` exists to catch. `CombatState` is a rebuilt snapshot and cannot hold state. Mirrors `patternIndex`/`hasEnraged`. |
| D2 | **Two thresholds.** `cancelThreshold` = early-escape bar observed *during* the window; `param` = the level compared *at close* (today's check, delayed) | Single threshold governing both | Resolves the tension between `enemy-intent-data` spec ("`param` continues to drive the expiry-check axis") and `combat-engine` spec R3. Keeps `param: 27` and the standing-fee branch load-bearing, and makes `cancelThreshold = 0` (the field default) reduce **exactly** to today's semantics, delayed — a safe floor for the sweep. |
| D3 | `intentPattern` advance is **untouched**; the enemy plays ATTACK/BUFF normally while a seizure is pending | Freezing the enemy on FORECLOSE for 3 turns | Freezing deletes two ATTACK-9 turns per cycle — a de facto enemy damage change (proposal §5 non-negotiable) and a guaranteed E2 break. Spec R2's "resume its normal advance" is read as "advance is unaffected". |
| D4 | Escape is observed **once per turn**, in `endPlayerTurn()` before the enemy loop (after the player has acted, before `beginTurn()`'s interest tick); sticky flag | Continuous observation inside `applyEffects` on every `RepayDebt`/`WipeDebt` | 3 fixed observation points per window; no new engine surface, no ordering subtlety with interest, fully deterministic. Net end-of-turn position is the honest read of "did the player act inside the window". |
| D5 | A FORECLOSE announced while a window is already open is **ignored** (no re-arm, no extension) | Reset the counter on re-announce | With a FORECLOSE-every-turn pattern (the `bailiff` fixture) resetting means the window never closes and the verb never fires. Ignoring is total and deterministic. |
| D6 | The whole outcome (cancel / fee / seizure) resolves at close — one outcome per FORECLOSE occurrence | Fee at announce, seizure at close | Preserves today's either/or economics and total damage per occurrence; only displaces the fee by 2 turns. That displacement is an E2 risk to *measure*, not to design around. |
| D7 | `N = 3` is `private const val FORECLOSE_WINDOW_TURNS = 3` in `CombatEngine` | Second JSON field; `DebtConfig` constant | Owner decision 7 authorizes **one** new data field (the threshold). N is a fixed owner decision, not a tunable; `DebtConfig` was explicitly steered away from. |
| D8 | Sweep candidate thresholds **in-test**, via a new `VerbControl.withForecloseCancelThreshold(enemies, value)` returning `def.copy(intentPattern = ...)` | Editing `all.json` between runs | Reuses the existing `verbsOffControl` house pattern. One measurement run can sweep every candidate with **zero** code or asset edits; the winning value is written to `all.json` once, after. |
| D9 | Window exposed on `EnemyState` as `forecloseWindowTurnsLeft` / `forecloseCancelThreshold` (both defaulted) | Policy infers from `intentType == FORECLOSE` | By turn 2 of the window the displayed intent is no longer FORECLOSE, so today's `RespondingPolicy` inference is blind. The fixture needs both numbers. |

## Data Flow

    PLAYER_ACTION ── repay/wipe ──→ debt↓
          │
    endPlayerTurn() ─ per live enemy ─┐
          │  when(intent): FORECLOSE → openForecloseWindow(intent, 3)   [arm only, no damage]
          │  tickForecloseWindow(debt):
          │      if debt < window.cancelThreshold → escaped = true
          │      turnsLeft--
          │      turnsLeft == 0 → escaped     → cancelled (no damage, no count)
          │                       debt>=param → seizureCount++, takeDamage(hp)
          │                       else        → standing fee (intent.damage)
          └─ ai.executeIntent() → advanceIntent()   [unchanged]

## File Changes

| File | Action | Description |
|---|---|---|
| `core/enemies/EnemyDefinition.kt` | Modify | `IntentStep.cancelThreshold: Int = 0`; same field on `EnemyInstance.Intent` |
| `core/enemies/EnemyInstance.kt` | Modify | Window vars + `openForecloseWindow` / `tickForecloseWindow` / read-only accessors |
| `core/combat/CombatEngine.kt` | Modify | FORECLOSE branch arms only (`:281–295`); new window tick in the enemy loop; `FORECLOSE_WINDOW_TURNS` |
| `core/model/EnemyState.kt` | Modify | Two defaulted mirror fields (D9) |
| `assets/enemies/all.json` | Modify | `cancelThreshold` on the `loan_shark` FORECLOSE step |
| `test/simulation/VerbControl.kt` | Modify | `withForecloseCancelThreshold` sweep helper (D8) |
| `test/simulation/WindowRespondingPolicy.kt` | **Create** | New minimal fixture — a fresh `object : RunPolicy`, **not** a `RespondingPolicy` variant |
| `test/simulation/IntentVerbsE1Test.kt`, `ForecloseControlMeasureTest.kt` | Modify | Measure the new fixture; seizure counts/timing move |
| `docs/BALANCE-BASELINE.md` | Modify | Sweep table, both win rates, E2 numbers, exact command |

## Interfaces / Contracts

```kotlin
// EnemyInstance
sealed interface ForecloseVerdict { object Cancelled; object Seize; object Fee }   // null while pending
fun openForecloseWindow(intent: Intent, turns: Int)   // no-op if a window is already open (D5)
fun tickForecloseWindow(debt: Int): ForecloseVerdict?
val forecloseWindowTurnsLeft: Int                     // 0 when none
val forecloseCancelThreshold: Int
```

The new measurement fixture (spec `balance-measurement`) is a **new class**, not a parameterization or
a new method on `RespondingPolicy` — the 13-variant measurement history in that file's KDoc is
evidence tied to the level-threshold FORECLOSE and must not be mutated or re-attributed. It reuses
`RespondingPolicy`'s reward/attack logic only by copying the parts it needs, and its response rule is
one line: while `forecloseWindowTurnsLeft > 0 && debt >= forecloseCancelThreshold`, play the cheapest
wipe/repay card.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Arm / cancel / expire / re-announce-ignored / dead-enemy-drops-window | `CombatEngineTest`-style, scripted debt |
| Unit | `cancelThreshold = 0` reduces to today's outcome, delayed | Regression pin for the rollback claim |
| Determinism | No new drift | `HarnessDeterminismTest` unchanged (D1 adds no map/UUID iteration) |
| Measurement | E1 gap + E2 bands + seizures + tier regression | One gradle invocation, all five tests (spec `balance-measurement`) |

Non-negotiables confirmed **not** violated: no enemy HP/damage value changes (D3, D6); HEDGE branch
(`:296–304`) untouched; `HarnessBands` ratios and `DebtConfig.EXECUTION_THRESHOLD` unmoved; E1 and E2
read from the same run; `IntentVerbsE1Test`'s `responseGap >= -5.0` floor unchanged.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary.

## Migration / Rollout

No migration. `EnemyState` carries `@Serializable` but nothing in `app/src/main` calls
`encodeToString`/`decodeFromString` on it — there is no save format, so the window introduces **no
save-compat concern**. Every loader uses `Json { ignoreUnknownKeys = true }`, so even a code-only
revert against the new JSON parses. `git revert` of the single commit restores snapshot FORECLOSE
(27 / fee 9). Rollback shape per proposal §10 holds.

## Open Questions

- [ ] **Difficulty-weight risk (for `sdd-tasks`/apply to measure, not resolve):** a window makes
  FORECLOSE strictly survivable for *both* policies, which pushes down `IntentVerbsE1Test`'s
  `weightResponding >= 20.0` / `weightIgnoring >= 15.0` floors. If the sweep buys E1 at the cost of
  those floors, that is a **fail** to record, not an assertion to relax.
- [ ] **Sweep range** is bounded but not fixed here: below `param` the escape bar is harder than doing
  nothing; at/above the band top (45) every run escapes and the verb dies. `sdd-tasks` picks the
  candidate ladder inside `[param, leverageBandHigh]` and measures it.
- [ ] `ForecloseControlMeasureTest`'s `bailiff` fixture asserts `1` seizure on turn 9; under D5+D6 the
  timing moves. Apply must re-derive the expected count from the fixture's arithmetic, not loosen the
  assertion.
