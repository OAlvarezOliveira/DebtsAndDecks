# Liquidity Archetype Specification

## Purpose

LIQUIDITY card roles — gold→block conversion, gold-scaling attacks, and reinforcement of existing economy cards (`debt_draw`, `refinance`, `gain_credit`). Existing roles reused; no new debt mechanics.

## Requirements

### Requirement: Liquidity Shield (Gold→Block)

The system SHALL provide at least one card that converts Gold to Block at play time. The conversion rate SHALL be at least 1:2 (1 Gold → 2 Block). The card SHALL NOT modify debt.

#### Scenario: Shield conversion

- GIVEN player has 15 Gold, plays "Liquidity Shield" (cost 1, rate 1:2)
- WHEN the card resolves
- THEN player gains 30 Block and Gold decreases by 15 (or remaining Gold × 2 if < 15)

#### Scenario: No debt side-effect

- GIVEN debt = 30, player plays a LIQUIDITY shield card
- WHEN the card resolves
- THEN debt remains exactly 30

### Requirement: Gold-Scaling Attack

The system SHALL provide at least one ATTACK card whose damage scales with current Gold holdings (not debt). Scaling formula: `baseDamage + floor(gold / N)` where N >= 5.

#### Scenario: Gold scaling at moderate wealth

- GIVEN gold = 40, baseDamage = 5, N = 5
- WHEN the card resolves
- THEN damage = 5 + floor(40/5) = 13

### Requirement: Existing LIQUIDITY Role Reuse

The system SHALL retain and reinforce existing LIQUIDITY-tagged cards: `debt_draw` (overdraft), `refinance`, `gain_credit` (golden_credit, subprime_loan), `gold_scaled_debt` (reverse_mortgage), `hand_exhaust` (asset_auction). No existing LIQUIDITY card SHALL lose its tag or change its core mechanic.

#### Scenario: Existing tags preserved

- GIVEN the card registry after changes
- WHEN checking `refinance` card tags
- THEN the card still has the `"refinance"` tag and its mechanic is unchanged (halves debt, grants block equal to cancelled amount)

#### Scenario: False-positive trap

- GIVEN a test declares LIQUIDITY "complete" because 6 cards carry LIQUIDITY tags
- THEN the test is incomplete — completeness requires at least 1 NEW gold→block card and 1 NEW gold-scaling attack (the existing cards cover draw/credit/exhaust but lack the two new roles).
