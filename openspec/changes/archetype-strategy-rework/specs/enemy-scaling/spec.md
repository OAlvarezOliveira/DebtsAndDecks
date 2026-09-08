# Enemy Scaling Specification

## Purpose

Per-act HP/damage scaling (~1.4–2.5× on 22/36/52/52 baseline). HP MUST matter — block alone no longer outlasts enemies.

## Requirements

### Requirement: Per-Act Enemy Stats

The system SHALL scale enemy HP and damage per act/district per this table:

| Enemy | Act I | Act II | Act III (boss) |
|-------|-------|--------|----------------|
| thug | 30 HP / 8–11 dmg | 55 HP / 12–14 dmg | — |
| loan_shark | — | 65 HP / 13–15 dmg | 90 HP / 16–18 dmg |
| collector | — | — | 120 HP / 18–22 dmg |
| godfather | 40 HP / 9–11 dmg | 75 HP / 14–16 dmg | 140 HP / 20–22 dmg |

#### Scenario: Act I thug tanks more hits

- GIVEN 6-damage attack vs thug (30 HP vs baseline 22 HP)
- THEN hits-to-kill = 5 vs 4

#### Scenario: Act III boss requires commitment

- GIVEN collector (120 HP, 18–22 dmg) vs player (50 maxHp, ~8 block/turn)
- THEN player cannot outlast boss with block alone (~100+ boss damage over pattern)

### Requirement: Intent Variety Beyond ATTACK Superset

The system SHALL add ≥1 non-ATTACK intent per enemy beyond current repertoire (ATTACK/BUFF/DEBUFF/LEVY/MULTI). New intents: FORECLOSE (force debt payment or penalty), HEDGE (reduce incoming damage), AUDIT (punish deck composition).

#### Scenario: FORECLOSE forces a decision

- GIVEN enemy FORECLOSE intent, player debt > 10
- WHEN intent executes
- THEN player loses 5 HP or gains 10 debt

### Requirement: HP Matters Invariant

The system SHALL ensure average hits-to-kill for a 6-damage attack across all 8 combats is ≥ 4 (currently 3.7 on baseline 22 HP).

#### Scenario: Sim validates hits-to-kill

- GIVEN sim with 200 seeds, scaled enemy stats
- WHEN measuring avg hits-to-kill for 6-damage attack
- THEN avg ≥ 4.0

#### Scenario: False-positive trap — HP without damage scaling

- GIVEN thug HP = 30 but damage stays 10
- THEN the test is incomplete — without damage scaling, player has MORE time to block, making fights EASIER. Both MUST scale.

### Requirement: Data-Driven Scaling

Enemy HP and damage SHALL be defined in `enemies/all.json`, not hardcoded in Kotlin. Rollback to baseline requires only JSON edit.
