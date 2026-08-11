# Debts & Decks

> A roguelike deck-builder where you pay off debts by fighting creditors. One 5-minute loop MVP.

## Project Vision

**Core Loop (5 min):** Draw hand → Play cards to attack/defend → Defeat creditor → Earn currency → Upgrade deck → Repeat

**MVP Scope:** Single combat encounter, 15 cards, 3 enemies, no persistence, no menus — just the loop.

---

## Quick Start

```bash
# Clone and build
git clone <repo>
cd "Debts & Decks"
./gradlew assembleDebug

# Run on device/emulator
./gradlew installDebug
```

---

## Documentation Index

| Document | Purpose | When to Update |
|----------|---------|----------------|
| [GDD.md](docs/GDD.md) | Game Design Document — core loop, cards, enemies, economy | When mechanics change |
| [TDD.md](docs/TDD.md) | Technical Design Document — architecture, modules, data flow | When architecture changes |
| [ADR/](docs/ADR/) | Architecture Decision Records — irreversible choices | New tech/pattern decisions |
| [TRACKING.md](docs/TRACKING.md) | Task board, sprint log, blockers | Daily |
| [CONVENTIONS.md](docs/CONVENTIONS.md) | Code style, commit messages, branch strategy | When conventions evolve |

---

## Commit Convention

We use **Conventional Commits** with a game-dev flavor:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

| Type | Use For |
|------|---------|
| `feat` | New card, enemy, mechanic, system |
| `fix` | Bug in combat, rendering, logic |
| `refactor` | Code restructure without behavior change |
| `balance` | Number tweaks (damage, cost, drop rates) |
| `content` | Assets, data files, card definitions |
| `docs` | Documentation only |
| `chore` | Build, deps, tooling, CI |
| `wip` | Work in progress (squash before merge) |

### Scopes

`combat` `deck` `enemy` `ui` `core` `build` `assets` `data`

### Examples

```
feat(combat): add block mechanic with visual feedback

- Block reduces incoming damage
- Added BlockComponent and BlockSystem
- Block UI shows remaining block above HP

Closes #12
```

```
balance(enemy): reduce Loan Shark HP from 40 to 32

First playtest showed 40 HP makes fight drag past 5 min target.
```

```
content(cards): add 5 starter deck cards (Strike, Defend, etc.)

Data-driven via JSON in assets/cards/
```

---

## Branch Strategy

```
main          → Protected, only via PR, always buildable
develop       → Integration branch, may be unstable
feature/*     → One feature per branch (feat/combat-block)
fix/*         → Hotfixes for main
balance/*     → Number tuning branches
content/*     → Asset/data additions
```

**Merge rule:** Squash & merge to `develop`. `develop` → `main` via PR on milestones.

---

## Project Tracking

See [TRACKING.md](docs/TRACKING.md) for:
- Current sprint goal
- Task board (To Do / Doing / Done)
- Blockers & decisions
- Playtest notes

Update **daily** at end of session.

---

## Tech Stack (MVP)

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin | Native Android, modern, coroutines |
| Engine | LibGDX | 2D, cross-platform, lightweight, no editor lock-in |
| Build | Gradle (KTS) | Standard Android, version catalogs |
| DI | Koin | Lightweight, Kotlin-first |
| Data | JSON + DataStore | No DB needed for MVP |
| Testing | JUnit + MockK | Unit tests for game logic |

**No:** Room, Hilt, Compose (game loop), multi-module (yet).

---

## Directory Structure

```
Debts & Decks/
├── app/                    # Android application module
│   ├── src/main/
│   │   ├── java/com/debtsdecks/
│   │   │   ├── core/       # Game logic (engine-agnostic)
│   │   │   │   ├── combat/
│   │   │   │   ├── cards/
│   │   │   │   ├── enemies/
│   │   │   │   ├── deck/
│   │   │   │   └── model/
│   │   │   ├── gdx/        # LibGDX integration
│   │   │   │   ├── screens/
│   │   │   │   ├── render/
│   │   │   │   └── input/
│   │   │   └── MainActivity.kt
│   │   ├── assets/
│   │   │   ├── cards/      # Card definitions (JSON)
│   │   │   ├── enemies/    # Enemy definitions (JSON)
│   │   │   └── art/        # Sprites, fonts
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/
│   ├── GDD.md
│   ├── TDD.md
│   ├── TRACKING.md
│   ├── CONVENTIONS.md
│   └── ADR/
│       └── 0001-use-libgdx.md
├── .github/workflows/      # CI (optional for MVP)
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

---

## Definition of Done (MVP)

- [ ] APK builds and runs on device
- [ ] Single combat plays start-to-finish in ~5 min
- [ ] 15 cards playable, 3 enemies fightable
- [ ] Win/lose conditions work
- [ ] No crashes, no ANRs
- [ ] Code builds on clean checkout
- [ ] `TRACKING.md` updated with final status

---

## Useful Commands

```bash
# Clean build
./gradlew clean assembleDebug

# Run tests
./gradlew test

# Lint
./gradlew lint

# Generate docs (if using Dokka)
./gradlew dokkaHtml
```

---

## License

Proprietary — Personal project, not for distribution.