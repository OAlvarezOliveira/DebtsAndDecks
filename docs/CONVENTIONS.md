# Conventions — Debts & Decks

> Code style, commit messages, branch strategy, PR process. Update when conventions evolve.

---

## Kotlin Code Style

### Formatting
- **Indentation:** 4 spaces (no tabs)
- **Line length:** 120 chars max
- **Trailing commas:** Yes (multiline)
- **Semicolons:** Never
- **Wildcard imports:** Never

```kotlin
// Good
val cards = listOf(
    Card("strike", 6),
    Card("defend", 5),
)

// Bad
val cards = listOf(Card("strike", 6), Card("defend", 5))
```

### Naming

| Element | Convention | Example |
|---------|------------|---------|
| Packages | lowercase, reverse domain | `com.debtsdecks.core.combat` |
| Classes/Objects | PascalCase | `CombatEngine`, `CardRegistry` |
| Functions/Properties | camelCase | `playCard`, `currentEnergy` |
| Constants | UPPER_SNAKE_CASE | `MAX_HAND_SIZE` |
| Enum cases | PascalCase | `CardType.ATTACK` |
| Type parameters | Single uppercase | `<T>`, `<E>` |
| Test methods | backticks + spaces | `` `playing strike deals damage` `` |

### Architecture Rules

1. **Core is pure Kotlin** — no Android, no LibGDX, no platform deps
2. **Immutable state** — Core emits `CombatState` snapshots; GDX renders
3. **Data-driven** — Cards, enemies loaded from JSON via Serialization
4. **Single responsibility** — One class, one job
5. **No singletons in Core** — Use DI (Koin) for instances
6. **Coroutines for async** — Asset loading, not game loop

### File Organization

```
core/
  combat/
    CombatEngine.kt           # Orchestrator
    CombatState.kt            # State snapshot
    TurnPhase.kt              # Enum
    resolution/
      CardResolver.kt         # Effect computation
      DamageCalculator.kt     # Damage/block/weak/vuln math
  cards/
    CardDefinition.kt         # Data class (serializable)
    CardInstance.kt           # Runtime wrapper
    CardRegistry.kt           # Lookup by ID
    CardType.kt               # Enum
    TargetType.kt             # Enum
  enemies/
    EnemyDefinition.kt
    EnemyInstance.kt
    EnemyAI.kt
    IntentType.kt
  model/
    PlayerState.kt
    EnemyState.kt
    StatusEffect.kt
  data/
    DataLoader.kt
```

### Testing Conventions

```kotlin
class CombatEngineTest {
    private val rng = Random(42)  // Deterministic seed
    private lateinit var engine: CombatEngine

    @BeforeEach
    fun setup() {
        engine = createTestEngine(rng)
    }

    @Test
    fun `playing strike deals damage to enemy`() {
        // Given
        val initialHp = engine.state.enemies.first().hp

        // When
        engine.playCard("strike_1", "thug_1")

        // Then
        assertEquals(initialHp - 6, engine.state.enemies.first().hp)
    }
}
```

- **Test naming:** `` `behavior under condition` ``
- **Arrange/Act/Assert** with comments
- **Deterministic RNG** — inject `Random(seed)`
- **No GDX in unit tests** — Core only

---

## Git Conventions

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Types
| Type | Description |
|------|-------------|
| `feat` | New feature (card, enemy, mechanic) |
| `fix` | Bug fix |
| `refactor` | Code restructure, no behavior change |
| `balance` | Number tuning (damage, cost, HP) |
| `content` | Assets, JSON data, card/enemy definitions |
| `docs` | Documentation only |
| `chore` | Build, deps, tooling, CI |
| `wip` | Work in progress (squash before merge) |

#### Scopes
`combat` `cards` `enemies` `deck` `ui` `core` `gdx` `build` `assets` `data` `test` `docs`

#### Subject
- Imperative mood: "add" not "added"
- Lowercase, no period
- ≤ 50 chars

#### Body
- Explain *what* and *why*, not *how*
- Wrap at 72 chars
- Bullet points for multiple changes

#### Footer
- `Closes #123` or `Fixes #123`
- `Breaking Change: <description>` if applicable

### Examples

```
feat(cards): add Iron Wave card (damage + block)

- Cost 2, deals 7 damage, grants 7 block
- Added to reward pool JSON
- CardResolver handles dual effect

Closes #15
```

```
balance(enemies): reduce Collector HP from 60 to 56

Playtest showed 60 HP pushes fight past 5 min target.
Adjusted to 56 (7 turns at 8 dmg/turn).

Fixes #22
```

```
refactor(core): extract DamageCalculator from CombatEngine

- Single responsibility: damage/block/weak/vuln math
- Easier to unit test in isolation
- No behavior change
```

```
content(enemies): add Thug, Loan Shark, Collector definitions

- JSON in assets/enemies/all.json
- Intent patterns per GDD
- Reward tables included
```

### Branch Naming

| Branch Type | Pattern | Example |
|-------------|---------|---------|
| Feature | `feat/<scope>-<short-desc>` | `feat/combat-block-mechanic` |
| Fix | `fix/<scope>-<short-desc>` | `fix/cards-exhaust-not-working` |
| Balance | `balance/<scope>-<short-desc>` | `balance/enemy-thug-hp` |
| Content | `content/<scope>-<short-desc>` | `content/cards-reward-pool` |
| Docs | `docs/<short-desc>` | `docs/update-gdd-status-effects` |
| Chore | `chore/<short-desc>` | `chore/upgrade-gradle` |

### PR Process

1. Branch from `develop`
2. Commits follow convention (squash WIP)
3. PR title: `<type>(<scope>): <subject>`
4. Description: What, Why, Testing done, Screenshots (if UI)
5. **Required checks:** Build passes, Tests pass, Lint clean
6. **Review:** Self-review first, then request review
7. **Merge:** Squash & merge to `develop`
8. **Delete branch** after merge

---

## Documentation Conventions

### Markdown Style
- ATX headings (`#`, `##`, `###`)
- Fenced code blocks with language
- Tables for structured data
- Relative links for internal docs
- `> Note:` for callouts

### File Locations
| Content | Location |
|---------|----------|
| Project overview, quick start | `README.md` |
| Game mechanics | `docs/GDD.md` |
| Architecture, data flow | `docs/TDD.md` |
| Task board, daily log | `docs/TRACKING.md` |
| Code/git conventions | `docs/CONVENTIONS.md` |
| Architectural decisions | `docs/ADR/NNNN-title.md` |

### ADR Format

```markdown
# ADR 0001: Use LibGDX for Rendering

## Status
Accepted

## Context
Need 2D rendering for Android game. Options: LibGDX, Godot, Android Canvas, Compose.

## Decision
Use LibGDX.

## Consequences
+ Lightweight, mature, Kotlin-friendly
+ No editor lock-in (code-first)
+ Easy Android deployment
- Manual UI layout (no visual editor)
- Learning curve for scene graph
```

---

## Asset Conventions

### JSON Files
- Lowercase with underscores: `all.json`, `starter_deck.json`
- UTF-8, no BOM
- Pretty-printed (2-space indent)
- Validated against Kotlinx Serialization models

### Art Placeholders (MVP)
| Asset | Spec | Naming |
|-------|------|--------|
| Card frame | 250×350, PNG, transparent bg | `card_frame.png` |
| Card art | 200×200, PNG | `art/<card_id>.png` |
| Enemy sprite | 200×200, PNG | `art/enemy_<id>.png` |
| Intent icons | 64×64, PNG | `art/intent_<type>.png` |
| Font | TTF, 24pt | `font/main.ttf` |

**MVP:** Use colored rectangles via ShapeRenderer — no art assets needed.

---

## Configuration

### `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
```

### `.gitignore` (key entries)
```
# Build
/build
/.gradle
/app/build
*.apk
*.aab

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# Local config
local.properties
*.keystore

# Logs
*.log
```

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-08-11 | Initial conventions | — |

---

*Last updated: 2025-08-11 — Follow these in every commit.*