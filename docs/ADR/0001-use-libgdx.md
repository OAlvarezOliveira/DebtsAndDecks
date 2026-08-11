# ADR 0001: Use LibGDX for Rendering

## Status
Accepted

## Date
2025-08-11

## Context
We need a 2D rendering solution for an Android game (Debts & Decks MVP). The game is a card-based roguelike with:
- Hand of cards (5-10 cards)
- Enemy sprites with HP bars and intent icons
- Turn-based combat UI (energy, end turn, log)
- Target selection (tap card → tap enemy)
- 60 FPS target, low memory budget

Options considered:
1. **LibGDX** — Mature 2D framework, Kotlin-friendly, code-first, no editor lock-in
2. **Godot 4** — Full engine with editor, GDScript/C#, export to Android
3. **Android Canvas + Custom View** — Native, no deps, but manual everything
4. **Jetpack Compose** — Modern declarative UI, but not designed for game loops
5. **Unity** — Overkill, C#, heavy, license concerns

## Decision
**Use LibGDX** with Kotlin (no Scene2D UI, custom rendering via SpriteBatch/ShapeRenderer).

## Consequences

### Positive
- **Lightweight:** ~2 MB core, minimal APK impact
- **Kotlin-first:** First-class Kotlin support, coroutines integration
- **Code-first:** No editor lock-in, version-controllable, refactorable
- **Cross-platform:** Desktop (JVM) for rapid iteration, Android for target
- **Battle-tested:** Used in Slay the Spire, many commercial games
- **Flexible rendering:** SpriteBatch for sprites, ShapeRenderer for debug/prototyping
- **Asset management:** Built-in AssetManager, texture atlases
- **Input handling:** InputProcessor, GestureDetector built-in

### Negative
- **No visual editor:** UI layout in code (mitigated: simple UI for MVP)
- **Learning curve:** Scene graph, cameras, viewports (mitigated: TDD documents patterns)
- **Manual memory management:** `dispose()` on textures, fonts (mitigated: AssetManager)
- **No built-in ECS:** We roll our own simple systems (mitigated: TDD defines CombatEngine)

### Risks & Mitigations
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| LibGDX abandoned | Low | High | Mature, community fork if needed; Slay the Spire still uses it |
| Android 14+ compatibility | Low | Medium | LibGDX 1.12+ targets API 34; test on device early |
| Performance on low-end | Medium | High | Profile early; ShapeRenderer for MVP, sprite atlas later |

## Alternatives Rejected

### Godot 4
- **Pros:** Visual editor, GDScript fast to write, good 2D
- **Cons:** Editor lock-in (scenes not easily diffable), GDScript not Kotlin, larger runtime, C# adds complexity

### Android Canvas
- **Pros:** Zero deps, native
- **Cons:** Reinvent SpriteBatch, texture management, input handling, animation — weeks of boilerplate

### Jetpack Compose
- **Pros:** Declarative, modern, Kotlin
- **Cons:** Not a game engine; no game loop, no texture atlases, no fixed timestep, GC pressure from recomposition

### Unity
- **Pros:** Industry standard, asset store
- **Cons:** C#, heavy (50+ MB empty), license tracking, overkill for 2D card game

## Implementation Notes

### Gradle Setup
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86_64")
}
```

### MainActivity
```kotlin
class MainActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            r = 8; g = 8; b = 8; a = 8
            depth = 16; stencil = 8
            useImmersiveMode = true
        }
        initialize(GameApp(), config)
    }
}
```

### GameApp (LibGDX ApplicationListener)
```kotlin
class GameApp : ApplicationAdapter() {
    private lateinit var screen: GameScreen

    override fun create() {
        screen = KoinApp.container.get()  // DI provides GameScreen
        setScreen(screen)
    }

    override fun render() {
        super.render()
        screen.render(Gdx.graphics.deltaTime)
    }

    override fun dispose() {
        screen.dispose()
    }
}
```

### Core/GDX Separation
- **Core** (`core/`): Pure Kotlin, zero LibGDX deps. Contains `CombatEngine`, `CardResolver`, `EnemyAI`, state models.
- **GDX** (`gdx/`): Platform layer. `GameScreen`, `CombatRenderer`, `CombatInputHandler`, asset loading.
- **Communication:** Core emits immutable `CombatState`; GDX renders it. Input flows GDX → Core.

## References
- [LibGDX Wiki](https://libgdx.com/wiki/)
- [Slay the Spire Modding API (LibGDX)](https://github.com/kiooeht/StS-Modding-Documentation)
- [Kotlin LibGDX Examples](https://github.com/libgdx/libgdx/tree/master/tests/gdx-tests/src/com/badlogic/gdx/tests)

## Review
- **Author:** Oscar
- **Reviewed by:** (self — solo project)
- **Next review:** After first playable combat (Sprint 1)