# Design: "En Mora" Arrears Hard-Lock (FV.E1)

## Technical Approach

Two booleans on `CombatState` (`inArrears`, `arrearsUsedThisCombat`), owned by `CombatEngine`
as private vars and reset in `startCombat`. Arming replaces the `endCombat(false)` return in
`addDebt`. Exit is a single invariant checked after every debt mutation. Outcome resolution
(Gatillo B) lives in `RunManager.refresh()`, which is where the run outcome is actually decided
— `endCombat(victory)` does **not** persist its flag; `refresh()` re-derives the outcome from
`state.enemies` (`RunManager.kt:167-196`).

## Architecture Decisions

| # | Options | Decision & rationale |
|---|---|---|
| D1 | (a) `EXECUTION_THRESHOLD = 40`; (b) split into `ARREARS_THRESHOLD = 40` + rename the old constant to `DEBT_SCALE_ANCHOR = 50` | **(b)**. `HarnessBands.kt:52-60` derives every E2 band as a *ratio* of that constant. (a) silently moves `avgPeakDebt` to `[20,36)` and `leverageTarget` to 28, contradicting the proposal's own "Out: changing E2 bands" and its absolute success criterion `[25,45)`, and destroying comparability with `docs/BALANCE-BASELINE.md`. **Requires a spec amendment** (see Risks). |
| D2 | Arm on `addDebt` only vs. also on the `beginTurn` interest tick | **`addDebt` only** — matches the proposal's named site and preserves Decision B. Pre-declared **tuning knob #1** if fire-rate measures 0. |
| D3 | Repoint the blind policies' borrow ceiling to 40, or keep it on the anchor (50) | **Keep on `DEBT_SCALE_ANCHOR`.** Pointing it at 40 makes `ScriptedPolicy`/`LeveragePolicy` lock-*avoidant*, reproducing the prior attempt's 0/200 fire-rate disqualifier and destroying the E1 signal. |
| D4 | Two exit rules (wipe tag + natural zero) vs. one invariant | **One**: `debt == 0 ⇒ inArrears = false`, evaluated after `RepayDebt`/`WipeDebt`/any debt write. `Effect.WipeDebt` already sets `debt = 0` (`CombatEngine.kt:446`), so the tag case is subsumed — no tag inspection in the engine. |
| D5 | Gatillo B in `CombatEngine.endCombat` vs. `RunManager.refresh` | **`RunManager.refresh`**, inserted after the `allEnemiesDead` check and *before* garnishment (`:177-189`). The engine has no persisted victory flag to override. |
| D6 | New HUD indicator vs. log-only | **Log-only** for this validation slice (Intent is harness measurement). But `log.debt_execution{,_levy}` now state a falsehood, so they become `log.arrears_locked{,_levy}` EN/ES, and `CombatRenderer.kt:365,387` repoints its red debt threshold to `ARREARS_THRESHOLD`. No new art, no HUD badge. |

## Data Flow

    addDebt(amount) ──► debt += amount (cap 200)
         │
         └─► debt >= ARREARS_THRESHOLD && !arrearsUsedThisCombat
                 └─► inArrears = true; arrearsUsedThisCombat = true; arrearsArmedCount++
                     (was: endCombat(victory=false))

    beginTurn() ──► if (!inArrears) debt = applyInterest(debt)      // freeze
    applyEffects(RepayDebt|WipeDebt) ──► if (debt == 0) inArrears = false

    RunManager.refresh() @ COMBAT_END
         hp<=0 ─► DEFEAT │ !allEnemiesDead ─► DEFEAT
         allEnemiesDead && state.inArrears ─► DEFEAT   ◄── Gatillo B (new)
         else ─► garnish ─► NODE | VICTORY

## Harness Policy Contract

| Policy | Locked | Unlocked | Expected effect |
|---|---|---|---|
| `ScriptedPolicy` | **no branch** (blind) | unchanged; ceiling `debtAfter >= DEBT_SCALE_ANCHOR` (`:75`) | Borrows through 40 freely → **primary fire-rate source**; its locked wins become Gatillo-B defeats → main E2 win-rate risk |
| `LeveragePolicy` | **no branch** (blind) | unchanged; ceiling `> DEBT_SCALE_ANCHOR` (`:95`) | Parks at `leverageTarget` 35 and stops borrowing above it → arms mainly via LEVY / `add_debt` cards → **lowest fire-rate**; watch for 0 |
| `RespondingPolicy` | **one new branch, first in `chooseAction`**: if `state.inArrears`, reuse the existing HP-aware cheapest-playable `wipe_debt` selection (`:75-86`), else fall through | unchanged (FORECLOSE branch, leverage loop) | The only escape-capable policy → the E1 differential |

`chooseReward` is **unchanged** in all three: the `wipe_debt` draft bump measured **-7.5pp** and
stays reverted (`RespondingPolicy.kt:39-44`).

Instrumentation mirrors the shipped `forecloseSeizureCount` pattern: `CombatEngine.arrearsArmedCount`
(`Int`, `private set`) → `SimulationResult.arrearsArmed` → report. Ints only, no UUID-keyed maps,
no iteration-order dependence — `HarnessDeterminismTest` untouched.

## File Changes

| File | Action | Description |
|---|---|---|
| `core/model/CombatState.kt` | Modify | `inArrears`, `arrearsUsedThisCombat` appended with `= false` defaults (all existing constructor call sites keep compiling) |
| `core/combat/DebtConfig.kt` | Modify | Add `ARREARS_THRESHOLD = 40`; rename `EXECUTION_THRESHOLD` → `DEBT_SCALE_ANCHOR` (value 50, re-KDoc'd as harness scale anchor only) |
| `core/combat/CombatEngine.kt` | Modify | `addDebt` arms instead of returning `true`; drop the `levyExecution`/`endCombat` defeat paths at `:211,:278,:455`; freeze interest at `:361`; exit check after `RepayDebt`/`WipeDebt`; `arrearsArmedCount`; reset in `startCombat`; expose both flags in `getState()` |
| `core/combat/RunManager.kt` | Modify | Gatillo B in `refresh()`; `takeLoan` guard stays on `DEBT_SCALE_ANCHOR` (node loans never route through `addDebt`, so they cannot arm) |
| `core/combat/resolution/CardResolver.kt` | **Unchanged** | `wipe_debt` → `Effect.WipeDebt` already exists (`:287`); D4 removes any need to touch it |
| `gdx/render/CombatRenderer.kt` | Modify | Red-debt threshold → `ARREARS_THRESHOLD`; loan affordance → `DEBT_SCALE_ANCHOR` |
| `assets/i18n/strings{,_es}.properties` | Modify | `log.debt_execution{,_levy}` → `log.arrears_locked{,_levy}` (EN/ES thematic, neutral Spanish) |
| `test/simulation/RespondingPolicy.kt` | Modify | One lock branch (above the FORECLOSE branch) |
| `test/simulation/{Scripted,Leverage}Policy.kt` | Modify | Mechanical rename only — **no lock branch** |
| `test/simulation/{RunSimulator,SimulationReport}.kt` | Modify | Thread `arrearsArmed` |
| `test/simulation/RunObservationTest.kt` | Modify | `DefeatCause.EXECUTION` → `ARREARS`; classifier reads the lock, not `endDebt` |
| `test/combat/DebtConfigTest.kt` | Modify | `assertEquals(40, ARREARS_THRESHOLD)`; anchor invariant vs. `BREAK_THRESHOLD` |
| `docs/BALANCE-BASELINE.md`, `docs/GDD.md`, `openspec/VERIFICATION-CHECKLIST.md` | Modify | Append pre/post numbers; update live constant tables. Historical `docs/ANALISIS-*` files are records — **not** rewritten |

## Interfaces

```kotlin
// DebtConfig
const val ARREARS_THRESHOLD: Int = 40   // behavioral: arms the En Mora lock
const val DEBT_SCALE_ANCHOR: Int = 50   // HarnessBands ratio anchor + blind-policy borrow ceiling

// CombatEngine (private)
private fun armArrearsIfCrossed()   // called at the tail of addDebt
private fun clearArrearsIfEscaped() // inArrears = inArrears && debt > 0
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Entry at exactly 40 (`>=`, not the old `>`), no entry at 39, no re-arm after charge spent, dip to 1 keeps the lock, `debt == 0` clears it, `wipe_debt` while unlocked does not consume the charge | `CombatEngineTest` with `Random(seed)`, concrete asserts |
| Unit | Interest frozen while locked; unfrozen immediately on exit (vacuous by construction — both exits leave `debt == 0` and `applyInterest(0)` is a no-op) | `DebtConfigTest` / `CombatEngineTest` |
| Integration | Gatillo B: enemy at 0 HP + `inArrears` → `Phase.DEFEAT`; escape-then-kill → `Phase.VICTORY` | `RunManagerTest` |
| Harness | **Baseline re-run BEFORE the change** (200 seeds, both tests, recorded in `BALANCE-BASELINE.md`), then post-change: E1 gap ≥ 10.0pp; E2 win rate `[0.35,0.55]`, `avgPeakDebt` `[25,45)`, neither policy ≥ 70%; **fire-rate > 0 reported per policy** | `IntentVerbsE1Test`, `RunSimulationHarnessTest` |
| Harness | Joint diagnostic sweep: `ARREARS_THRESHOLD ∈ {40,45}` × Gatillo B `{on,off}` as a 2×2, to attribute any band exit to threshold vs. Gatillo B. 40/on is owner-locked — the sweep informs, it does not re-decide | Local sweep, numbers appended to `BALANCE-BASELINE.md` pass or fail |
| Determinism | `HarnessDeterminismTest` green | Unchanged; new state is two `Boolean`s + one `Int` |

Strict TDD: RED → GREEN → TRIANGULATE → REFACTOR per unit. Gradle via the cached binary, not `./gradlew`.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary.

## Migration / Rollout

No migration. No persisted state: a `Preferences|serialize|saveGame|SaveState` grep over
`app/src/main/java` returns **zero** files, so **proposal Q4 is answered — no save/load path
exists to break**. Rollback is a single-branch revert on `fv-e1-leverage`.

## Resolved Open Questions

- **Q2 — second crossing after the charge is spent**: the player is **immune for the rest of that
  combat**. Instant Execution is deleted outright; there is no code path left to restore. Confirmed
  against binding constraint 3 and the spec's Once-Per-Combat requirement.
- **Q3 — interest resume**: **immediate, no skipped tick, no extra state**. Both exits leave
  `debt == 0`, and `applyInterest` already no-ops at `debt <= 0` (`DebtConfig.kt:84`), so the
  question is vacuous by construction.
- **Q4 — save/load**: unaffected; no persistence layer exists (grep above).
- **Q5 — UI/i18n**: deferred except the two log keys, which are engine-owned, not UI (D6).

## Open Questions

- [ ] D1 contradicts `specs/debt-economy/spec.md` ("every reference MUST use 40"). Spec amendment
      required before apply, or the E2 bands move and the measurement becomes uninterpretable.
