# Project Tracking — Debts & Decks

> Single source of truth for tasks, blockers, decisions. Update **daily** at end of session.

---

## Current Sprint: MVP Combat Loop

**Goal:** Playable 5-minute combat loop (Thug → Loan Shark → Collector) on device, with debt economy as the difficulty axis.

**Status:** Core loop code-complete and unit-verified. Pending: code delivery (commits), playtest, APK build.

---

## Task Board

### To Do
- [ ] **Delivery** Commit debt-economy work (currently uncommitted in working tree — lifecycle-gated harness, batch-at-end)
- [ ] **Delivery** Merge combat-progression-i18n stack (pr1–pr7, 8 commits) into `develop`
- [ ] **Polish** Manual playtest on device/emulator: full loop + economy feel
- [ ] **Polish** Balance pass from playtest (debt pressure, boss LEVY, reward pool)
- [ ] **Build** APK installs and runs on device (target < 20 MB)
- [ ] **Measure** Metrics dashboard below (combat duration, win rate, FPS, launch time)

### Doing
- [ ] TRACKING.md update (this session)

### Done
- [x] **Setup** Gradle project with LibGDX + Kotlin + Serialization + Koin
- [x] **Core** CombatEngine with turn orchestration
- [x] **Core** Card system: definitions, registry, resolution pipeline
- [x] **Core** Enemy AI: intent pattern, execution
- [x] **Core** State model + immutable snapshots
- [x] **Core** Debt/Gold/Credit economy + garnishment + threshold-break encounter (`DebtConfig`)
- [x] **Core** Enemy tiers with tag-driven mechanics and per-tier rewards
- [x] **Core** Player HP persisted through `startCombat` across encounters
- [x] **Core** `Localizer` interface keeps `core/` pure Kotlin (no GDX imports)
- [x] **Data** Card JSON: 15 economy cards + 4 starter; Enemy JSON: 3 enemies
- [x] **Data** DataLoader + JSON i18n wiring (bundle keys in data, literals via `I18NBundle`)
- [x] **GDX** GameScreen + basic render loop
- [x] **GDX** CombatRenderer: HP, block, energy, hand, enemy intents, reward/end screens
- [x] **GDX** CombatInputHandler: card select → target → play + end turn
- [x] **GDX** SoundManager (5 SFX wired: card play/select, end turn, victory, defeat)
- [x] **GDX** Launcher icon + themed resources (`res/`)
- [x] **Integration** Wire Core ↔ GDX via DI (Module.kt), MainActivity launches GameScreen
- [x] **Polish** Win/Lose screens
- [x] **Polish** Card reward screen (pick 1 of 3; pool = 15 economy cards, starters excluded)
- [x] **Test** Unit tests: CombatEngine, CardResolver, CardInstance, EnemyInstance, RunManager, DebtConfig, EnemyTierRegression, I18nBundle (baseline 76+ green)
- [x] **SDD** Change `debt-economy-cards-and-boss-interest`: verify PASS (7/7 requirements, 12/12 scenarios) — code-complete, delivery pending
- [x] **SDD** Change `combat-progression-and-i18n`: verify completed (core-purity CRITICAL resolved via `Localizer`)
- [x] **Assets** 15 card arts + 3 enemy arts + card frames + intent icons (`assets/art/`)

---

## Daily Log

### 2026-08-25 (Session — fallback harness, Pi/gentle-engram)
**Goal:** Recap MVP status; update TRACKING; smoke-check SDK/extensions.
**Done:**
- Verified suite green + SDD verify PASS for `debt-economy-cards-and-boss-interest` (7/7 req, 12/12 scenarios)
- Confirmed 15 card arts + 3 enemy arts + frames + intents + 5 SFX + launcher icons in place
- Confirmed delivery state: debt-economy changes live only in working tree (19 files, +734/−248); combat-progression-i18n stack (pr1–pr7) not yet merged to `develop`
- Updated TRACKING.md to reflect real project state
**Next:** Authorize commit batch for delivery.

### 2026-08-20 (Session)
**Goal:** Economy + combat progression + i18n foundation.
**Done:**
- `feat(combat)` Debt/Gold/Credit economy with garnishment and threshold-break encounter (merged to develop)
- Stack `feat/combat-progression-i18n` pr1–pr7: HP persistence, enemy tiers, I18NBundle infra, HUD/reward/end-screen/domain strings, JSON key wiring (local branches, not yet on develop)
- Established `.pi/` skill + registry + HARNESS structure; configured Engram project detection (`.engram/config.json`, project name `debtsanddecks`)
- Resolved sdd-verify CRITICAL: extracted pure-Kotlin `Localizer` interface in `core/`, GDX `BundleLocalizer` adapter outside; `core/` GDX-free again
- Delivered SDD change `debt-economy-cards-and-boss-interest`: 4 new card-effect primitives, LEVY boss intent, reward pool = 15 economy cards; verify PASS
**Next:** Commit debt-economy work; merge stack to develop.

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
| 2026-08-20 | Core purity (sdd-verify CRITICAL) | **Localizer** — pure-Kotlin interface in `core/`, GDX `BundleLocalizer` adapter outside. `grep -rn "com.badlogic" core/` must be empty. |
| 2026-08-20 | Reward pool | Exactly the **15 economy cards**; `starter`-tagged cards never offered as rewards. |
| 2026-08-20 | Player-facing strings | **Bundle keys** in JSON data; literal text only via `I18NBundle`, EN/ES thematic, neutral Spanish (no voseo). |
| 2026-08-20 | Delivery in fallback harness | `git commit` is **lifecycle-gated** (fail-closed on wrapped commands). Route commits through sanctioned review path; batch-at-end pending. |
| 2026-08-25 | Engram tooling | Server on `127.0.0.1:7437`; project name is **`debtsanddecks`** (lowercase) — `mem_search` with any other casing fails connection. |

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

- [x] Code compiles
- [x] Unit tests pass (core logic)
- [ ] Manual test on device/emulator
- [ ] No new lint warnings
- [x] TRACKING.md updated
- [ ] Commit follows convention (authorized)

---

*Update this file at end of every session. Commit with `chore(docs): update tracking`.*