# Delta for Balance Measurement — Window-Exploiting Responding Policy

No prior spec file exists for this domain; written as ADDED requirements.

## ADDED Requirements

### Requirement: New Minimal Window-Exploiting Responding Policy Fixture
The system MUST include a new, minimal responding-policy test fixture (source-test scope, not
`RespondingPolicy.kt`'s spent 13-variant lever) that repays debt below the FORECLOSE
cancel-threshold at some point inside the pending window, so `IntentVerbsE1Test` can measure the
gap this mechanic actually produces rather than re-measuring the level-threshold FORECLOSE.

#### Scenario: Fixture exploits the window to cancel a seizure
- GIVEN a FORECLOSE seizure is pending against the window-exploiting policy
- WHEN that policy has at least one turn remaining in the window
- THEN it MUST play a repay-capable card so `debt` drops below the cancel threshold before expiry

### Requirement: Single-Pass Measurement Gate
`IntentVerbsE1Test`, `ForecloseControlMeasureTest`, `RunSimulationHarnessTest`,
`HarnessDeterminismTest`, and `EnemyTierRegressionTest` MUST run in the same measurement pass so
the E1 gap and the E2 bands are read from the same numbers, never asserted separately. The E1 gap
MUST be read from `IntentVerbsE1Test` stdout, not inferred from the suite going green.

#### Scenario: Exit criterion evaluated from one run
- GIVEN the full measurement pass has completed
- WHEN the results are recorded
- THEN `docs/BALANCE-BASELINE.md` MUST contain the E1 gap, both policies' win rates, seizure
  counts, the E2 numbers, and the exact gradle command that produced them

#### Scenario: Gap under 10pp is a valid, recorded outcome
- GIVEN the measured response gap is below 10pp over 200 seeds
- WHEN the result is written up
- THEN the number MUST be recorded as a fail and `IntentVerbsE1Test`'s assertion MUST NOT be
  weakened to manufacture a pass
