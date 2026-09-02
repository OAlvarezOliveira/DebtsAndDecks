# Delta for Combat Engine — FORECLOSE Resolution

No prior spec file exists for this domain; written as ADDED requirements (first spec, not a
diff). This is the first proposal in the FV.E1 chain to change `CombatEngine.kt` engine logic
rather than only data (`enemies/all.json`, `cards/all.json`) — treat every requirement below as
higher blast-radius than the three prior (data-only) siblings.

## Prerequisite Finding (verified against source, not assumed)

FORECLOSE is **already telegraphed one turn ahead**: `EnemyInstance.currentIntent()` is exposed
through `CombatState`/`EnemyState` during `TurnPhase.PLAYER_ACTION` (the player's own turn), and
the same intent only resolves later, inside `endPlayerTurn()` → `TurnPhase.ENEMY_ACTION`
(`CombatEngine.kt:281-295`). So the player already has exactly one full turn of lead before any
FORECLOSE resolution today. This satisfies proposal §4 decision point 5 as a confirmed
prerequisite, not an open design question — no `EnemyAI`/scheduling change is required to make an
N-turn window meaningful.

## ADDED Requirements

### Requirement: FORECLOSE Opens a Pending-Seizure Window
When a FORECLOSE intent is announced (becomes the enemy's `currentIntent()`), the system MUST
NOT resolve the seizure on that same announcement; it MUST instead open a pending window of
exactly N = 3 player turns (the turn of announcement plus 2 more) during which the seizure is
deferred, replacing today's single-snapshot `debt >= intent.param` check at resolution.

#### Scenario: Window opens on announcement
- GIVEN a FORECLOSE intent becomes the enemy's current intent
- WHEN the player's turn showing that intent begins
- THEN the engine MUST mark a FORECLOSE seizure as pending with 3 turns remaining
- AND MUST NOT apply `player.takeDamage(player.hp)` in that same enemy-action phase

### Requirement: Cancel Threshold Repay Check
While a FORECLOSE seizure is pending, the system MUST check the player's `CombatState.debt`
against the FORECLOSE intent's new cancel-threshold field at each turn boundary within the
window and MUST cancel the pending seizure (no damage, no `forecloseSeizureCount` increment) the
first time `debt` is below that threshold.

#### Scenario: Player repays inside the window
- GIVEN a FORECLOSE seizure is pending with turns remaining > 0
- WHEN the player's `debt` drops below the intent's cancel-threshold field before the window closes
- THEN the pending seizure MUST be cancelled
- AND the enemy MUST resume its normal `intentPattern` advance on its next turn

### Requirement: Uncancelled Window Expiry Stays Run-Ending
If the window closes (3 turns elapsed) with `debt` still at or above the cancel threshold, the
system MUST run-end exactly as today's snapshot check does: `forecloseSeizureCount` increments
and `player.takeDamage(player.hp)` fires. (Owner decision 2026-08-29 §4.3: unchanged outcome,
only the trigger axis changes.)

#### Scenario: Window expires uncancelled
- GIVEN a FORECLOSE seizure is pending with 0 turns remaining
- WHEN the window closes and `debt` is still >= the cancel threshold
- THEN the engine MUST increment `forecloseSeizureCount` and apply outright seizure damage

## Constraints (non-negotiable, inherited from proposal §5)

- MUST NOT change any enemy HP or damage value (`loan_shark`/`collector`/`thug`, any
  `ATTACK`/`MULTI_ATTACK`/`BUFF`/`DEBUFF`/`LEVY` value).
- MUST NOT touch the HEDGE branch (`CombatEngine.kt:296-304`) — proposal §4.4, HEDGE unchanged.
- MUST NOT move `HarnessBands` ratios or `DebtConfig.EXECUTION_THRESHOLD` to pass this change.
- `RunSimulationHarnessTest` (E2 band assertions) MUST stay green in the same measurement run as
  the E1 gap read — never asserted separately, never on paper.
- MUST NOT manufacture an E1 pass by further weakening `IntentVerbsE1Test`'s existing
  `responseGap >= -5.0` assertion.
