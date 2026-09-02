# Pressure Archetype Specification

## Purpose

PRESSURE net-new card line: paydown strikes, weak/vulnerable stackers, low-debt escalator, AUDIT-punish. Currently ZERO dedicated PRESSURE cards.

## Requirements

### Requirement: Paydown Strike

The system SHALL provide ≥1 ATTACK card that repays debt AND deals bonus damage scaling with amount repaid: `baseDamage + debtRepaid`.

#### Scenario: Paydown scales with repayment

- GIVEN debt = 15, card has baseDamage: 4, debtRepay: 3
- WHEN card resolves
- THEN 3 Debt repaid, damage = 4 + 3 = 7

#### Scenario: Zero debt fallback

- GIVEN debt = 0, debtRepay: 3
- WHEN card resolves
- THEN damage = baseDamage only (no bonus, no negative)

### Requirement: Weak/Vulnerable Stackers

The system SHALL provide ≥2 PRESSURE-tagged cards applying Weak or Vulnerable at ≥2 turns per application.

#### Scenario: Double status application

- GIVEN PRESSURE weak-stacker with weakApply: 2
- WHEN card resolves
- THEN enemy receives weak: 2

### Requirement: Low-Debt Escalator

The system SHALL provide ≥1 card that triggers a bonus when debt < 15 at end of turn (extra damage, draw, or strength).

#### Scenario: Trigger at low debt

- GIVEN debt = 10, end of turn
- THEN escalator bonus activates

#### Scenario: No trigger at high debt

- GIVEN debt = 30, end of turn
- THEN no escalator bonus

### Requirement: AUDIT-Punish Card

The system SHALL provide ≥1 PRESSURE card that punishes enemy BUFF/EMPOWER actions (e.g., applies vulnerability when enemy buffs).

#### Scenario: Punish on enemy buff

- GIVEN enemy plays BUFF intent, player has AUDIT card
- WHEN buff resolves
- THEN enemy receives vulnerable: 2

### Requirement: PRESSURE Card Identity

The system SHALL have ≥4 distinct PRESSURE-tagged cards in the reward pool. Cards MUST have explicit PRESSURE-identifying tags.

#### Scenario: False-positive trap — non-economy ≠ PRESSURE tag

- GIVEN a test counts 10 non-economy cards and declares "PRESSURE has 10 cards"
- THEN the test is wrong. Non-economy cards signal PRESSURE for `playerArchetype()` tie-breaking but are NOT PRESSURE-tagged dedicated cards.
