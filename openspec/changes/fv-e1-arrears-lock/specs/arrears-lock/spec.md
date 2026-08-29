# Arrears Lock Specification

## Purpose

Defines the "En Mora" hard-lock state on `CombatState`: a survivable-but-escapable
replacement for instant Execution at high debt, asymmetric by construction (only a
policy that actively responds can escape it).

## Requirements

### Requirement: Lock Entry
The system MUST arm `inArrears` on `CombatState` when `state.debt >=
ARREARS_THRESHOLD` (40) and `arrearsUsedThisCombat` is `false`. Entry MUST
replace the prior instant-defeat behavior of the single `EXECUTION_THRESHOLD`
constant. `ARREARS_THRESHOLD` is a new, purely behavioral constant split off
from the old `EXECUTION_THRESHOLD`; the harness-facing half of that constant
is renamed `DEBT_SCALE_ANCHOR` and stays at 50, unchanged — see the
`debt-economy` delta's "Split Constant" requirement for the full call-site
breakdown. `HarnessBands.kt`'s E2 band derivation, and the borrow ceilings in
`ScriptedPolicy`/`LeveragePolicy`, read `DEBT_SCALE_ANCHOR`, not
`ARREARS_THRESHOLD`.

#### Scenario: Debt crosses 40 for the first time this combat
- GIVEN `state.debt` is below 40 and `arrearsUsedThisCombat` is `false`
- WHEN debt-increasing effects push `state.debt >= 40`
- THEN `inArrears` becomes `true` AND `arrearsUsedThisCombat` becomes `true`
- AND combat does not end

### Requirement: Once-Per-Combat Consumability
The lock MUST NOT re-arm after its single charge is spent in the current combat,
even if `state.debt` crosses 40 again later in the same combat.

#### Scenario: Second crossing after charge spent
- GIVEN `arrearsUsedThisCombat` is `true` and the lock was already cleared once
- WHEN `state.debt` reaches `>= 40` again in the same combat
- THEN `inArrears` MUST remain `false`

### Requirement: Passive Interest Freeze
While `inArrears` is `true`, the system MUST suspend passive interest accrual
(`applyInterest`). Active, card-applied debt increases MUST still be allowed.

#### Scenario: Interest tick while locked
- GIVEN `inArrears` is `true`
- WHEN the passive interest tick would normally fire
- THEN `state.debt` MUST NOT increase from that tick
- AND a card that applies debt directly still increases `state.debt`

### Requirement: Lock Exit
The lock MUST clear (`inArrears = false`) only when `state.debt == 0` naturally
or a `wipe_debt`-tagged card resolves. It MUST NOT clear on a debt dip that stays
above zero. Playing a `wipe_debt` card while not locked MUST NOT consume the
once-per-combat charge.

#### Scenario: Escape via wipe_debt card
- GIVEN `inArrears` is `true`
- WHEN a `wipe_debt`-tagged card resolves
- THEN `inArrears` becomes `false`

#### Scenario: Debt dip does not clear the lock
- GIVEN `inArrears` is `true` and `state.debt` is 40
- WHEN `state.debt` drops to 10 without reaching 0 and without a `wipe_debt` card
- THEN `inArrears` MUST remain `true`

### Requirement: Gatillo B (Locked-At-Combat-End Defeat)
If `COMBAT_END` is reached with `inArrears == true`, the system MUST resolve the
run as DEFEAT regardless of enemy HP. Victory requires enemy HP == 0 AND
`inArrears == false`.

#### Scenario: Enemy dies while player still locked
- GIVEN enemy HP reaches 0 and `inArrears` is `true`
- WHEN `COMBAT_END` is evaluated
- THEN the run resolves as DEFEAT

#### Scenario: Enemy dies after escape
- GIVEN enemy HP reaches 0 and `inArrears` is `false`
- WHEN `COMBAT_END` is evaluated
- THEN the run resolves as VICTORY

### Requirement: Harness Policy Awareness
`ScriptedPolicy` and `LeveragePolicy` MUST remain blind to lock state (no new
branch). `RespondingPolicy` MUST prioritize a `wipe_debt` card when locked.
`HarnessDeterminismTest` MUST stay green (no UUID-keyed maps, no
iteration-order dependence).

#### Scenario: Responding policy escapes when possible
- GIVEN `RespondingPolicy` is locked and holds a `wipe_debt` card
- WHEN it selects its next action
- THEN it MUST prioritize playing that card over other options

### Requirement: Empirical Balance Validation
The joint effect of entry threshold 40 and Gatillo B MUST be measured, not
assumed, over 200 seeds before the change is accepted.

#### Scenario: Re-baseline confirms E1 differential
- GIVEN the lock, threshold 40, and Gatillo B are implemented
- WHEN `IntentVerbsE1Test` runs over 200 seeds
- THEN the response-gap MUST be >= 10.0pp

#### Scenario: E2 bands stay green under the double tightening
- GIVEN the lock, threshold 40, and Gatillo B are implemented
- WHEN `RunSimulationHarnessTest` runs over 200 seeds
- THEN greedy win rate MUST fall in `[0.35,0.55]` AND `avgPeakDebt` MUST fall
  in `[25,45)` AND neither policy reaches >= 70% win rate
- AND lock fire-rate MUST be > 0 across the 200 seeds

## Open Questions (Non-Blocking, Carried From Proposal)

These do not block spec acceptance; design/tasks MUST address them explicitly:

1. After the once-per-combat charge is spent and the player escapes, does a
   second `debt >= 40` crossing restore instant Execution, or stay dormant for
   the rest of combat? (Current requirement above assumes dormant — confirm.)
2. Does frozen passive interest resume immediately on exit, or skip one tick?
3. Is any mid-combat save/load path affected? (`CombatState` is combat-scoped —
   likely none, confirm.)
4. Does the lock need a visible UI indicator plus EN/ES i18n keys in this
   slice, or is it headless-sim-only for FV.E1 validation?
