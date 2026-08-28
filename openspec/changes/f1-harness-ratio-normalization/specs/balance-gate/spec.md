# Spec delta — capability: `balance-gate`

## ADDED Requirements

### Requirement: R1.1 — Every debt-scale threshold is a named ratio of a named anchor

The simulation harness and its policies SHALL express debt thresholds as a ratio of
`DebtConfig.EXECUTION_THRESHOLD`, declared in one place, with the anchor named at the
declaration site.

#### Scenario: Reading a band
- **WHEN** a contributor reads the leverage band
- **THEN** it reads as a fraction of the execution line (e.g. `0.50..0.90`), not as `25..45`
- **AND** the declaration states which constant it is a fraction of

#### Scenario: A magic debt number survives somewhere
- **WHEN** the test source set is searched for bare debt thresholds in the harness, in
  `LeveragePolicy` and in `NodePolicy`
- **THEN** every remaining literal is either a gold-scale value listed as deferred in the
  proposal, or a deck/turn count, and none of them is a debt level

### Requirement: R1.2 — Normalization is behaviour-preserving

The change SHALL produce identical simulation results for identical seeds, before and after.

#### Scenario: The 200-seed sweep is compared
- **GIVEN** the report printed by `leverage policy comparison sweep` on `develop`
- **WHEN** the same test runs on the F1 branch
- **THEN** win rate, average peak debt, HP at victory and the defeat breakdown are identical
  for both policies
- **AND** any difference, however small, fails the change — it is not a rounding tolerance,
  it is proof the refactor was not a refactor

#### Scenario: The derived thresholds are checked against the historical values
- **GIVEN** `EXECUTION_THRESHOLD = 50`
- **WHEN** the ratios are resolved
- **THEN** the leverage band resolves to exactly `[25.0, 45.0)` and the won-peak floor to
  exactly `25.0`

### Requirement: R1.3 — The simulated players scale with the economy

`LeveragePolicy` and `NodePolicy` SHALL derive their debt thresholds from the same ratio
declarations as the assertions.

#### Scenario: The execution threshold is doubled in a spike
- **GIVEN** a throwaway branch where `EXECUTION_THRESHOLD` is set to 100
- **WHEN** the sweep runs
- **THEN** `LeveragePolicy` borrows toward 70, `NodePolicy` refuses loans that would end
  above 90 and repays below 50
- **AND** no policy behaves as though the execution line were still 50

#### Scenario: A future re-scale lands without touching the policies
- **WHEN** F3 changes the economy's scale
- **THEN** no edit to `LeveragePolicy` or `NodePolicy` is required for them to keep playing
  the same *relative* game

### Requirement: R1.4 — Failures are readable at any scale

The simulation report SHALL express peak debt as both an absolute value and a fraction of the
execution line, and assertion messages SHALL include both.

#### Scenario: The band assertion fails
- **WHEN** average peak debt falls outside the band
- **THEN** the message names the observed absolute value, the observed ratio, and the ratio
  bounds that were violated

### Requirement: R1.5 — Win-rate invariants are preserved verbatim

The win-rate band, the 70% ceiling and the one-sided policy-gap assertion SHALL be carried
across unchanged in both value and direction.

#### Scenario: The policy gap keeps its asymmetry
- **WHEN** the gap assertion is read after F1
- **THEN** it still asserts that the leverage policy may not win more than 5pp *below* greedy
- **AND** it still places no ceiling on the leverage policy winning more

#### Scenario: Someone "tidies" the win band into the ratio object
- **THEN** the values `0.35`, `0.55` and `0.70` are unchanged, because they were already
  dimensionless — relocating them is acceptable, altering them is not
