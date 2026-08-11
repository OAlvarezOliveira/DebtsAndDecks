# Technical Design Document — Debts & Decks (MVP)

> Architecture, modules, data flow. Update when architecture changes.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Android App (app module)               │
├─────────────────────────────────────────────────────────────┤
│  MainActivity → GameScreen (LibGDX)                         │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐    ┌──────────────────────────────┐  │
│  │   Core (Pure      │    │   GDX Layer (Platform)       │  │
│  │   Kotlin, no      │    │   - Rendering (SpriteBatch)  │  │
│  │   Android/GDX)    │    │   - Input (GDX InputProcessor)│  │
│  │                   │    │   - Asset Loading            │  │
│  │  - CombatEngine   │◀───│   - Screen Management        │  │
│  │  - Card System    │    │                              │  │
│  │  - Enemy AI       │    │                              │  │
│  │  - Model/State    │    │                              │  │
│  │  - Data (JSON)    │    │                              │  │
│  └──────────────────┘    └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Rule:** Core knows nothing about LibGDX/Android. GDX layer translates.

---

## Module: Core (`core/`)

### CombatEngine

```kotlin
class CombatEngine(
    private val player: Player,
    private val enemies: List<Enemy>,
    private val cardRegistry: CardRegistry,
    private val rng: Random
) {
    fun startCombat()
    fun playCard(cardId: String, targetId: String?): Result
    fun endPlayerTurn(): TurnResult
    fun processEnemyTurn(): TurnResult
    fun getState(): CombatState
}
```

**Responsibilities:**
- Turn orchestration
- Energy management
- Card resolution
- Enemy intent execution
- Win/lose detection
- State snapshots for rendering

### State Model (Immutable Data Classes)

```kotlin
// Core state snapshot — rendered by GDX layer
data class CombatState(
    val player: PlayerState,
    val enemies: List<EnemyState>,
    val currentTurn: TurnPhase,
    val energy: Int,
    val hand: List<CardInstance>,
    val drawPileCount: Int,
    val discardPileCount: Int,
    val exhaustPileCount: Int,
    val log: List<CombatLogEntry>
)

enum class TurnPhase { PLAYER_DRAW, PLAYER_ACTION, ENEMY_ACTION, TURN_END, COMBAT_END }

data class PlayerState(
    val hp: Int,
    val maxHp: Int,
    val block: Int,
    val strength: Int,
    val weak: Int,        // turns remaining
    val vulnerable: Int
)

data class EnemyState(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val block: Int,
    val strength: Int,
    val intent: IntentType,
    val intentDamage: Int
)

data class CardInstance(
    val id: String,           // unique runtime ID
    val cardId: String,       // references CardDefinition
    val cost: Int,
    val exhausted: Boolean
)
```

### Card System

```kotlin
// Definition (loaded from JSON)
data class CardDefinition(
    val id: String,
    val name: String,
    val type: CardType,
    val cost: Int,
    val damage: Int = 0,
    val block: Int = 0,
    val draw: Int = 0,
    val strengthGain: Int = 0,
    val weakApply: Int = 0,
    val vulnerableApply: Int = 0,
    val targetType: TargetType,
    val description: String,
    val rarity: Rarity,
    val tags: Set<String>
)

// Runtime instance (mutable during combat)
class CardInstance(val definition: CardDefinition) {
    var cost: Int = definition.cost
    var exhausted: Boolean = false
    var upgraded: Boolean = false // MVP: false
}

// Resolution
sealed interface CardEffect {
    data class Damage(val amount: Int, val target: TargetType) : CardEffect
    data class Block(val amount: Int) : CardEffect
    data class Draw(val count: Int) : CardEffect
    data class ApplyStrength(val amount: Int, val target: TargetType) : CardEffect
    data class ApplyWeak(val turns: Int, val target: TargetType) : CardEffect
    data class ApplyVulnerable(val turns: Int, val target: TargetType) : CardEffect
    data class ExhaustSelf : CardEffect
}
```

**Card Resolution Pipeline:**
```
playCard(card, target)
    → validate (cost, targetable)
    → payEnergy
    → computeEffects(card, target, playerState, enemyStates)
    → applyEffects (mutate state)
    → moveCardToDiscard (or exhaust)
    → triggerOnPlay hooks
    → return Result
```

### Enemy AI

```kotlin
class EnemyAI(
    private val pattern: List<IntentStep>,
    private val rng: Random
) {
    var patternIndex = 0

    fun nextIntent(): Intent {
        val step = pattern[patternIndex % pattern.size]
        patternIndex++
        return Intent(step.type, step.damage, step.param)
    }
}

data class IntentStep(
    val type: IntentType,
    val damage: Int = 0,
    val param: Int = 0  // buff amount, debuff turns, etc.
)

enum class IntentType { ATTACK, BUFF, DEBUFF, MULTI_ATTACK }
```

**Intent Execution:**
```
ATTACK(damage)          → player.takeDamage(damage)
BUFF(strengthGain)      → enemy.strength += strengthGain
DEBUFF(weakTurns)       → player.weak += weakTurns
MULTI_ATTACK(count, dmg)→ repeat count times: player.takeDamage(dmg)
```

### Data Loading

```kotlin
// JSON → Data Class via Kotlinx Serialization
@Serializable
data class CardDefinition(...)

@Serializable
data class EnemyDefinition(...)

object DataLoader {
    fun loadCards(context: Context): List<CardDefinition> {
        val json = context.assets.open("cards/all.json").readText()
        return Json.decodeFromString<List<CardDefinition>>(json)
    }
}
```

---

## Module: GDX Layer (`gdx/`)

### Screen Architecture

```kotlin
class GameScreen : Screen {
    private val stage = Stage()
    private val combatEngine: CombatEngine
    private val renderer: CombatRenderer
    private val inputHandler: CombatInputHandler

    override fun render(delta: Float) {
        // 1. Handle input
        // 2. Update animations
        // 3. Render
    }
}
```

### Rendering

| Component | Responsibility |
|-----------|----------------|
| `CombatRenderer` | Draws state: HP bars, block, hand, enemy intents, log |
| `CardRenderer` | Draws card: cost, name, description, artwork placeholder |
| `EnemyRenderer` | Draws enemy sprite, HP bar, intent icon |
| `UIRenderer` | Energy, end turn button, turn phase indicator |

**Render Loop:**
```
render(delta)
    → Gdx.gl.glClear(...)
    → batch.begin()
    → renderer.drawBackground()
    → renderer.drawEnemies(state.enemies)
    → renderer.drawPlayer(state.player)
    → renderer.drawHand(state.hand, selectedCard)
    → renderer.drawUI(state.energy, state.currentTurn)
    → renderer.drawLog(state.log)
    → batch.end()
    → stage.act(delta)
    → stage.draw()
```

### Input Handling

```kotlin
class CombatInputHandler(
    private val engine: CombatEngine,
    private val camera: Camera
) : InputProcessor {

    override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
        val worldCoords = camera.unproject(Vector3(x.toFloat(), y.toFloat(), 0f))
        
        // 1. Check card in hand clicked
        // 2. Check target (enemy) clicked
        // 3. Check end turn button
        // 4. Return consumed
    }
}
```

**Input Flow:**
```
touchDown
    → if card in hand: selectCard(card)
    → if targetable card selected + enemy clicked: engine.playCard(cardId, enemyId)
    → if endTurn button: engine.endPlayerTurn()
    → if enemy intent hovered: show tooltip
```

---

## Data Files (JSON)

### `assets/cards/all.json`
```json
[
  {
    "id": "strike",
    "name": "Strike",
    "type": "ATTACK",
    "cost": 1,
    "damage": 6,
    "block": 0,
    "draw": 0,
    "strengthGain": 0,
    "weakApply": 0,
    "vulnerableApply": 0,
    "targetType": "ENEMY",
    "description": "Deal 6 damage.",
    "rarity": "BASIC",
    "tags": ["starter"]
  }
]
```

### `assets/enemies/all.json`
```json
[
  {
    "id": "thug",
    "name": "Thug",
    "hp": 24,
    "intentPattern": [
      {"type": "ATTACK", "damage": 6},
      {"type": "ATTACK", "damage": 6},
      {"type": "BUFF", "param": 3}
    ],
    "rewards": {"gold": 10, "cardChoices": 3}
  }
]
```

---

## Dependency Injection (Koin)

```kotlin
// di/Module.kt
val coreModule = module {
    single { CardRegistry(get()) }
    single { Random(System.currentTimeMillis()) }
    factory { (player: Player, enemies: List<Enemy>) ->
        CombatEngine(player, enemies, get(), get())
    }
}

val gdxModule = module {
    single { CombatRenderer(get()) }
    factory { CombatInputHandler(get(), get()) }
    factory { GameScreen(get(), get(), get()) }
}
```

---

## Build Configuration

### `settings.gradle.kts`
```kotlin
rootProject.name = "DebtsAndDecks"
include(":app")
```

### `build.gradle.kts` (root)
```kotlin
plugins {
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}
```

### `app/build.gradle.kts` (key deps)
```kotlin
dependencies {
    // LibGDX
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86_64")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DI
    implementation("io.insert-koin:koin-android:3.5.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.13")
}
```

---

## Testing Strategy

| Layer | Tool | Coverage Target |
|-------|------|-----------------|
| Core (CombatEngine, CardResolution, EnemyAI) | JUnit + MockK | 80%+ |
| Data Loading | JUnit | 100% |
| GDX Layer | Manual / Instrumented | Smoke only |

**Test Example:**
```kotlin
class CombatEngineTest {
    @Test
    fun `playing strike deals damage to enemy`() {
        val engine = createEngine()
        engine.playCard("strike_1", "thug_1")
        assertEquals(18, engine.getState().enemies.first().hp) // 24 - 6
    }
}
```

---

## Performance Budget (MVP)

| Metric | Budget |
|--------|--------|
| APK Size | < 20 MB |
| Launch Time | < 2 sec |
| Frame Time | 16 ms (60 FPS) |
| GC Pressure | < 1 MB/frame |
| Memory | < 100 MB |

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-08-11 | Initial MVP TDD | — |

---

*Last updated: 2025-08-11 — Keep in sync with code.*