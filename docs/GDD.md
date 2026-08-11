# Game Design Document — Debts & Decks (MVP)

> Single source of truth for game mechanics. Update when mechanics change.

---

## Core Loop (5 Minutes)

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   DRAW 5    │────▶│  PLAY CARDS │────▶│  RESOLVE    │────▶│  END TURN   │
│   CARDS     │     │  (Attack/   │     │  (Damage,   │     │  (Discard,  │
│             │     │   Defend,   │     │   Block,    │     │   Draw 5)   │
│             │     │   Effects)  │     │   Effects)  │     │             │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
       ▲                                                                   │
       │                                                                   │
       └───────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  ENEMY TURN     │
                     │  (Auto-resolve) │
                     └─────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  CHECK WIN/LOSE │
                     └─────────────────┘
```

**Target:** 8-10 turns per combat, ~30-40 sec/turn = ~5 min.

---

## Resources

| Resource | Max | Description |
|----------|-----|-------------|
| **Health (HP)** | 50 | Player HP. 0 = lose. |
| **Energy** | 3/turn | Spend to play cards. Resets each turn. |
| **Block** | ∞ | Temporary HP. Resets at end of turn (unless card says otherwise). |
| **Gold** | ∞ | Earned on win. MVP: cosmetic only. |
| **Debt** | ∞ | Meta-currency. MVP: not implemented. |

---

## Card System

### Card Structure

```json
{
  "id": "strike",
  "name": "Strike",
  "type": "ATTACK",
  "cost": 1,
  "damage": 6,
  "description": "Deal 6 damage.",
  "rarity": "BASIC",
  "tags": ["starter"]
}
```

### Card Types

| Type | Behavior |
|------|----------|
| `ATTACK` | Deal damage to target |
| `SKILL` | Apply effect (block, draw, buff, debuff) |
| `POWER` | Persistent effect (not in MVP) |

### Targeting

| Target | Description |
|--------|-------------|
| `ENEMY` | Single enemy |
| `ALL_ENEMIES` | All enemies (not in MVP) |
| `SELF` | Player |
| `RANDOM_ENEMY` | Random enemy |

### Starter Deck (10 cards)

| Card | Cost | Effect | Count |
|------|------|--------|-------|
| Strike | 1 | Deal 6 damage | 5 |
| Defend | 1 | Gain 5 Block | 3 |
| Bash | 2 | Deal 8 damage, apply Vulnerable (1) | 1 |
| Survive | 1 | Gain 8 Block | 1 |

### Reward Pool (5 cards, unlock after win)

| Card | Cost | Effect |
|------|------|--------|
| Heavy Blade | 2 | Deal 14 damage |
| Iron Wave | 2 | Deal 7 damage, gain 7 Block |
| Pommel Strike | 1 | Deal 9 damage, draw 1 card |
| Whirlwind | X | Deal 5 damage to ALL enemies (MVP: single) |
| Reckless Charge | 1 | Deal 7 damage, lose 2 HP |

---

## Enemies

### Enemy Structure

```json
{
  "id": "loan_shark",
  "name": "Loan Shark",
  "hp": 40,
  "intentPattern": ["ATTACK", "BUFF", "ATTACK", "DEBUFF"],
  "attackDamage": 8,
  "buffAmount": 3,
  "debuffType": "WEAK",
  "rewards": { "gold": 15, "cardChoices": 3 }
}
```

### Intent System

Each turn, enemy shows **next intent** (icon above head). Player plans around it.

| Intent | Icon | Effect |
|--------|------|--------|
| `ATTACK` | ⚔️ | Deal damage to player |
| `BUFF` | 🛡️ | Gain Strength / Block |
| `DEBUFF` | ☠️ | Apply Weak / Vulnerable to player |
| `MULTI` | ⚔️⚔️ | Attack multiple times |

### Enemy Roster (MVP: 3)

| Enemy | HP | Pattern | Damage | Special |
|-------|-----|---------|--------|---------|
| **Thug** | 24 | ATTACK, ATTACK, BUFF | 6 | Gains 3 Strength on BUFF |
| **Loan Shark** | 40 | ATTACK, BUFF, ATTACK, DEBUFF | 8 | Applies Weak (1) on DEBUFF |
| **Collector** | 56 | ATTACK, MULTI(2), BUFF, DEBUFF | 10 | MULTI = 2 attacks of 7 |

---

## Status Effects (MVP)

| Effect | Duration | Stacks | Description |
|--------|----------|--------|-------------|
| **Strength** | Combat | Yes | +X damage on attacks |
| **Weak** | 1-2 turns | Yes | -25% damage dealt |
| **Vulnerable** | 1-2 turns | Yes | +50% damage taken |
| **Block** | Turn | Yes | Temporary HP, resets end of turn |

---

## Combat Flow

### Turn Start
1. Gain 3 Energy
2. Draw 5 cards (reshuffle discard if needed)
3. Enemy shows next intent
4. Apply start-of-turn effects (Vulnerable/Weak tick down)

### Player Phase
- Play cards while Energy > 0
- Click card → select target (if needed) → resolve
- Can end turn early

### Enemy Phase
- Execute current intent
- Apply damage/effects
- Advance intent pattern (loop)

### Turn End
1. Discard hand
2. Lose all Block (unless card says Retain)
3. Check win/lose
4. Next turn

---

## Win / Lose Conditions

| Condition | Result |
|-----------|--------|
| Enemy HP ≤ 0 | **WIN** → Show rewards screen → Return to map (MVP: restart) |
| Player HP ≤ 0 | **LOSE** → Game Over screen → Restart run |

---

## Rewards (Post-Combat)

| Reward | Options |
|--------|---------|
| Gold | 10-20 (MVP: cosmetic) |
| Card Reward | Pick 1 of 3 cards to add to deck |
| (Future) Relic | Passive bonus |
| (Future) Potion | One-use consumable |

---

## MVP Scope Boundaries

### IN SCOPE
- Single combat encounter (Thug → Loan Shark → Collector)
- 15 cards (10 starter + 5 rewards)
- 3 enemies with intent system
- Basic block/damage/strength/weak/vulnerable
- Hand/deck/discard/exhaust piles
- Energy system
- Win/lose screens

### OUT OF SCOPE (Post-MVP)
- Map / path selection
- Relics, potions, events, shops
- Save/load, progression, meta-currency
- Multiple acts, bosses, elite enemies
- Card upgrade system
- Settings, pause menu, accessibility
- Sound, music, particles, juice
- Tutorial, tooltips, encyclopedia

---

## Balance Targets

| Metric | Target |
|--------|--------|
| Avg turns to kill Thug | 3-4 |
| Avg turns to kill Loan Shark | 5-6 |
| Avg turns to kill Collector | 7-8 |
| Player HP remaining (win) | 15-30% |
| Energy efficiency (dmg/energy) | ~5-6 |
| Block efficiency (block/energy) | ~5-6 |

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-08-11 | Initial MVP GDD | — |

---

*Last updated: 2025-08-11 — Keep this file in sync with code.*