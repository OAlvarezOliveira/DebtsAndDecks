# Archetype Synergy Specification

## Purpose

Tag-count reward tiers that give each archetype legible combat payoff. Built on `playerArchetype()` scoring — no new data model, no global debt coupling.

## Requirements

### Requirement: Tag-Count Tier Computation

The system SHALL compute per-archetype tag counts and assign tier = floor(tagCount / 2), capped at 3. Thresholds: 2, 4, 6 economy tags of one archetype.

#### Scenario: Tier at thresholds

- GIVEN 3 LEVERAGE-tagged cards
- WHEN tier computed
- THEN tier = 1 (floor(3/2))
- AND 5 LIQUIDITY tags → tier = 2

#### Scenario: No tier with sparse tags

- GIVEN 1 LEVERAGE-tagged card
- WHEN tier computed
- THEN tier = 0 (no bonus applied)

### Requirement: LEVERAGE Tier Bonus

The system SHALL grant +1 flat damage per attack per LEVERAGE tier, stacking with existing `floor(debt / LEVERAGE_DIVISOR)`.

#### Scenario: Tier stacks with base leverage

- GIVEN debt = 24, LEVERAGE tier = 2
- WHEN non-tagged attack resolves
- THEN bonus = floor(24/6) + 2 = 6 damage

#### Scenario: False-positive trap

- GIVEN a test reports LEVERAGE tier bonus with 0 LEVERAGE tags
- THEN the test is wrong — tier must be 0 and no bonus applies.

### Requirement: LIQUIDITY Tier Bonus

The system SHALL grant +1 extra draw per turn per LIQUIDITY tier at combat start, and +10% multiplier on gold gains per tier.

#### Scenario: Draw bonus at tier 1

- GIVEN LIQUIDITY tier = 1
- WHEN combat starts
- THEN player draws 1 extra card first turn

### Requirement: PRESSURE Tier Bonus

The system SHALL grant +1 Weak and +1 Vulnerable application per PRESSURE tier to PRESSURE-tagged cards. At tier 2+, +20% damage when enemy HP < 50%.

#### Scenario: Status escalation

- GIVEN PRESSURE tier = 1, card applies weakApply: 1
- WHEN card resolves
- THEN enemy receives weakApply: 2

#### Scenario: False-positive trap

- GIVEN a test shows PRESSURE multiplier active with only 3 plain non-economy cards
- THEN the test is wrong — plain non-economy cards signal PRESSURE for `playerArchetype()` but do NOT count toward tier thresholds. Only PRESSURE-tagged cards count.

### Requirement: No Global Debt Coupling

The system SHALL NOT modify any global debt scalar, interest rate, bleed floor, or garnishment based on synergy tiers.

#### Scenario: Debt unchanged by tier

- GIVEN LEVERAGE tier = 3, debt = 40
- WHEN combat turn elapses
- THEN interest equals `applyInterest(40)` unchanged by tier
