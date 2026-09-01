# Debt HUD Specification

## Purpose

On-screen indicators during combat: debt level, active archetype, risk counter. All information MUST be actionable — players can act on it.

## Requirements

### Requirement: Debt Level Display

The system SHALL display current debt with visual band markers:
- **Safe**: debt < 22 (`DEBT_BLEED_FLOOR`)
- **Danger**: 22 ≤ debt < 30 (`BREAK_THRESHOLD`)
- **Execution proximity**: 30 ≤ debt < 50 (`EXECUTION_THRESHOLD`)
- **Execution line**: debt ≥ 50 = defeat

#### Scenario: Band reflects current debt

- GIVEN debt = 35
- WHEN HUD renders
- THEN indicator shows "Danger zone" (between 22 and 50, above 30)

#### Scenario: False-positive trap — stale value

- GIVEN debt changes from 20→30 mid-combat (interest tick)
- WHEN HUD renders after tick
- THEN display MUST show 30, not 20. Stale debt gives false confidence — worse than no HUD.

### Requirement: Active Archetype Display

The system SHALL display dominant archetype from `playerArchetype()`, updating when the deck changes.

#### Scenario: Archetype updates after pick

- GIVEN scores: LEVERAGE=3, LIQUIDITY=1, PRESSURE=2
- WHEN player picks LEVERAGE-tagged card
- THEN HUD updates to show LEVERAGE (now dominant)

### Requirement: Risk Counter

The system SHALL display distance to bleed floor (22) and execution line (50). Clear scale (e.g., "15 to execution" or bar from 22 to 50).

#### Scenario: Risk at moderate debt

- GIVEN debt = 35
- WHEN counter renders
- THEN shows: 50 - 35 = 15 points to execution

#### Scenario: Counter actionable

- GIVEN debt = 45, player holds `debt_payoff` card
- THEN risk counter (5 to execution) + repayment value (floor(45/2)=22) = player sees playing this card drops debt to 23

### Requirement: HUD Is Read-Only

The HUD SHALL NOT modify game state, trigger mechanics, or affect combat resolution. Stripping HUD MUST NOT change any combat outcome.

#### Scenario: HUD removal is safe

- GIVEN HUD rendering disabled
- WHEN combat runs with identical seed
- THEN outcome (victory/defeat, final HP, debt) is identical to HUD-enabled run
