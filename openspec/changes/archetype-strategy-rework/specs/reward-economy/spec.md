# Reward Economy Specification

## Purpose

3-choose-1 biased picks replacing 1-of-1. Upgrade: 1 every 4 wins, cap raised from 2 to 4. Convergence: ~5 picks + 2 upgrades to express one archetype.

## Requirements

### Requirement: 3-Choose-1 Biased Free Pick

The system SHALL offer 3 card choices at each free-pick node, biased toward detected archetype (3× matching, 1× cross, 2× neutral). Starter-tagged cards excluded.

#### Scenario: Biased offer

- GIVEN archetype = LEVERAGE, pool has 5 LEVERAGE cards
- WHEN free pick enters
- THEN offer contains ≥2 LEVERAGE cards with probability ≥ 0.6

#### Scenario: No starters in offer

- WHEN free pick generated
- THEN no starter-tagged card appears (strike, defend, bash, survive excluded)

### Requirement: Upgrade Cadence

The system SHALL offer 1 upgrade every 4 wins. `MAX_UPGRADES_PER_RUN` raised from 2 to 4. Cost flat 15g.

#### Scenario: Upgrade at win 4

- GIVEN 4 wins, entering node after slot 4
- WHEN node entered
- THEN at least 1 upgrade offered (if eligible cards exist)

#### Scenario: Cap enforcement

- GIVEN 4 upgrades already used
- WHEN attempting 5th upgrade
- THEN upgrade rejected

#### Scenario: False-positive trap — upgrade every node

- GIVEN a test shows upgrades offered at every node (1–7)
- THEN the test is wrong. Upgrades occur every 4 wins only. Offering every node destroys convergence.

### Requirement: Convergence Guarantee

The system SHALL enable one-archetype expression per run. A sim with archetype-picking policy SHALL produce ≥40% winning decks with >50% economy tags in one archetype.

#### Scenario: Sim validates concentration

- GIVEN 200 runs, policy picks highest-weighted archetype card
- WHEN measuring dominant tag concentration
- THEN ≥40% of wins have >50% economy tags in one archetype

### Requirement: Run Length Unchanged

The system SHALL NOT change combat count. Sequence remains 8 slots per `run/sequence.json`.

#### Scenario: Sequence invariant

- WHEN counting slots in `run/sequence.json`
- THEN exactly 8 slots exist
