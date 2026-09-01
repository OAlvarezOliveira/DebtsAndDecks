# Leverage Archetype Specification

## Purpose

Debt as fuel for LEVERAGE cards only, with band-capped payoff killing EXECUTION-1 parking exploit. Divisor drift unified.

## Requirements

### Requirement: Band-Capped Leverage Payoff

The system SHALL compute payoff with diminishing returns above debt 40. Below cap: `floor(debt / N)`. Above: `floor(40 / N) + floor((debt - 40) / M)` where M > N.

#### Scenario: Linear below cap

- GIVEN debt = 30, N = 2
- WHEN `debt_payoff` resolves
- THEN payoff = floor(30/2) = 15

#### Scenario: Diminishing above cap

- GIVEN debt = 50, N = 2, M = 5
- WHEN `debt_payoff` resolves
- THEN payoff = floor(40/2) + floor(10/5) = 22 (vs 25 uncapped)

#### Scenario: False-positive trap — EXECUTION-1 parking

- GIVEN a sim parks debt at 49, plays `debt_payoff` every turn
- THEN win rate MUST NOT exceed 70%. If it does, band cap or M divisor is wrong and the parking exploit survives.

### Requirement: Divisor Drift Unification

The system SHALL NOT use hardcoded magic numbers for debt scaling where a named `DebtConfig` constant exists. All `debt_scaling` formulas SHALL reference the matching config constant.

#### Scenario: Named constants only

- GIVEN a `debt_scaling` SKILL resolves
- WHEN computing scaled value
- THEN divisor MUST be `DebtConfig.DEBT_SCALING_ATTACK_DIVISOR`, NOT a magic number

#### Scenario: False-positive trap

- GIVEN a test passes with hardcoded `/10` in CardResolver
- THEN the test has not caught the drift — numeric equality does not satisfy the named-constant requirement.

### Requirement: LEVERAGE Card Roles

The system SHALL provide ≥3 distinct LEVERAGE card roles in the reward pool: (1) debt accelerator, (2) cash-out (`debt_payoff`/`execution_damage`), (3) draw/engine (`debt_draw`).

#### Scenario: Each role covered

- GIVEN card registry after changes
- WHEN filtering LEVERAGE cards by role
- THEN at least one card exists per role

#### Scenario: False-positive trap

- GIVEN a test counts 5 cards with `debt_scaling` tag and declares LEVERAGE complete
- THEN the test is wrong — tag count ≠ role coverage. 5 copies of the same card is not 3 distinct roles.
