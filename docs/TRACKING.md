# Project Tracking — Debts & Decks

> Single source of truth for tasks, blockers, decisions. Update **daily** at end of session.

---

## Current Sprint: MVP Combat Loop

**Goal:** Playable 5-minute combat loop (Thug → Loan Shark → Collector) on device.

**Dates:** 2025-08-11 → 2025-08-18 (1 week)

---

## Task Board

### To Do
- [ ] **Setup** Gradle project with LibGDX + Kotlin + Serialization + Koin
- [ ] **Core** CombatEngine with turn orchestration
- [ ] **Core** Card system: definitions, registry, resolution pipeline
- [ ] **Core** Enemy AI: intent pattern, execution
- [ ] **Core** State model + immutable snapshots
- [ ] **Data** Card JSON (15 cards) + Enemy JSON (3 enemies)
- [ ] **Data** DataLoader with Kotlinx Serialization
- [ ] **GDX** GameScreen + basic render loop
- [ ] **GDX** CombatRenderer: HP, block, energy, hand, enemy intents
- [ ] **GDX** CardRenderer: cost, name, description placeholder
- [ ] **GDX** InputHandler: card select → target → play
- [ ] **GDX** End turn button + turn phase indicator
- [ ] **Integration** Wire Core ↔ GDX via DI
- [ ] **Integration** MainActivity launches GameScreen
- [ ] **Polish** Win/Lose screens
- [ ] **Polish** Card reward screen (pick 1 of 3)
- [ ] **Test** Unit tests for CombatEngine, CardResolution, EnemyAI
- [ ] **Test** Playtest full loop, tune balance
- [ ] **Build** APK installs and runs on device

### Doing
- [ ] *Nothing yet — start with project setup*

### Done
- [ ] Documentation foundation (README, GDD, TDD, CONVENTIONS, ADR)

---

## Daily Log

### 2025-08-11 (Session 1)
**Goal:** Documentation foundation complete
**Done:**
- Created README with conventions, commit style, branch strategy
- Created GDD with core loop, cards, enemies, status effects
- Created TDD with architecture, modules, data flow
- Created CONVENTIONS (this file)
- Created ADR/0001-use-libgdx.md
- Set up directory structure
- Initialized Git repo with initial commit (feat: initial project foundation)
- Created develop branch
**Next:** Gradle project setup with LibGDX - verify build works

---

## Blockers & Decisions

| Date | Blocker/Decision | Resolution |
|------|------------------|------------|
| 2025-08-11 | Engine choice: LibGDX vs Godot vs Native | **LibGDX** — lightweight, Kotlin-first, no editor lock-in, easy Android. See ADR/0001. |
| 2025-08-11 | DI: Koin vs Hilt vs Manual | **Koin** — lightweight, Kotlin-first, no annotation processing. |
| 2025-08-11 | Serialization: Gson vs Kotlinx | **Kotlinx Serialization** — multiplatform, compile-time, Kotlin-native. |
| 2025-08-11 | State: Mutable vs Immutable | **Immutable snapshots** — Core emits CombatState, GDX renders. Easier testing, no sync issues. |

---

## Playtest Notes

| Date | Build | Notes | Balance Changes |
|------|-------|-------|-----------------|
| — | — | — | — |

---

## Metrics Dashboard (MVP Targets)

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Combat duration | 4-6 min | — | ⬜ |
| Turns per combat | 8-10 | — | ⬜ |
| Player win rate | 60-70% | — | ⬜ |
| Avg HP on win | 15-30% | — | ⬜ |
| APK size | < 20 MB | — | ⬜ |
| Launch time | < 2 sec | — | ⬜ |
| 60 FPS stability | 99% | — | ⬜ |
| Crash-free sessions | 100% | — | ⬜ |

---

## Backlog (Post-MVP)

- [ ] Map / path selection
- [ ] Relics system
- [ ] Potions system
- [ ] Events (non-combat)
- [ ] Shop
- [ ] Card upgrades
- [ ] Save/Load (DataStore)
- [ ] Meta progression (Debt currency)
- [ ] Act 2, 3 + Bosses
- [ ] Elites
- [ ] Settings / Pause / Accessibility
- [ ] Sound / Music / Particles
- [ ] Tutorial / Encyclopedia

---

## Definition of Done (Per Task)

- [ ] Code compiles
- [ ] Unit tests pass (core logic)
- [ ] Manual test on device/emulator
- [ ] No new lint warnings
- [ ] TRACKING.md updated
- [ ] Commit follows convention

---

*Update this file at end of every session. Commit with `chore(docs): update tracking`.*